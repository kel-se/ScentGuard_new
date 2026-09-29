package com.example.scentguard.data.repository

import com.example.scentguard.data.model.ChartData
import com.example.scentguard.data.model.ChartPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ChartRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateIdFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    suspend fun getGasLevelHistory(restaurantId: String, isWeekly: Boolean = false): Result<ChartData> {
        return getSensorHistory(restaurantId, "currentGasPpm", 0f, 2000f, "ppm", isWeekly)
    }

    private suspend fun getSensorHistory(
        restaurantId: String,
        fieldName: String,
        minVal: Float,
        maxVal: Float,
        unit: String,
        isWeekly: Boolean
    ): Result<ChartData> {
        if (restaurantId.isBlank()) return Result.failure(Exception("Invalid ID"))
        
        return try {
            val limit = if (isWeekly) 1000 else 96
            val snapshot = firestore.collection("restaurants")
                .document(restaurantId)
                .collection("sensor_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                
            val documents = snapshot.documents.reversed()
            
            val points = if (isWeekly) {
                // Weekly View: Aggregate by day
                documents.groupBy { doc ->
                    val date = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    dateIdFormatter.format(date)
                }.values.mapIndexed { index, dailyDocs ->
                    val avgValue = dailyDocs.map { (it.get(fieldName) as? Number)?.toFloat() ?: 0f }.average().toFloat()
                    val firstDocDate = dailyDocs.first().getTimestamp("timestamp")?.toDate() ?: Date()
                    val label = dayFormatter.format(firstDocDate)
                    ChartPoint(index.toFloat(), avgValue, label)
                }
            } else {
                // Daily View: Individual readings
                documents.mapIndexed { index, doc ->
                    val value = (doc.get(fieldName) as? Number)?.toFloat() ?: 0f
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val label = timeFormatter.format(timestamp)
                    ChartPoint(index.toFloat(), value, label)
                }
            }
            
            Result.success(
                ChartData(
                    points = points,
                    minVal = minVal,
                    maxVal = maxVal,
                    unit = unit
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
