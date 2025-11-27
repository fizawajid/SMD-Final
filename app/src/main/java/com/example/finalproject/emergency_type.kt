package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class emergency_type : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.emergency_type)

        // Get the views
        val cardPersonalSafety = findViewById<FrameLayout>(R.id.card_personal_safety)
        val cardTravelEmergency = findViewById<FrameLayout>(R.id.card_travel_emergency)
        val cardMedicalEmergency = findViewById<FrameLayout>(R.id.card_medical_emergency)

        // 👉 When "Personal Safety" card is pressed
        cardPersonalSafety.setOnClickListener {
            startActivity(Intent(this, personal_safety::class.java))
        }

        // 👉 When "Travel Emergency" card is pressed
        cardTravelEmergency.setOnClickListener {
            startActivity(Intent(this, travel_safety::class.java))
        }

        cardMedicalEmergency.setOnClickListener {
            startActivity(Intent(this, medical_safety::class.java))
        }
    }
}
