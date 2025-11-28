package com.example.finalproject


import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class gotosettings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val btnoffline = findViewById<ImageView>(R.id.offline)
        btnoffline.setOnClickListener {
            startActivity(android.content.Intent(this, OfflineAlertsActivity::class.java))

        }
    }
    }
