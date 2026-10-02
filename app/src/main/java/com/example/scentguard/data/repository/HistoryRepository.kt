package com.example.scentguard.data.repository

import com.example.scentguard.data.model.HistoryItem
import com.example.scentguard.data.model.HistoryType
import com.example.scentguard.data.model.Incident
import com.example.scentguard.data.model.StaffAction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class HistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class HistoryResponse(
        val items: List<HistoryItem>,
        val lastDocument: com.google.firebase.firestore.DocumentSnapshot?
    )

    suspend fun getHistory(
        restaurantId: String,
        limit: Long = 50,
        lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null,
        category: String? = null,
        startDate: java.util.Date? = null
    ): Result<HistoryResponse> {
        if (restaurantId.isBlank()) return Result.failure(Exception("Invalid Restaurant ID"))
        
        return try {
            var query: Query = firestore.collection("restaurants")
                .document(restaurantId)
                .collection("logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            // Single-field filters are safe with orderBy on the same field
            if (startDate != null) {
                query = query.whereGreaterThanOrEqualTo("timestamp", com.google.firebase.Timestamp(startDate))
            }

            if (lastDocument != null) {
                query = query.startAfter(lastDocument)
            }

            // Fetch more than requested if we are filtering in Kotlin
            val fetchLimit = if (category != null && category != "All") limit * 4 else limit
            val snapshot = query.limit(fetchLimit).get().await()
            
            val allItems = snapshot.toObjects(HistoryItem::class.java)
            
            // Filter in Kotlin to avoid composite index
            val filteredItems = if (category != null && category != "All") {
                allItems.filter { item ->
                    when (category) {
                        "Alerts" -> item.type == HistoryType.ALERT || item.type == HistoryType.WARNING
                        "Fan" -> item.eventType == "FAN_ON" || item.eventType == "FAN_OFF"
                        "Devices" -> item.eventType == "DEVICE_CONNECT" || item.eventType == "DEVICE_DISCONNECT"
                        "Users" -> listOf("USER_LOGIN", "USER_CHANGE", "MEMBER_JOIN", "MEMBER_REMOVE").contains(item.eventType)
                        "System" -> listOf("SYSTEM_START", "SYSTEM_UPDATE", "AIR_SAFE", "SANITATION_START", "SANITATION_END", "SANITATION_CYCLE").contains(item.eventType) ||
                                item.eventType.contains("PUMP", ignoreCase = true) ||
                                item.eventType.contains("SANITATION", ignoreCase = true) ||
                                item.title.contains("Sanitation", ignoreCase = true) ||
                                item.title.contains("Pump", ignoreCase = true)
                        else -> true
                    }
                }.take(limit.toInt())
            } else {
                allItems
            }

            Result.success(HistoryResponse(filteredItems, snapshot.documents.lastOrNull()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds a deterministic log entry to the restaurant's history.
     * Uses set() with a predefined ID to support multi-device deduplication.
     */
    suspend fun addLogEntry(restaurantId: String, log: HistoryItem): Result<Unit> {
        if (restaurantId.isBlank() || log.id.isBlank()) return Result.failure(Exception("Invalid data"))
        
        return try {
            firestore.collection("restaurants")
                .document(restaurantId)
                .collection("logs")
                .document(log.id)
                .set(log)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries the incidents sub-collection for any document that is not yet CLEARED.
     */
    suspend fun getActiveIncident(restaurantId: String): Result<Incident?> {
        if (restaurantId.isBlank()) return Result.failure(Exception("Invalid Restaurant ID"))
        return try {
            val snapshot = firestore.collection("restaurants")
                .document(restaurantId)
                .collection("incidents")
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
            
            val incident = snapshot.documents
                .mapNotNull { it.toObject(Incident::class.java) }
                .firstOrNull { it.status == "IN_PROGRESS" }
            
            Result.success(incident)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically adds a staff response message to an active incident.
     */
    suspend fun addStaffResponse(restaurantId: String, incidentId: String, response: StaffAction): Result<Unit> {
        if (restaurantId.isBlank() || incidentId.isBlank()) return Result.failure(Exception("Invalid data"))
        return try {
            firestore.collection("restaurants")
                .document(restaurantId)
                .collection("incidents")
                .document(incidentId)
                .update("actions", FieldValue.arrayUnion(response))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new incident document ONLY if there isn't an active one already.
     * This prevents duplicate incidents for the same danger cycle.
     */
    suspend fun createIncidentIfMissing(incident: Incident): Result<Unit> {
        if (incident.restaurantId.isBlank() || incident.id.isBlank()) {
            return Result.failure(Exception("Invalid incident data"))
        }
        return try {
            val active = getActiveIncident(incident.restaurantId).getOrNull()
            if (active != null) {
                return Result.success(Unit) // Already exists
            }
            
            firestore.collection("restaurants")
                .document(incident.restaurantId)
                .collection("incidents")
                .document(incident.id)
                .set(incident)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks the most recent active incident as CLEARED when the environment becomes SAFE.
     */
    suspend fun clearActiveIncident(restaurantId: String, clearanceTime: Timestamp): Result<Unit> {
        if (restaurantId.isBlank()) return Result.failure(Exception("Invalid Restaurant ID"))
        return try {
            val activeResult = getActiveIncident(restaurantId)
            val incident = activeResult.getOrNull() ?: return Result.success(Unit)

            firestore.collection("restaurants")
                .document(restaurantId)
                .collection("incidents")
                .document(incident.id)
                .update(
                    "status", "CLEARED",
                    "environmentalClearanceTime", clearanceTime
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a specific log entry.
     */
    suspend fun deleteLogEntry(restaurantId: String, logId: String): Result<Unit> {
        if (restaurantId.isBlank() || logId.isBlank()) return Result.failure(Exception("Invalid data"))
        return try {
            firestore.collection("restaurants")
                .document(restaurantId)
                .collection("logs")
                .document(logId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes all logs for a specific restaurant.
     * Note: Firestore client SDK doesn't support deleting a whole collection in one call.
     * We delete documents in the collection.
     */
    suspend fun deleteAllLogs(restaurantId: String): Result<Unit> {
        if (restaurantId.isBlank()) return Result.failure(Exception("Invalid Restaurant ID"))
        return try {
            val collectionRef = firestore.collection("restaurants")
                .document(restaurantId)
                .collection("logs")
            
            val snapshot = collectionRef.get().await()
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
