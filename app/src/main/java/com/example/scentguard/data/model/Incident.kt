package com.example.scentguard.data.model

import com.google.firebase.Timestamp

data class StaffAction(
    val id: String = "",
    val staffUid: String = "",
    val staffName: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val completionTime: Timestamp? = null,
    val gasSnapshot: Int = 0,
    val actionType: String = "",
    val status: String = "COMPLETED", 
    val message: String = "",         
    val isResponse: Boolean = true    
)

data class Incident(
    val id: String = "",
    val restaurantId: String = "",
    val staffUid: String = "",
    val staffName: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val resolutionTime: Timestamp? = null,
    val durationMillis: Long? = null,
    val initialGas: Int = 0,
    val finalGas: Int? = null,
    val triggerType: String = "GAS", // "GAS"
    val actionPerformed: String = "", // Recommended action title
    val status: String = "IN_PROGRESS", // "IN_PROGRESS", "CLEARED"
    val actions: List<StaffAction> = emptyList(),
    val environmentalClearanceTime: Timestamp? = null
)
