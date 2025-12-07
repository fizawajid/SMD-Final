package com.example.finalproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.finalproject.FcmTokenManager

class gotosettings : AppCompatActivity() {

    private lateinit var switchShakeDetection: SwitchCompat
    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var btnTestShake: LinearLayout
    private lateinit var btnSignOut: LinearLayout
    private lateinit var btnDeleteAccount: LinearLayout

    // Firebase
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupClickListeners()
        loadShakeSettings()
    }

    private fun initializeViews() {
        switchShakeDetection = findViewById(R.id.switchShakeDetection)
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        btnTestShake = findViewById(R.id.btnTestShake)
        btnSignOut = findViewById(R.id.btnSignOut)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        // Set up sensitivity seekbar (0-100, where 0=low, 50=medium, 100=high)
        seekBarSensitivity.max = 100
        seekBarSensitivity.progress = 50 // Default to medium
    }

    private fun setupClickListeners() {
        // Back button
        val backButton = findViewById<ImageView>(R.id.back)
        backButton?.setOnClickListener {
            finish()
        }

        // Offline alerts
        val btnoffline = findViewById<LinearLayout>(R.id.btnoffline)
        btnoffline.setOnClickListener {
            startActivity(Intent(this, OfflineAlertsActivity::class.java))
        }

        // Sign out button
        btnSignOut.setOnClickListener {
            showSignOutDialog()
        }

        // Delete account button
        btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }

        // Test shake detection button
        btnTestShake.setOnClickListener {
            if (ShakeDetectionManager.isEnabled(this)) {
                testShakeDetection()
            } else {
                Toast.makeText(this, "Please enable shake detection first", Toast.LENGTH_SHORT).show()
            }
        }

        // Shake detection switch
        switchShakeDetection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndEnableShake()
            } else {
                disableShakeDetection()
            }
        }

        // Sensitivity seekbar
        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ShakeDetectionManager.isEnabled(this@gotosettings)) {
                    updateShakeSensitivity(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showSignOutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        try {
            // Stop shake detection service if running
            if (ShakeDetectionManager.isEnabled(this)) {
                ShakeDetectorService.stop(this)
                ShakeDetectionManager.setEnabled(this, false)
            }

            // Clear local preferences
            getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            FcmTokenManager.deleteFcmToken()
            // Sign out from Firebase
            auth.signOut()

            // Show success message
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()

            // Navigate to login screen
            val intent = Intent(this, getstarted::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error signing out: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Account")
            .setMessage("This action is permanent and cannot be undone.\n\n" +
                    "All your data including:\n" +
                    "• Profile information\n" +
                    "• Emergency contacts\n" +
                    "• Settings and preferences\n\n" +
                    "will be permanently deleted.\n\n" +
                    "Are you absolutely sure?")
            .setPositiveButton("Delete") { _, _ ->
                confirmDeleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAccount() {
        // Second confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Final Confirmation")
            .setMessage("Type 'DELETE' to confirm account deletion")
            .setPositiveButton("Confirm") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val userId = currentUser.uid

            // Stop shake detection
            if (ShakeDetectionManager.isEnabled(this)) {
                ShakeDetectorService.stop(this)
            }

            // Delete user data from Realtime Database
            val database = FirebaseDatabase.getInstance().reference
            database.child("users").child(userId).removeValue()
                .addOnSuccessListener {
                    // Delete Firebase Auth account
                    currentUser.delete()
                        .addOnSuccessListener {
                            // Clear local data
                            clearAllLocalData()

                            Toast.makeText(
                                this,
                                "Account deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Navigate to login
                            val intent = Intent(this, getstarted::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Error deleting account: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error deleting user data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearAllLocalData() {
        // Clear all shared preferences
        getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE).edit().clear().apply()

        // Clear any other app-specific data
        val sharedPrefsDir = filesDir.parentFile?.listFiles()?.find { it.name == "shared_prefs" }
        sharedPrefsDir?.listFiles()?.forEach { it.delete() }
    }

    private fun testShakeDetection() {
        AlertDialog.Builder(this)
            .setTitle("🧪 Test Shake Detection")
            .setMessage("This will simulate a shake and send test emergency alerts to all your contacts.\n\n⚠️ Real emails will be sent!\n\nContinue?")
            .setPositiveButton("Yes, Send Test") { _, _ ->
                // Trigger the shake detection manually
                val intent = Intent("com.example.finalproject.TEST_SHAKE")
                sendBroadcast(intent)

                Toast.makeText(
                    this,
                    "🧪 Test alert triggered! Check notifications and logcat",
                    Toast.LENGTH_LONG
                ).show()

                // Also show info about what to check
                android.os.Handler(mainLooper).postDelayed({
                    AlertDialog.Builder(this)
                        .setTitle("📱 What to Check")
                        .setMessage(
                            "You should see:\n\n" +
                                    "1. 🚨 SHAKE DETECTED notification\n" +
                                    "2. 📍 Getting Location notification\n" +
                                    "3. 📧 Sending Alerts notification\n" +
                                    "4. ✅ Final success notification\n" +
                                    "5. Emails in your contacts' inboxes\n\n" +
                                    "Check Logcat for detailed logs with emojis!"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }, 1000)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadShakeSettings() {
        // Load shake detection state
        val isEnabled = ShakeDetectionManager.isEnabled(this)
        switchShakeDetection.isChecked = isEnabled

        // Load sensitivity settings
        val prefs = getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE)
        val sensitivity = prefs.getInt("shake_sensitivity", 50)
        seekBarSensitivity.progress = sensitivity

        // Enable/disable sensitivity control based on shake detection state
        seekBarSensitivity.isEnabled = isEnabled
    }

    private fun checkPermissionsAndEnableShake() {
        val permissionsNeeded = mutableListOf<String>()

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            showPermissionDialog(permissionsNeeded)
        } else {
            enableShakeDetection()
        }
    }

    private fun showPermissionDialog(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Shake detection requires:\n\n" +
                    "📍 Location - To send your location in emergency alerts\n" +
                    "🔔 Notifications - To show service status\n\n" +
                    "These permissions are essential for emergency response.")
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    LOCATION_PERMISSION_REQUEST
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                switchShakeDetection.isChecked = false
                Toast.makeText(this, "Permissions required for shake detection", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableShakeDetection()
            } else {
                switchShakeDetection.isChecked = false
                Toast.makeText(this, "Permissions denied. Cannot enable shake detection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableShakeDetection() {
        ShakeDetectionManager.setEnabled(this, true)
        seekBarSensitivity.isEnabled = true

        // Save sensitivity
        val sensitivity = seekBarSensitivity.progress
        saveSensitivity(sensitivity)

        Toast.makeText(
            this,
            "✅ Shake detection enabled\nShake your phone to send emergency alerts",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun disableShakeDetection() {
        ShakeDetectionManager.setEnabled(this, false)
        seekBarSensitivity.isEnabled = false
        Toast.makeText(this, "Shake detection disabled", Toast.LENGTH_SHORT).show()
    }

    private fun updateShakeSensitivity(progress: Int) {
        saveSensitivity(progress)

        // Calculate threshold based on progress (inverse relationship)
        val threshold = when {
            progress < 34 -> 20.0f - (progress / 33f * 5f)
            progress < 67 -> 15.0f - ((progress - 33) / 33f * 5f)
            else -> 10.0f - ((progress - 66) / 34f * 5f)
        }

        // Restart service with new threshold if it's running
        if (ShakeDetectionManager.isEnabled(this)) {
            ShakeDetectorService.stop(this)

            // Small delay to ensure service stops before restarting
            android.os.Handler(mainLooper).postDelayed({
                ShakeDetectorService.start(this)
            }, 500)
        }

        val sensitivityText = when {
            progress < 34 -> "Low"
            progress < 67 -> "Medium"
            else -> "High"
        }

        Toast.makeText(
            this,
            "Sensitivity: $sensitivityText",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveSensitivity(progress: Int) {
        getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE)
            .edit()
            .putInt("shake_sensitivity", progress)
            .apply()
    }

    override fun onResume() {
        super.onResume()
        loadShakeSettings()
    }
}