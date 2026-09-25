package com.example.scentguard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scentguard.data.model.ChartData
import com.example.scentguard.data.model.ReportSummary
import com.example.scentguard.data.repository.AuthRepository
import com.example.scentguard.data.repository.ChartRepository
import com.example.scentguard.data.repository.ReportRepository
import com.example.scentguard.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val chartRepository: ChartRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _reportState = MutableStateFlow<Resource<ReportSummary>>(Resource.Idle())
    val reportState: StateFlow<Resource<ReportSummary>> = _reportState

    private val _chartState = MutableStateFlow<Resource<ChartData>>(Resource.Idle())
    val chartState: StateFlow<Resource<ChartData>> = _chartState

    private val _tempChartState = MutableStateFlow<Resource<ChartData>>(Resource.Idle())
    val tempChartState: StateFlow<Resource<ChartData>> = _tempChartState

    private val _computedSummary = MutableStateFlow(ReportSummary())
    val computedSummary: StateFlow<ReportSummary> = _computedSummary.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            authRepository.userSession.collectLatest { session ->
                if (session != null) {
                    val rid = session.restaurantId
                    fetchDailyReport(rid)
                    fetchChartData(isWeekly = false, restaurantId = rid)
                    fetchTempChartData(isWeekly = false, restaurantId = rid)
                } else {
                    _reportState.value = Resource.Idle()
                    _chartState.value = Resource.Idle()
                    _tempChartState.value = Resource.Idle()
                    _computedSummary.value = ReportSummary()
                }
            }
        }
    }

    fun fetchTempChartData(isWeekly: Boolean = false, restaurantId: String? = null) {
        val rid = restaurantId ?: authRepository.userSession.value?.restaurantId ?: return
        viewModelScope.launch {
            _tempChartState.value = Resource.Loading()
            val result = chartRepository.getTemperatureHistory(rid, isWeekly)
            result.onSuccess { data ->
                _tempChartState.value = Resource.Success(data)
                updateSummaryWithTemp(data)
            }.onFailure {
                _tempChartState.value = Resource.Error(it.message ?: "Failed to load temp chart")
            }
        }
    }

    private fun updateSummaryWithTemp(data: ChartData) {
        val avgTemp = data.points.map { it.y }.average()
        if (!avgTemp.isNaN()) {
            val currentSummary = _computedSummary.value
            _computedSummary.value = currentSummary.copy(
                avgTemp = String.format(java.util.Locale.getDefault(), "%.1f°C", avgTemp)
            )
        }
    }

    fun fetchChartData(isWeekly: Boolean = false, restaurantId: String? = null) {
        val rid = restaurantId ?: authRepository.userSession.value?.restaurantId ?: return
        viewModelScope.launch {
            _chartState.value = Resource.Loading()
            val result = chartRepository.getGasLevelHistory(rid, isWeekly)
            result.onSuccess { data ->
                _chartState.value = Resource.Success(data)
                computeSummaryFromData(data, rid, isWeekly)
            }.onFailure {
                _chartState.value = Resource.Error(it.message ?: "Failed to load chart")
            }
        }
    }

    private fun computeSummaryFromData(data: ChartData, rid: String, isWeekly: Boolean = false) {
        viewModelScope.launch {
            val points = data.points
            if (points.isEmpty()) {
                _computedSummary.value = ReportSummary(
                    avgGasLevel = "0 ppm",
                    totalFanRuntime = "0m",
                    airQualityScore = 0,
                    alertsCount = 0,
                    period = if (isWeekly) "Weekly" else "Daily"
                )
                return@launch
            }

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val dangerThreshold = try {
                val restDoc = db.collection("restaurants").document(rid).get().await()
                (restDoc.getLong("thresholdDanger") ?: 1500L).toFloat()
            } catch (e: Exception) {
                1500f
            }

            // 1. Average Gas
            val avgGas = points.map { it.y }.average().toInt()

            // 2. Total Alerts (snapshots reaching or exceeding configured Danger threshold)
            val dangerSnapshots = points.count { it.y >= dangerThreshold }
            val totalSnapshots = points.size

            // 3. Performance Index
            val performanceScore = if (totalSnapshots > 0) {
                (100 - (dangerSnapshots.toFloat() / totalSnapshots * 100)).toInt()
            } else 0

            // 4. Fan Runtime
            var totalMinutes = 0
            try {
                val limit = if (isWeekly) 1000 else 96
                val snapshot = db.collection("restaurants").document(rid)
                    .collection("sensor_history")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()
                
                for (doc in snapshot.documents) {
                    if (doc.getString("fanStatus") == "ON") {
                        totalMinutes += 15
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }

            val runtimeText = if (totalMinutes >= 60) {
                "${totalMinutes / 60}h ${totalMinutes % 60}m"
            } else {
                "${totalMinutes}m"
            }

            _computedSummary.value = ReportSummary(
                avgGasLevel = "$avgGas ppm",
                totalFanRuntime = runtimeText,
                airQualityScore = performanceScore,
                alertsCount = dangerSnapshots,
                period = if (isWeekly) "Weekly" else "Daily"
            )
        }
    }

    fun fetchDailyReport(restaurantId: String? = null) {
        val rid = restaurantId ?: authRepository.userSession.value?.restaurantId ?: return
        viewModelScope.launch {
            _reportState.value = Resource.Loading()
            fetchChartData(isWeekly = false, restaurantId = rid)
            fetchTempChartData(isWeekly = false, restaurantId = rid)
            val result = reportRepository.getDailyReport(rid)
            result.onSuccess {
                _reportState.value = Resource.Success(it)
            }.onFailure {
                _reportState.value = Resource.Error(it.message ?: "Failed to load report")
            }
        }
    }

    fun fetchWeeklyReport(restaurantId: String? = null) {
        val rid = restaurantId ?: authRepository.userSession.value?.restaurantId ?: return
        viewModelScope.launch {
            _reportState.value = Resource.Loading()
            fetchChartData(isWeekly = true, restaurantId = rid)
            fetchTempChartData(isWeekly = true, restaurantId = rid)
            val result = reportRepository.getWeeklyReport(rid)
            result.onSuccess {
                _reportState.value = Resource.Success(it)
            }.onFailure {
                _reportState.value = Resource.Error(it.message ?: "Failed to load report")
            }
        }
    }
}
