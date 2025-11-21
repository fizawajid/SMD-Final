package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class signin_phone : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin_phone)   // your XML layout file

        // Get views from XML
        val btnSignInPhone = findViewById<Button>(R.id.btnSignIn)
        val tvSignupPhone = findViewById<TextView>(R.id.tvSignUp)

        // 👉 Sign In → Dashboard
        btnSignInPhone.setOnClickListener {
            val intent = Intent(this, dashboard::class.java)
            startActivity(intent)
        }

        // 👉 Signup → Create Account (Phone)
        tvSignupPhone.setOnClickListener {
            val intent = Intent(this, createaccount_phone::class.java)
            startActivity(intent)
        }
    }
}
