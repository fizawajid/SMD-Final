package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import kotlin.jvm.java

class createaccount_email : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_createaccount_email)

        // TextViews and Button from your XML
        val tvPhone = findViewById<TextView>(R.id.phone)
        val tvLogin = findViewById<TextView>(R.id.login)
        val btnContinue = findViewById<Button>(R.id.next)

        // 👉 Phone clicked → open createaccount_phone
        tvPhone.setOnClickListener {
            val intent = Intent(this, createaccount_phone::class.java)
            startActivity(intent)
        }

        // 👉 Login clicked → open signin_email
        tvLogin.setOnClickListener {
            val intent = Intent(this, signin_email::class.java)
            startActivity(intent)
        }

        // 👉 Continue button → open dashboard
        btnContinue.setOnClickListener {
            val intent = Intent(this, dashboard::class.java)
            startActivity(intent)
        }
    }
}
