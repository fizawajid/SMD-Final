package com.example.finalproject

data class EmergencyContact(
    val id: String = "",
    val fullName: String = "",
    val relationship: String = "",
    val priorityLevel: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val smsEnabled: Boolean = true,
    val callEnabled: Boolean = true,
    val emailEnabled: Boolean = true,
    val medicalInfo: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)