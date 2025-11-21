package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class signin_email : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin_email)   // your XML layout name

        // Find the views
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val tvSignup = findViewById<TextView>(R.id.tvSignUp)

        // 👉 Sign In button → go to dashboard
        btnSignIn.setOnClickListener {
            val intent = Intent(this, dashboard::class.java)
            startActivity(intent)
        }

        // 👉 Signup text → go to createaccount_email
        tvSignup.setOnClickListener {
            val intent = Intent(this, createaccount_email::class.java)
            startActivity(intent)
        }
    }
}
