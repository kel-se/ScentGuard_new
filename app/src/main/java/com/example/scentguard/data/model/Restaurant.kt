package com.example.scentguard.data.model

import com.google.firebase.Timestamp

data class Restaurant(
    val id: String = "",
    val name: String = "",
    val managerUid: String = "",
    val inviteCode: String = "",
    val inviteCodeExpiresAt: Timestamp? = null,
    val currentGasPpm: Int = 0,
    val airStatus: String = "SAFE", // "SAFE", "WARN", "DANGER"
    val fanStatus: String = "OFF", // "ON", "OFF"
    val pumpStatus: String = "OFF", // "ON", "OFF"
    val pumpMode: String = "AUTO", // "ON", "OFF", "AUTO"
    val manualSanitationTrigger: Timestamp? = null,
    val lastSanitationTime: Timestamp? = null,
    val sanitationDurationSeconds: Int = 50, // Match firmware default
    val thresholdWarn: Int = 1000,
    val thresholdDanger: Int = 1500,
    val lastSeen: Timestamp? = null,
    val fanMode: String = "AUTO", // "ON", "OFF", "AUTO"
    val createdAt: Timestamp? = null
) {
    companion object {
        const val DEFAULT_THRESHOLD_WARN = 1000
        const val DEFAULT_THRESHOLD_DANGER = 1500
    }
}
