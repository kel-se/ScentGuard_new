package com.example.scentguard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scentguard.data.model.Restaurant
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.data.repository.UserRepository
import com.example.scentguard.utils.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class StaffFilterOption(val label: String) {
    LAST_JOINED("Last Joined"),
    RECENTLY_JOINED("Recently Joined")
}

class StaffViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _staffList = MutableStateFlow<Resource<List<UserProfile>>>(Resource.Idle())
    val staffList: StateFlow<Resource<List<UserProfile>>> = _staffList

    private val _selectedFilter = MutableStateFlow(StaffFilterOption.LAST_JOINED)
    val selectedFilter: StateFlow<StaffFilterOption> = _selectedFilter.asStateFlow()

    val filteredStaffList: StateFlow<Resource<List<UserProfile>>> = combine(_staffList, _selectedFilter) { resource, filter ->
        when (resource) {
            is Resource.Success -> {
                val list = resource.data ?: emptyList()
                val filtered = applyFilterAndSort(list, filter)
                Resource.Success(filtered)
            }
            is Resource.Loading -> Resource.Loading()
            is Resource.Error -> Resource.Error(resource.message ?: "Failed to fetch staff")
            is Resource.Idle -> Resource.Idle()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Idle())

    private val _restaurantInfo = MutableStateFlow<Resource<Restaurant>>(Resource.Idle())
    val restaurantInfo: StateFlow<Resource<Restaurant>> = _restaurantInfo

    private val _isRefreshingCode = MutableStateFlow(false)
    val isRefreshingCode: StateFlow<Boolean> = _isRefreshingCode

    private val _removalState = MutableStateFlow<Resource<Unit>>(Resource.Idle())
    val removalState: StateFlow<Resource<Unit>> = _removalState

    private val _timeRemaining = MutableStateFlow("")
    val timeRemaining: StateFlow<String> = _timeRemaining

    init {
        startTimer()
    }

    fun setFilter(option: StaffFilterOption) {
        _selectedFilter.value = option
    }

    private fun applyFilterAndSort(list: List<UserProfile>, filter: StaffFilterOption): List<UserProfile> {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - thirtyDaysInMillis

        return when (filter) {
            StaffFilterOption.LAST_JOINED -> {
                list.sortedWith(
                    Comparator { a, b ->
                        val tA = a.createdAt?.toDate()?.time
                        val tB = b.createdAt?.toDate()?.time
                        when {
                            tA == null && tB == null -> 0
                            tA == null -> 1  // Null createdAt placed at end
                            tB == null -> -1 // Null createdAt placed at end
                            else -> tB.compareTo(tA) // Newest timestamp first
                        }
                    }
                )
            }
            StaffFilterOption.RECENTLY_JOINED -> {
                list.filter { member ->
                    val time = member.createdAt?.toDate()?.time
                    time != null && time >= thirtyDaysAgo
                }.sortedWith(
                    Comparator { a, b ->
                        val tA = a.createdAt!!.toDate().time
                        val tB = b.createdAt!!.toDate().time
                        tB.compareTo(tA) // Newest timestamp first
                    }
                )
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (isActive) {
                updateCountdown()
                delay(1000 * 60) // Update every minute
            }
        }
    }

    private fun updateCountdown() {
        val restaurant = (_restaurantInfo.value as? Resource.Success)?.data
        val expiry = restaurant?.inviteCodeExpiresAt?.toDate()
        
        if (expiry == null) {
            _timeRemaining.value = ""
            return
        }

        val diff = expiry.time - System.currentTimeMillis()
        if (diff <= 0) {
            _timeRemaining.value = "Expired"
        } else {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            _timeRemaining.value = String.format("%02dh %02dm", hours, minutes)
        }
    }

    fun fetchStaff(restaurantId: String) {
        viewModelScope.launch {
            _staffList.value = Resource.Loading()
            val result = userRepository.getStaffByRestaurant(restaurantId)
            result.onSuccess {
                _staffList.value = Resource.Success(it)
            }.onFailure {
                _staffList.value = Resource.Error(it.message ?: "Failed to fetch staff")
            }
        }
    }

    fun fetchRestaurantInfo(restaurantId: String, userRole: String? = null) {
        viewModelScope.launch {
            _restaurantInfo.value = Resource.Loading()
            val result = userRepository.getRestaurantById(restaurantId)
            result.onSuccess { restaurant ->
                if (restaurant != null) {
                    val finalRestaurant = if (userRole?.uppercase() != "MANAGER") {
                        restaurant.copy(inviteCode = "******")
                    } else {
                        restaurant
                    }
                    _restaurantInfo.value = Resource.Success(finalRestaurant)
                    updateCountdown()
                } else {
                    _restaurantInfo.value = Resource.Error("Restaurant not found")
                }
            }.onFailure {
                _restaurantInfo.value = Resource.Error(it.message ?: "Error fetching restaurant")
            }
        }
    }

    fun refreshInviteCode(restaurantId: String, durationHours: Long = 24, userRole: String? = null) {
        viewModelScope.launch {
            _isRefreshingCode.value = true
            val result = userRepository.refreshInviteCode(restaurantId, durationHours)
            if (result.isSuccess) {
                fetchRestaurantInfo(restaurantId, userRole)
            }
            _isRefreshingCode.value = false
        }
    }

    fun removeStaff(uid: String, restaurantId: String) {
        viewModelScope.launch {
            _removalState.value = Resource.Loading()
            val result = userRepository.removeStaffFromRestaurant(uid)
            if (result.isSuccess) {
                _removalState.value = Resource.Success(Unit)
                fetchStaff(restaurantId)
            } else {
                _removalState.value = Resource.Error(result.exceptionOrNull()?.message ?: "Failed to remove staff")
            }
        }
    }
    
    fun resetRemovalState() {
        _removalState.value = Resource.Idle()
    }
}
