package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class dashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard)   // your XML file name

        // ImageViews
        val settings = findViewById<ImageView>(R.id.setting)
        val history = findViewById<ImageView>(R.id.history)
        val contacts = findViewById<ImageView>(R.id.contacts)

        // Button for Emergency Alert
        val emergencyAlertBtn = findViewById<Button>(R.id.btnEmergencyAlert)

        // Click listeners
        settings.setOnClickListener {
            startActivity(Intent(this, gotosettings::class.java))
        }

        history.setOnClickListener {
            startActivity(Intent(this, alerthistory::class.java))
        }

        contacts.setOnClickListener {
            startActivity(Intent(this, emergency_contacts::class.java))
        }

        // 👉 Emergency Alert pressed → go to emergency_type activity
        emergencyAlertBtn.setOnClickListener {
            startActivity(Intent(this, emergency_type::class.java))
        }
    }
}
