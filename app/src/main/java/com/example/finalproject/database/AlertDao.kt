package com.example.finalproject.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity): Long
    
    @Update
    suspend fun updateAlert(alert: AlertEntity)
    
    @Delete
    suspend fun deleteAlert(alert: AlertEntity)
    
    @Query("SELECT * FROM offline_alerts WHERE id = :id")
    suspend fun getAlertById(id: Int): AlertEntity?
    
    @Query("SELECT * FROM offline_alerts WHERE alertId = :alertId")
    suspend fun getAlertByAlertId(alertId: String): AlertEntity?
    
    @Query("SELECT * FROM offline_alerts WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAlertsByUserId(userId: String): List<AlertEntity>
    
    @Query("SELECT * FROM offline_alerts WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAlertsByUserIdFlow(userId: String): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM offline_alerts WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getAlertsByStatus(status: String): List<AlertEntity>
    
    @Query("SELECT * FROM offline_alerts WHERE status = 'pending' ORDER BY timestamp ASC")
    suspend fun getPendingAlerts(): List<AlertEntity>
    
    @Query("SELECT COUNT(*) FROM offline_alerts WHERE status = 'pending'")
    suspend fun getPendingAlertsCount(): Int
    
    @Query("SELECT COUNT(*) FROM offline_alerts WHERE status = 'pending'")
    fun getPendingAlertsCountFlow(): Flow<Int>
    
    @Query("SELECT * FROM offline_alerts WHERE status = 'failed' AND syncAttempts < 3 ORDER BY timestamp ASC")
    suspend fun getFailedAlertsForRetry(): List<AlertEntity>
    
    @Query("UPDATE offline_alerts SET status = :status, lastSyncAttempt = :lastAttempt WHERE id = :id")
    suspend fun updateAlertStatus(id: Int, status: String, lastAttempt: Long = System.currentTimeMillis())
    
    @Query("UPDATE offline_alerts SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :lastAttempt WHERE id = :id")
    suspend fun incrementSyncAttempts(id: Int, lastAttempt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM offline_alerts WHERE status = 'synced' AND timestamp < :cutoffTime")
    suspend fun deleteOldSyncedAlerts(cutoffTime: Long)
    
    @Query("DELETE FROM offline_alerts WHERE status = 'failed' AND syncAttempts >= 3 AND timestamp < :cutoffTime")
    suspend fun deleteOldFailedAlerts(cutoffTime: Long)
    
    @Query("SELECT * FROM offline_alerts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAlerts(limit: Int): List<AlertEntity>
    
    @Query("SELECT * FROM offline_alerts WHERE type = :type AND userId = :userId ORDER BY timestamp DESC")
    suspend fun getAlertsByTypeAndUser(type: String, userId: String): List<AlertEntity>
    
    @Query("""
        SELECT * FROM offline_alerts 
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL 
        AND userId = :userId 
        ORDER BY timestamp DESC
    """)
    suspend fun getAlertsWithLocation(userId: String): List<AlertEntity>
    
    @Query("SELECT COUNT(*) FROM offline_alerts")
    suspend fun getTotalAlertsCount(): Int
    
    @Query("SELECT COUNT(*) FROM offline_alerts WHERE userId = :userId")
    suspend fun getUserAlertsCount(userId: String): Int

    @Query("DELETE FROM offline_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)
}