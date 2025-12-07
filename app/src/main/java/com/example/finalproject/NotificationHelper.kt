package com.example.finalproject

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.finalproject.EmergencyContact
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    /**
     * IMPORTANT: For production, use Firebase Cloud Functions or a backend server
     * This implementation uses FCM directly from the client which is acceptable
     * for testing but not recommended for production due to security concerns.
     *
     * For now, we'll use Firebase's built-in methods which don't require server key
     */

    /**
     * Send emergency alert notification to a specific user
     * Using Firebase Admin SDK approach (client-side implementation)
     */
    fun sendEmergencyAlert(
        context: Context,
        recipientUserId: String,
        senderName: String,
        senderEmail: String,
        alertType: String,
        message: String,
        location: String,
        latitude: Double?,
        longitude: Double?
    ) {
        Log.d(TAG, "📤 Preparing to send alert to user: $recipientUserId")

        // First, get the recipient's FCM token from database
        getFcmToken(recipientUserId) { token ->
            if (token != null) {
                Log.d(TAG, "✅ Got FCM token: ${token.take(20)}...")

                // Create notification data in Firebase Database
                // This will trigger Cloud Functions (if you set them up) or
                // the recipient's app will listen to this path
                createNotificationInDatabase(
                    recipientUserId = recipientUserId,
                    senderName = senderName,
                    senderEmail = senderEmail,
                    alertType = alertType,
                    message = message,
                    location = location,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                Log.w(TAG, "⚠️ No FCM token found for user: $recipientUserId")
            }
        }
    }

    /**
     * Send notification to multiple contacts
     */
    fun sendEmergencyAlertToContacts(
        context: Context,
        contacts: List<EmergencyContact>,
        senderName: String,
        senderEmail: String,
        alertType: String,
        message: String,
        location: String,
        latitude: Double?,
        longitude: Double?
    ) {
        Log.d(TAG, "📤 Sending alert to ${contacts.size} contacts")

        contacts.forEach { contact ->
            // Try to find user by email
            findUserIdByEmail(contact.email) { userId ->
                if (userId != null) {
                    sendEmergencyAlert(
                        context = context,
                        recipientUserId = userId,
                        senderName = senderName,
                        senderEmail = senderEmail,
                        alertType = alertType,
                        message = message,
                        location = location,
                        latitude = latitude,
                        longitude = longitude
                    )
                } else {
                    Log.w(TAG, "⚠️ User ID not found for email: ${contact.email}")
                }
            }
        }
    }

    /**
     * Create notification entry in Firebase Database
     * The recipient's app will listen to this and show local notification
     */
    private fun createNotificationInDatabase(
        recipientUserId: String,
        senderName: String,
        senderEmail: String,
        alertType: String,
        message: String,
        location: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val database = FirebaseDatabase.getInstance()
        val notificationId = database.reference.push().key ?: return

        val notificationData = hashMapOf(
            "notificationId" to notificationId,
            "type" to alertType,
            "fromUser" to senderName,
            "fromEmail" to senderEmail,
            "message" to message,
            "location" to location,
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        // Save to recipient's notifications path
        database.reference
            .child("notifications")
            .child(recipientUserId)
            .child(notificationId)
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Notification saved to database for user: $recipientUserId")

                // Also increment unread count
                incrementUnreadCount(recipientUserId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save notification: ${e.message}", e)
            }
    }

    /**
     * Increment unread notification count
     */
    private fun incrementUnreadCount(userId: String) {
        val database = FirebaseDatabase.getInstance()
        val unreadRef = database.reference
            .child("users")
            .child(userId)
            .child("unreadNotifications")

        unreadRef.get().addOnSuccessListener { snapshot ->
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            unreadRef.setValue(currentCount + 1)
        }
    }

    /**
     * Get FCM token from Firebase Database
     */
    private fun getFcmToken(userId: String, callback: (String?) -> Unit) {
        FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .child(userId)
            .child("fcmToken")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val token = snapshot.getValue(String::class.java)
                    callback(token)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Error getting FCM token: ${error.message}")
                    callback(null)
                }
            })
    }

    /**
     * Find user ID by email address
     */
    private fun findUserIdByEmail(email: String, callback: (String?) -> Unit) {
        FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .orderByChild("email")
            .equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userId = snapshot.children.first().key
                        callback(userId)
                    } else {
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Error finding user by email: ${error.message}")
                    callback(null)
                }
            })
    }

    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(userId: String, notificationId: String) {
        FirebaseDatabase.getInstance()
            .reference
            .child("notifications")
            .child(userId)
            .child(notificationId)
            .child("read")
            .setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Notification marked as read")
                decrementUnreadCount(userId)
            }
    }

    /**
     * Decrement unread notification count
     */
    private fun decrementUnreadCount(userId: String) {
        val database = FirebaseDatabase.getInstance()
        val unreadRef = database.reference
            .child("users")
            .child(userId)
            .child("unreadNotifications")

        unreadRef.get().addOnSuccessListener { snapshot ->
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            if (currentCount > 0) {
                unreadRef.setValue(currentCount - 1)
            }
        }
    }

    /**
     * Get all notifications for a user
     */
    fun getNotifications(userId: String, callback: (List<Map<String, Any>>) -> Unit) {
        FirebaseDatabase.getInstance()
            .reference
            .child("notifications")
            .child(userId)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = mutableListOf<Map<String, Any>>()

                    snapshot.children.forEach { child ->
                        val notification = child.value as? Map<String, Any>
                        if (notification != null) {
                            notifications.add(notification)
                        }
                    }

                    // Sort by timestamp (newest first)
                    notifications.sortByDescending {
                        (it["timestamp"] as? Long) ?: 0L
                    }

                    callback(notifications)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Error getting notifications: ${error.message}")
                    callback(emptyList())
                }
            })
    }
}