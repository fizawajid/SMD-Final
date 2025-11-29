package com.example.finalproject.repository

import android.content.Context
import android.util.Log
import com.example.finalproject.database.AlertDao
import com.example.finalproject.database.AlertEntity
import com.example.finalproject.database.AppDatabase
import com.example.finalproject.utils.NetworkUtils
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class AlertRepository(private val context: Context) {

    private val alertDao: AlertDao = AppDatabase.getDatabase(context).alertDao()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val gson = Gson()

    companion object {
        private const val TAG = "AlertRepository"
    }

    // Insert alert (either to Firebase if online, or local DB if offline)
    suspend fun saveAlert(
        userId: String,
        userEmail: String,
        type: String,
        message: String,
        additionalMessage: String,
        contactsJson: String,
        contactsNotified: Int,
        latitude: Double? = null,
        longitude: Double? = null,
        location: String = "",
        imagePath: String? = null
    ): Result<String> {
        return try {
            val alertId = System.currentTimeMillis().toString()

            if (NetworkUtils.isNetworkAvailable(context)) {
                // Online: Save to Firebase
                val alertData = hashMapOf(
                    "alertId" to alertId,
                    "userId" to userId,
                    "userEmail" to userEmail,
                    "type" to type,
                    "message" to message,
                    "additionalMessage" to additionalMessage,
                    "timestamp" to System.currentTimeMillis(),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "location" to location,
                    "contactsNotified" to contactsNotified,
                    "contacts" to gson.fromJson(contactsJson, List::class.java),
                    "status" to "Unresolved"
                )

                firebaseDb.getReference("emergency_alerts")
                    .push()
                    .setValue(alertData)
                    .await()

                Log.d(TAG, "Alert saved to Firebase: $alertId")
                Result.success("Alert sent successfully")
            } else {
                // Offline: Save to local database
                val alertEntity = AlertEntity(
                    alertId = alertId,
                    userId = userId,
                    userEmail = userEmail,
                    type = type,
                    message = message,
                    additionalMessage = additionalMessage,
                    timestamp = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    location = location,
                    contactsNotified = contactsNotified,
                    contactsJson = contactsJson,
                    status = "pending",
                    imagePath = imagePath
                )

                val id = alertDao.insertAlert(alertEntity)
                Log.d(TAG, "Alert saved locally: $id (offline mode)")
                Result.success("Alert saved locally (offline). Will sync when online.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving alert", e)
            Result.failure(e)
        }
    }

    // Get all alerts for a user
    fun getAllAlertsForUser(userId: String): Flow<List<AlertEntity>> {
        return alertDao.getAlertsByUserIdFlow(userId)
    }

    // Get pending alerts
    suspend fun getPendingAlerts(): List<AlertEntity> {
        return alertDao.getPendingAlerts()
    }

    // Get pending alerts count
    suspend fun getPendingAlertsCount(): Int {
        return alertDao.getPendingAlertsCount()
    }

    // Update alert status
    suspend fun updateAlertStatus(id: Int, status: String) {
        alertDao.updateAlertStatus(id, status)
    }

    // Delete old synced alerts (cleanup)
    suspend fun cleanupOldAlerts(daysOld: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        alertDao.deleteOldSyncedAlerts(cutoffTime)
    }

    // Sync single pending alert to Firebase
    suspend fun syncAlertToFirebase(alert: AlertEntity): Result<Unit> {
        return try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return Result.failure(Exception("No internet connection"))
            }

            val alertData = hashMapOf(
                "alertId" to alert.alertId,
                "userId" to alert.userId,
                "userEmail" to alert.userEmail,
                "type" to alert.type,
                "message" to alert.message,
                "additionalMessage" to alert.additionalMessage,
                "timestamp" to alert.timestamp,
                "latitude" to alert.latitude,
                "longitude" to alert.longitude,
                "location" to alert.location,
                "contactsNotified" to alert.contactsNotified,
                "contacts" to gson.fromJson(alert.contactsJson, List::class.java),
                "status" to "Unresolved"
            )

            firebaseDb.getReference("emergency_alerts")
                .push()
                .setValue(alertData)
                .await()

            // Update status to synced
            alertDao.updateAlertStatus(alert.id, "synced")
            Log.d(TAG, "Alert synced successfully: ${alert.alertId}")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing alert: ${alert.alertId}", e)

            // Update sync attempts
            alertDao.incrementSyncAttempts(alert.id, System.currentTimeMillis())

            Result.failure(e)
        }
    }

    // Get all alerts (for debugging/admin)
    suspend fun getAllAlerts(): List<AlertEntity> {
        return alertDao.getRecentAlerts(100) // Get recent 100 alerts
    }

    // Delete single alert
    suspend fun deleteAlert(id: Int): Result<Unit> {
        return try {
            alertDao.deleteAlertById(id)
            Log.d(TAG, "Alert deleted: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting alert: $id", e)
            Result.failure(e)
        }
    }
}