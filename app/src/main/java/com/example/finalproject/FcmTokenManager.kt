package com.example.finalproject

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    /**
     * Initialize FCM and get token
     * Call this when user logs in or app starts
     */
    fun initializeFcmToken(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No user logged in, skipping FCM token initialization")
            return
        }

        Log.d(TAG, "🔄 Initializing FCM token for user: ${currentUser.uid}")

        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "❌ Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "✅ FCM Token: $token")

            // Save token to database
            saveFcmTokenToDatabase(token)
        }
    }

    /**
     * Save FCM token to Firebase Database
     */
    private fun saveFcmTokenToDatabase(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No user logged in")
            return
        }

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
    }

    /**
     * Delete FCM token when user logs out
     */
    fun deleteFcmToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No user logged in")
            return
        }

        val userId = currentUser.uid
        val database = FirebaseDatabase.getInstance()

        // Delete token from Firebase
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "✅ FCM token deleted")

                // Remove from database
                database.reference
                    .child("users")
                    .child(userId)
                    .child("fcmToken")
                    .removeValue()
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ FCM token removed from database")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to remove FCM token from database: ${e.message}")
                    }
            } else {
                Log.e(TAG, "❌ Failed to delete FCM token", task.exception)
            }
        }
    }

    /**
     * Refresh FCM token manually
     */
    fun refreshFcmToken() {
        Log.d(TAG, "🔄 Refreshing FCM token...")
        initializeFcmToken(null as? Context ?: return)
    }
}