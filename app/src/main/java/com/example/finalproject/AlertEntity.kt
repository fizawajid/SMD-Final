package com.example.finalproject.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "offline_alerts")
@TypeConverters(Converters::class)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val alertId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val type: String = "",
    val message: String = "",
    val additionalMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String = "",
    val contactsNotified: Int = 0,
    val contactsJson: String = "", // JSON string of contacts list
    val status: String = "pending", // pending, synced, failed
    val imagePath: String? = null, // Optional: path to local image
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
)