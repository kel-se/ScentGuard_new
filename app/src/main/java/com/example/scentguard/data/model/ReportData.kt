package com.example.scentguard.data.model

data class ReportSummary(
    val avgGasLevel: String = "0 ppm",
    val totalFanRuntime: String = "0m",
    val airQualityScore: Int = 0,
    val alertsCount: Int = 0,
    val period: String = "Daily"
)
