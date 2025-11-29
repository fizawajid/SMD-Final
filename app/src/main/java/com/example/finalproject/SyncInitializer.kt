package com.example.finalproject

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncInitializer {

    private const val TAG = "SyncInitializer"

    /**
     * Initialize the automatic sync system
     * Call this from your Application class or MainActivity onCreate
     */
    fun initialize(context: Context) {
        try {
            Log.d(TAG, "🚀 Initializing automatic alert sync system...")

            // Schedule periodic sync (every 15 minutes when connected)
            AlertSyncWorker.schedulePeriodicSync(context)

            // Check for pending alerts immediately if online
            checkAndSyncNow(context)

            Log.d(TAG, "✅ Automatic sync system initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing sync system: ${e.message}", e)
        }
    }

    /**
     * Manually trigger sync check
     */
    fun checkAndSyncNow(context: Context) {
        try {
            if (com.example.finalproject.utils.NetworkUtils.isNetworkAvailable(context)) {
                AlertSyncWorker.scheduleSync(context)
                Log.d(TAG, "📡 Manual sync check triggered")
            } else {
                Log.d(TAG, "⚠️ No network available for manual sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering manual sync: ${e.message}", e)
        }
    }

    /**
     * Cancel all scheduled sync work
     */
    fun cancelSync(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(AlertSyncWorker.WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("${AlertSyncWorker.WORK_NAME}_periodic")
            Log.d(TAG, "🛑 All sync work cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling sync: ${e.message}", e)
        }
    }
}