package com.example.finalproject

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "emergency_alerts_channel"
        private const val CHANNEL_NAME = "Emergency Alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated or refreshed
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 New FCM Token: $token")

        // Save token to Firebase Database
        saveFcmTokenToDatabase(token)
    }

    /**
     * Called when a message is received from FCM
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "📩 Message received from: ${remoteMessage.from}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "📬 Notification Title: ${notification.title}")
            Log.d(TAG, "📬 Notification Body: ${notification.body}")

            sendNotification(
                title = notification.title ?: "Emergency Alert",
                body = notification.body ?: "You have received an emergency notification",
                data = remoteMessage.data
            )
        }

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "📦 Data Payload: ${remoteMessage.data}")

            // Extract data
            val type = remoteMessage.data["type"] ?: "emergency"
            val fromUser = remoteMessage.data["fromUser"] ?: "Unknown"
            val message = remoteMessage.data["message"] ?: "Emergency alert received"
            val location = remoteMessage.data["location"] ?: ""
            val timestamp = remoteMessage.data["timestamp"] ?: ""

            // If notification wasn't sent, create one from data
            if (remoteMessage.notification == null) {
                sendNotification(
                    title = "🚨 Emergency Alert from $fromUser",
                    body = message,
                    data = remoteMessage.data
                )
            }
        }
    }

    /**
     * Save FCM token to Firebase Database under user profile
     */
    private fun saveFcmTokenToDatabase(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val userId = currentUser.uid
            val database = FirebaseDatabase.getInstance()

            database.reference
                .child("users")
                .child(userId)
                .child("fcmToken")
                .setValue(token)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ FCM token saved to database for user: $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to save FCM token: ${e.message}", e)
                }
        } else {
            Log.w(TAG, "⚠️ No user logged in, token not saved")
        }
    }

    /**
     * Create and display notification
     */
    private fun sendNotification(title: String, body: String, data: Map<String, String>) {
        // Intent to open app when notification is tapped
        val intent = Intent(this, dashboard::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // Pass data to activity
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Default notification sound
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.error) // Your app icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setColor(resources.getColor(R.color.reddish, null))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notification ID (use timestamp to show multiple notifications)
        val notificationId = System.currentTimeMillis().toInt()

        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "✅ Notification displayed with ID: $notificationId")
    }

    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency alert notifications from SafeMe"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "✅ Notification channel created: $CHANNEL_ID")
        }
    }
}