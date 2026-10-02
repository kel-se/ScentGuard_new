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
                } else {
                    _reportState.value = Resource.Idle()
                    _chartState.value = Resource.Idle()
                    _computedSummary.value = ReportSummary()
                }
            }
        }
    }

    fun fetchChartData(
        isWeekly: Boolean = false,
        restaurantId: String? = null,
        warnThreshold: Int = 1000,
        dangerThreshold: Int = 1500
    ) {
        val rid = restaurantId ?: authRepository.userSession.value?.restaurantId ?: return
        viewModelScope.launch {
            _chartState.value = Resource.Loading()
            val result = chartRepository.getGasLevelHistory(rid, isWeekly)
            result.onSuccess { data ->
                _chartState.value = Resource.Success(data)
                computeSummaryFromData(data, rid, isWeekly, warnThreshold, dangerThreshold)
            }.onFailure {
                _chartState.value = Resource.Error(it.message ?: "Failed to load chart")
            }
        }
    }

    private fun computeSummaryFromData(
        data: ChartData,
        rid: String,
        isWeekly: Boolean = false,
        warnThreshold: Int = 1000,
        dangerThreshold: Int = 1500
    ) {
        viewModelScope.launch {
            val points = data.points
            if (points.isEmpty()) {
                _computedSummary.value = ReportSummary(
                    avgGasLevel = "0 ppm",
                    totalFanRuntime = "0m",
                    airQualityScore = 100,
                    alertsCount = 0,
                    period = if (isWeekly) "Weekly" else "Daily"
                )
                return@launch
            }

            // Fetch actual active thresholds from Firestore if defaults were passed
            var warnT = warnThreshold
            var dangerT = dangerThreshold
            try {
                val db = FirebaseFirestore.getInstance()
                val restDoc = db.collection("restaurants").document(rid).get().await()
                if (restDoc.exists()) {
                    warnT = restDoc.getLong("thresholdWarn")?.toInt() ?: warnThreshold
                    dangerT = restDoc.getLong("thresholdDanger")?.toInt() ?: dangerThreshold
                }
            } catch (_: Exception) {
                // Fallback to supplied thresholds
            }

            // 1. Average Gas
            val avgGas = points.map { it.y }.average().toInt()

            // 2. Total Alerts matching sensitivity setting
            val dangerSnapshots = points.count { it.y >= dangerT.toFloat() }
            val warnSnapshots = points.count { it.y >= warnT.toFloat() && it.y < dangerT.toFloat() }
            val totalSnapshots = points.size

            // 3. Air Quality Score based on active sensitivity limits
            val performanceScore = if (totalSnapshots > 0) {
                (100 - ((dangerSnapshots * 1.0f + warnSnapshots * 0.3f) / totalSnapshots * 100)).toInt().coerceIn(0, 100)
            } else 100

            // 4. Fan Runtime
            var totalMinutes = 0
            val db = FirebaseFirestore.getInstance()
            try {
                val limit = if (isWeekly) 1000 else 96
                val snapshot = db.collection("restaurants").document(rid)
                    .collection("sensor_history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()
                
                for (doc in snapshot.documents) {
                    if (doc.getString("fanStatus") == "ON") {
                        totalMinutes += 15
                    }
                }
            } catch (_: Exception) {
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
            val result = reportRepository.getWeeklyReport(rid)
            result.onSuccess {
                _reportState.value = Resource.Success(it)
            }.onFailure {
                _reportState.value = Resource.Error(it.message ?: "Failed to load report")
            }
        }
    }
}
