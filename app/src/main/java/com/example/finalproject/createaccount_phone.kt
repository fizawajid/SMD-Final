package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class createaccount_phone : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_createaccount_phone)

        // IDs from your XML layout
        val tvEmail = findViewById<TextView>(R.id.email)
        val tvLoginPhone = findViewById<TextView>(R.id.login)
        val btnContinuePhone = findViewById<Button>(R.id.next)

        // 👉 Email pressed → go to createaccount_email
        tvEmail.setOnClickListener {
            val intent = Intent(this, createaccount_email::class.java)
            startActivity(intent)
        }

        // 👉 Login pressed → go to signin_phone
        tvLoginPhone.setOnClickListener {
            val intent = Intent(this, signin_phone::class.java)
            startActivity(intent)
        }

        // 👉 Next pressed → go to dashboard
        btnContinuePhone.setOnClickListener {
            val intent = Intent(this, otp::class.java)
            startActivity(intent)
        }
    }
}
