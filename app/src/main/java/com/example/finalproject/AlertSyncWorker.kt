package com.example.finalproject

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints

import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.finalproject.database.AlertEntity
import com.example.finalproject.repository.AlertRepository
import com.example.finalproject.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import com.google.gson.reflect.TypeToken


class AlertSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val alertRepository = AlertRepository(context)
    private val gson = Gson()

    // EmailJS Configuration
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"

    companion object {
        private const val TAG = "AlertSyncWorker"
        const val WORK_NAME = "alert_sync_work"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<AlertSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicSyncRequest = PeriodicWorkRequestBuilder<AlertSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicSyncRequest
                )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting alert sync work...")

            // Check network availability
            if (!NetworkUtils.isNetworkAvailable(applicationContext)) {
                Log.w(TAG, "❌ No network available, retrying later")
                return@withContext Result.retry()
            }

            // Get all pending alerts
            val pendingAlerts = alertRepository.getPendingAlerts()

            if (pendingAlerts.isEmpty()) {
                Log.d(TAG, "✅ No pending alerts to sync")
                return@withContext Result.success()
            }

            Log.d(TAG, "📋 Found ${pendingAlerts.size} pending alerts to sync")

            var successCount = 0
            var failureCount = 0

            // Sync each pending alert
            for (alert in pendingAlerts) {
                try {
                    // Sync to Firebase
                    val syncResult = alertRepository.syncAlertToFirebase(alert)

                    if (syncResult.isSuccess) {
                        // After successful Firebase sync, send emails
                        val emailSent = sendEmailsForAlert(alert)

                        if (emailSent) {
                            successCount++
                            Log.d(
                                TAG,
                                "✅ Alert ${alert.alertId} synced and emails sent successfully"
                            )
                        } else {
                            // Mark as synced to Firebase but log email failure
                            Log.w(
                                TAG,
                                "⚠️ Alert ${alert.alertId} synced to Firebase but email sending failed"
                            )
                            successCount++
                        }
                    } else {
                        failureCount++
                        Log.e(TAG, "❌ Failed to sync alert ${alert.alertId}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "❌ Error syncing alert ${alert.alertId}: ${e.message}", e)
                }
            }

            Log.d(TAG, "📊 Sync complete: $successCount succeeded, $failureCount failed")

            // Show notification about sync result
            showSyncNotification(successCount, failureCount)

            // Return success if at least one alert was synced
            return@withContext if (successCount > 0) {
                Result.success()
            } else if (failureCount > 0) {
                Result.retry()
            } else {
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Alert sync failed: ${e.message}", e)
            return@withContext Result.retry()
        }
    }

    private suspend fun sendEmailsForAlert(alert: AlertEntity): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Parse contacts from JSON
                val contactsJson = alert.contactsJson
                val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type

                val contactsList: List<Map<String, Any?>> =
                    gson.fromJson(contactsJson, listType)


                if (contactsList.isEmpty()) {
                    Log.w(TAG, "No contacts found for alert ${alert.alertId}")
                    return@withContext true // Consider it success if no contacts
                }

                val emailContacts = contactsList.filter { contact ->
                    val email = contact["email"] as? String ?: return@filter false
                    email.isNotBlank()
                }



                if (emailContacts.isEmpty()) {
                    Log.w(TAG, "No contacts with email for alert ${alert.alertId}")
                    return@withContext true
                }

                Log.d(
                    TAG,
                    "📧 Sending emails to ${emailContacts.size} contacts for alert ${alert.alertId}"
                )

                var emailsSent = 0
                var emailsFailed = 0

                // Send email to each contact
                for (contact in emailContacts) {
                    val email = contact["email"] as? String ?: continue
                    val name = contact["name"] as? String ?: "Emergency Contact"

                    val emailSent = sendEmail(alert, email, name)
                    if (emailSent) {
                        emailsSent++
                    } else {
                        emailsFailed++
                    }
                }

                Log.d(TAG, "📊 Email results: $emailsSent sent, $emailsFailed failed")

                return@withContext emailsSent > 0
            } catch (e: Exception) {
                Log.e(TAG, "Error sending emails for alert ${alert.alertId}: ${e.message}", e)
                return@withContext false
            }
        }
    }

    private suspend fun sendEmail(
        alert: AlertEntity,
        toEmail: String,
        contactName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.emailjs.com/api/v1.0/email/send"

            val message = buildAlertMessage(alert)

            val jsonBody = JSONObject().apply {
                put("service_id", EMAILJS_SERVICE_ID)
                put("template_id", EMAILJS_TEMPLATE_ID)
                put("user_id", EMAILJS_PUBLIC_KEY)
                put("template_params", JSONObject().apply {
                    put("to_email", toEmail)
                    put("contact_name", contactName)
                    put("alert_message", message)

                    // Add location data if available
                    if (alert.latitude != null && alert.longitude != null) {
                        put("location_address", alert.location)
                        put("location_coordinates", "${alert.latitude}, ${alert.longitude}")
                        put(
                            "google_maps_link",
                            "https://www.google.com/maps?q=${alert.latitude},${alert.longitude}"
                        )
                    }
                })
            }

            var emailSuccess = false

            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "✅ Email sent successfully to $toEmail (Response: $response)")
                    emailSuccess = true
                },
                { error ->
                    if (error.networkResponse?.statusCode == 200) {
                        Log.d(TAG, "✅ Email sent successfully to $toEmail (HTTP 200)")
                        emailSuccess = true
                    } else {
                        val errorMsg = when {
                            error.networkResponse != null -> {
                                val statusCode = error.networkResponse.statusCode
                                val errorBody = String(error.networkResponse.data ?: byteArrayOf())
                                "HTTP $statusCode: $errorBody"
                            }

                            error.cause is ConnectException -> {
                                "Connection failed"
                            }

                            else -> error.message ?: "Unknown error"
                        }
                        Log.e(TAG, "❌ Failed to send email to $toEmail: $errorMsg")
                        emailSuccess = false
                    }
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return hashMapOf(
                        "Content-Type" to "application/json",
                        "origin" to "http://localhost",
                        "Authorization" to "Bearer $EMAILJS_PUBLIC_KEY"
                    )
                }

                override fun getBody(): ByteArray {
                    return jsonBody.toString().toByteArray(Charsets.UTF_8)
                }

                override fun getBodyContentType(): String {
                    return "application/json; charset=utf-8"
                }
            }

            request.setRetryPolicy(
                DefaultRetryPolicy(
                    15000,
                    1,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )
            )

            val requestQueue = Volley.newRequestQueue(applicationContext)
            requestQueue.add(request)

            // Wait for the request to complete (with timeout)
            val startTime = System.currentTimeMillis()
            while (!emailSuccess && (System.currentTimeMillis() - startTime) < 20000) {
                Thread.sleep(100)
            }

            return@withContext emailSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email to $toEmail: ${e.message}", e)
            return@withContext false
        }
    }

    private fun buildAlertMessage(alert: AlertEntity): String {
        val sb = StringBuilder()

        when {
            alert.type.contains("Personal", ignoreCase = true) -> {
                sb.append("🚨 PERSONAL SAFETY ALERT 🚨\n\n")
            }
            alert.type.contains("Travel", ignoreCase = true) -> {
                sb.append("🚨 TRAVEL EMERGENCY ALERT 🚨\n\n")
            }
            alert.type.contains("Medical", ignoreCase = true) -> {
                sb.append("🚨 MEDICAL EMERGENCY ALERT 🚨\n\n")
            }
            else -> {
                sb.append("🚨 EMERGENCY ALERT 🚨\n\n")
            }
        }

        sb.append("From: ${alert.userEmail}\n")
        sb.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))}\n\n")

        // Location information
        sb.append("📍 LOCATION:\n")
        if (alert.latitude != null && alert.longitude != null) {
            sb.append("${alert.location}\n")
            sb.append("Coordinates: ${alert.latitude}, ${alert.longitude}\n")
            sb.append("Google Maps: https://www.google.com/maps?q=${alert.latitude},${alert.longitude}\n\n")
        } else {
            sb.append("Location: ${alert.location}\n\n")
        }

        // Additional message
        if (alert.additionalMessage.isNotEmpty()) {
            sb.append("Message: ${alert.additionalMessage}\n\n")
        }

        sb.append("⚠️ IMMEDIATE RESPONSE REQUIRED ⚠️\n")
        sb.append("This is an automated emergency alert that was sent offline and synced when connectivity was restored.\n")
        sb.append("Please respond immediately.")

        return sb.toString()
    }

    private fun showSyncNotification(successCount: Int, failureCount: Int) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel for sync notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "alert_sync_channel",
                    "Alert Sync",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for alert synchronization"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val title = if (failureCount == 0) {
                "✅ Alerts Synced Successfully"
            } else {
                "⚠️ Alerts Sync Completed"
            }

            val message = "Synced: $successCount${if (failureCount > 0) " | Failed: $failureCount" else ""}"

            val notification = NotificationCompat.Builder(applicationContext, "alert_sync_channel")
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(3001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing sync notification: ${e.message}", e)
        }
    }
}