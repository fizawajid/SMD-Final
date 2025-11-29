package com.example.finalproject

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.finalproject.repository.AlertRepository
import com.example.finalproject.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectivityReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConnectivityReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📡 Connectivity change detected")

        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                checkConnectivityAndSync(context)
            }
        }
    }

    private fun checkConnectivityAndSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isConnected = NetworkUtils.isNetworkAvailable(context)

                Log.d(TAG, "Network status: ${if (isConnected) "Connected ✅" else "Disconnected ❌"}")

                if (isConnected) {
                    // Check if there are pending alerts
                    val repository = AlertRepository(context)
                    val pendingCount = repository.getPendingAlertsCount()

                    if (pendingCount > 0) {
                        Log.d(TAG, "🔄 Found $pendingCount pending alerts, triggering sync...")

                        // Schedule immediate sync
                        AlertSyncWorker.scheduleSync(context)

                        // Show notification
                        showSyncStartNotification(context, pendingCount)
                    } else {
                        Log.d(TAG, "✅ No pending alerts to sync")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connectivity: ${e.message}", e)
            }
        }
    }

    private fun showSyncStartNotification(context: Context, pendingCount: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

            val notification = NotificationCompat.Builder(context, "alert_sync_channel")
                .setContentTitle("🔄 Syncing Alerts")
                .setContentText("Syncing $pendingCount pending alert${if (pendingCount != 1) "s" else ""}...")
                .setSmallIcon(R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(3000, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }
}