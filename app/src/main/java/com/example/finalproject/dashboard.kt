package com.example.finalproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.finalproject.repository.AlertRepository
import com.example.finalproject.SyncInitializer
import kotlinx.coroutines.launch
import com.example.finalproject.FcmTokenManager

class dashboard : AppCompatActivity() {

    private lateinit var alertRepository: AlertRepository
    private lateinit var tvPendingAlerts: TextView
    private lateinit var llPendingAlertsIndicator: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard)

        // Initialize repository
        alertRepository = AlertRepository(this)

        // Initialize views
        initializeViews()
        setupClickListeners()

        // Check for pending alerts
        checkPendingAlerts()

        // ⭐ INITIALIZE FCM TOKEN ⭐
        FcmTokenManager.initializeFcmToken(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()
    }

    private fun initializeViews() {
        // Existing views
        val settings = findViewById<ImageView>(R.id.setting)
        val history = findViewById<ImageView>(R.id.history)
        val contacts = findViewById<ImageView>(R.id.contacts)
        val emergencyAlertBtn = findViewById<Button>(R.id.btnEmergencyAlert)

        // You may need to add these to your dashboard.xml layout:
        // tvPendingAlerts = findViewById(R.id.tvPendingAlerts)
        // llPendingAlertsIndicator = findViewById(R.id.llPendingAlertsIndicator)
    }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Dashboard", "✅ Notification permission granted")
                    // Re-initialize FCM token now that permission is granted
                    FcmTokenManager.initializeFcmToken(this)
                } else {
                    Log.w("Dashboard", "⚠️ Notification permission denied")
                    Toast.makeText(
                        this,
                        "Please enable notifications to receive emergency alerts",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun setupClickListeners() {
        val settings = findViewById<ImageView>(R.id.setting)
        val history = findViewById<ImageView>(R.id.history)
        val contacts = findViewById<ImageView>(R.id.contacts)
        val emergencyAlertBtn = findViewById<Button>(R.id.btnEmergencyAlert)

        settings.setOnClickListener {
            startActivity(Intent(this, gotosettings::class.java))
        }

        history.setOnClickListener {
            startActivity(Intent(this, alerthistory::class.java))
        }

        contacts.setOnClickListener {
            startActivity(Intent(this, emergency_contacts::class.java))
        }

        emergencyAlertBtn.setOnClickListener {
            startActivity(Intent(this, emergency_type::class.java))
        }

        // Add click listener for pending alerts indicator (if exists)
        try {
            llPendingAlertsIndicator?.setOnClickListener {
                startActivity(Intent(this, OfflineAlertsActivity::class.java))
            }
        } catch (e: Exception) {
            // View doesn't exist in layout
        }
    }

    private fun checkPendingAlerts() {
        lifecycleScope.launch {
            try {
                val pendingCount = alertRepository.getPendingAlertsCount()

                updatePendingAlertsUI(pendingCount)

                // If there are pending alerts and we're online, trigger sync
                if (pendingCount > 0 && com.example.finalproject.utils.NetworkUtils.isNetworkAvailable(this@dashboard)) {
                    SyncInitializer.checkAndSyncNow(this@dashboard)
                }
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error checking pending alerts", e)
            }
        }
    }

    private fun updatePendingAlertsUI(count: Int) {
        try {
            if (count > 0) {
                tvPendingAlerts?.text = "$count alert${if (count != 1) "s" else ""} pending sync"
                tvPendingAlerts?.visibility = View.VISIBLE
                llPendingAlertsIndicator?.visibility = View.VISIBLE
            } else {
                tvPendingAlerts?.visibility = View.GONE
                llPendingAlertsIndicator?.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Views don't exist in layout
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh pending alerts count when returning to dashboard
        checkPendingAlerts()
    }
}