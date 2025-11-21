package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class emergency_contacts : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.emergency_contacts) // your XML layout

        val addButton = findViewById<TextView>(R.id.btn_add_contact)

        addButton.setOnClickListener {
            // Open the add_emergency_contacts activity
            val intent = Intent(this, add_emergency_contact::class.java)
            startActivity(intent)
        }
    }
}
