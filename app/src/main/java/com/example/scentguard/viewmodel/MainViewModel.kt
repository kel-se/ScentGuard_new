package com.example.scentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scentguard.ScentGuardApplication
import com.example.scentguard.data.model.HistoryItem
import com.example.scentguard.data.model.Restaurant
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.data.model.UserSession
import com.example.scentguard.data.repository.AuthRepository
import com.example.scentguard.data.repository.HistoryRepository
import com.example.scentguard.data.repository.UserRepository
import com.example.scentguard.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val historyRepository: HistoryRepository
) : AndroidViewModel(application) {

    private val preferencesManager = (application as ScentGuardApplication).preferencesManager

    private val _userProfile = MutableStateFlow<Resource<UserProfile>>(Resource.Idle())
    val userProfile: StateFlow<Resource<UserProfile>> = _userProfile

    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted

    private val _isUserAuthenticated = MutableStateFlow(authRepository.currentUser != null)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated

    private val _liveRestaurantData = MutableStateFlow<Restaurant?>(null)
    val liveRestaurantData: StateFlow<Restaurant?> = _liveRestaurantData.asStateFlow()

    private val _recentActivity = MutableStateFlow<List<HistoryItem>>(emptyList())
    val recentActivity: StateFlow<List<HistoryItem>> = _recentActivity.asStateFlow()

    private val _signalStatus = MutableStateFlow("Offline")
    val signalStatus: StateFlow<String> = _signalStatus.asStateFlow()

    val userSession: StateFlow<UserSession?> = authRepository.userSession

    val currentUserEmail: String?
        get() = authRepository.currentUser?.email

    init {
        observeAuthState()
        loadLocalOnboardingStatus()
        observeLiveRestaurantData()
        observeRecentActivity()
        startSignalStatusTicker()
        
        // Attempt to restore session if firebase user exists but session is null
        if (authRepository.currentUser != null && authRepository.userSession.value == null) {
            viewModelScope.launch {
                authRepository.restoreSession()
            }
        }
    }

    private fun observeLiveRestaurantData() {
        viewModelScope.launch {
            userSession.collectLatest { session ->
                if (session != null) {
                    userRepository.getRestaurantFlow(session.restaurantId).collect { restaurant ->
                        _liveRestaurantData.value = restaurant
                        updateSignalStatus(restaurant)
                    }
                } else {
                    _liveRestaurantData.value = null
                    _signalStatus.value = "Offline"
                }
            }
        }
    }

    /**
     * Ticker that re-evaluates signal status every 2 seconds.
     * This ensures the UI reflects "Offline" even if no Firestore updates occur.
     */
    private fun startSignalStatusTicker() {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                updateSignalStatus(_liveRestaurantData.value)
            }
        }
    }

    private fun observeRecentActivity() {
        viewModelScope.launch {
            userSession.collectLatest { session ->
                if (session != null) {
                    // Fetch real logs from HistoryRepository
                    historyRepository.getHistory(session.restaurantId).onSuccess { response ->
                        _recentActivity.value = response.items.take(3)
                    }.onFailure {
                        _recentActivity.value = emptyList()
                    }
                } else {
                    _recentActivity.value = emptyList()
                }
            }
        }
    }

    private fun updateSignalStatus(restaurant: Restaurant?) {
        val lastSeen = restaurant?.lastSeen?.toDate()
        val currentTime = System.currentTimeMillis()
        
        if (restaurant == null) {
            _signalStatus.value = "Offline"
            return
        }

        if (lastSeen == null) {
            _signalStatus.value = "Offline"
            return
        }

        val diffMs = currentTime - lastSeen.time
        _signalStatus.value = when {
            diffMs < 15000 -> "Active"      // < 15s for Active
            diffMs < 45000 -> "Weak"        // < 45s for Weak
            else -> "Offline"               // > 45s for Offline
        }
    }

    fun updateFanMode(mode: String) {
        val session = userSession.value ?: return
        viewModelScope.launch {
            userRepository.updateFanMode(session.restaurantId, mode)
        }
    }

    fun updateFcmToken(token: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            userRepository.updateFcmToken(uid, token)
        }
    }

    fun selectMascotAvatar(avatarId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = userRepository.updateAvatar(uid, "mascot", avatarId)
            if (result.isSuccess) {
                fetchUserProfile()
            }
        }
    }

    private fun loadLocalOnboardingStatus() {
        viewModelScope.launch {
            _onboardingCompleted.value = preferencesManager.isOnboardingCompleted.first()
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authStateFlow.collectLatest { firebaseUser ->
                _isUserAuthenticated.value = firebaseUser != null
                if (firebaseUser != null) {
                    fetchUserProfile()
                } else {
                    // Not authenticated - set error to trigger navigation in Splash
                    _userProfile.value = Resource.Error("Not authenticated")
                    _onboardingCompleted.value = false
                }
            }
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            val result = withContext(Dispatchers.IO) {
                userRepository.getUserProfile()
            }
            
            result.onSuccess { user ->
                if (user != null) {
                    _userProfile.value = Resource.Success(user)
                    _onboardingCompleted.value = user.onboardingCompleted
                    preferencesManager.setOnboardingCompleted(user.onboardingCompleted)
                } else {
                    _userProfile.value = Resource.Error("MISSING_PROFILE")
                }
            }.onFailure {
                _userProfile.value = Resource.Error(it.message ?: "Failed to fetch profile")
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                userRepository.updateOnboardingStatus(true)
            }
            if (result.isSuccess) {
                preferencesManager.setOnboardingCompleted(true)
                _onboardingCompleted.value = true
                fetchUserProfile()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _userProfile.value = Resource.Idle()
            _onboardingCompleted.value = false
            preferencesManager.setOnboardingCompleted(false)
            _isUserAuthenticated.value = false
        }
    }
}
