package com.example.finalproject

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request.Method.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.finalproject.repository.AlertRepository
import com.example.finalproject.utils.NetworkUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject

class travel_safety : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var btnBack: ImageView
    private lateinit var etAdditionalMessage: EditText
    private lateinit var contactsContainer: LinearLayout
    private lateinit var btnSendAlert: TextView
    private lateinit var tvContactCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressStatus: TextView
    private lateinit var progressContainer: LinearLayout

    private val contactsList = mutableListOf<EmergencyContact>()
    private var finishData: FinishData? = null
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var locationAddress: String = "Location unavailable"

    // Offline storage
    private lateinit var alertRepository: AlertRepository
    private val gson = Gson()

    // Email tracking
    private var emailsSent = 0
    private var emailsFailed = 0
    private var totalEmailsToSend = 0

    // Get sender email from logged in user
    private val SENDER_EMAIL = FirebaseAuth.getInstance().currentUser?.email ?: "noreply@safeme.com"

    // EmailJS credentials
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"

    companion object {
        private const val TAG = "TravelSafety"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.travel_safety)

        // Initialize repository and location client
        alertRepository = AlertRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializeViews()
        setupClickListeners()
        loadFinishData()
        loadEmergencyContacts()
        checkLocationPermission()

        Log.d(TAG, "Sender email: $SENDER_EMAIL")
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        etAdditionalMessage = findViewById(R.id.etAdditionalMessage)
        btnSendAlert = findViewById(R.id.btnSendAlert)
        tvContactCount = findViewById(R.id.tvContactCount)
        contactsContainer = findViewById(R.id.contactsContainer)
        progressBar = findViewById(R.id.progressBar)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        progressContainer = findViewById(R.id.progressContainer)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnSendAlert.setOnClickListener { sendEmergencyAlert() }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission granted, get location
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("This app needs location access to send your current location in travel emergency alerts. This is especially important when traveling in unfamiliar areas.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestLocationPermission()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                // Request permission
                requestLocationPermission()
            }
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Alerts will be sent without location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    locationAddress = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    Log.d(TAG, "Location obtained: $locationAddress")

                    // Optionally reverse geocode to get address
                    reverseGeocodeLocation(location)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location error", e)
        }
    }

    private fun reverseGeocodeLocation(location: Location) {
        try {
            val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                locationAddress = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                }
                Log.d(TAG, "Address: $locationAddress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            // Keep using coordinates if geocoding fails
        }
    }

    private fun getGoogleMapsLink(): String {
        return currentLocation?.let {
            "https://www.google.com/maps?q=${it.latitude},${it.longitude}"
        } ?: "Location unavailable"
    }

    private fun loadFinishData() {
        val finishRef = FirebaseDatabase.getInstance().getReference("finish").child(userId)

        finishRef.get().addOnSuccessListener { snapshot ->
            finishData = snapshot.getValue(FinishData::class.java)
            Log.d(TAG, "Finish data loaded: $finishData")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to load finish data", e)
            Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEmergencyContacts() {
        database = FirebaseDatabase.getInstance().getReference("emergency_contacts")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactsList.clear()
                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                    contact?.let {
                        contactsList.add(it)
                        Log.d(TAG, "Loaded contact: ${it.fullName}, Email: ${it.email}")
                    }
                }

                contactsList.sortWith(compareBy {
                    when (it.priorityLevel) {
                        "High" -> 1
                        "Medium" -> 2
                        "Low" -> 3
                        else -> 4
                    }
                })
                displayContacts()
                updateContactCount()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load contacts", error.toException())
                Toast.makeText(this@travel_safety, "Failed to load contacts: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayContacts() {
        contactsContainer.removeAllViews()
        if (contactsList.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No emergency contacts found. Please add contacts first."
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 14f
                setPadding(16, 16, 16, 16)
            }
            contactsContainer.addView(emptyView)
            return
        }
        for (contact in contactsList) {
            val contactView = createContactView(contact)
            contactsContainer.addView(contactView)
        }
    }

    private fun createContactView(contact: EmergencyContact): View {
        val contactView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(getAvatarResource(contact))
        }
        contactView.addView(imgAvatar)

        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
            orientation = LinearLayout.VERTICAL
        }

        val tvName = TextView(this).apply {
            text = contact.fullName
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentLayout.addView(tvName)

        val tvPhone = TextView(this).apply {
            text = contact.phoneNumber
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 14f
        }
        contentLayout.addView(tvPhone)

        if (!contact.email.isNullOrBlank()) {
            val tvEmail = TextView(this).apply {
                text = contact.email
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 12f
            }
            contentLayout.addView(tvEmail)
        }

        contactView.addView(contentLayout)
        return contactView
    }

    private fun getAvatarResource(contact: EmergencyContact): Int {
        return when {
            contact.fullName.contains("mom", ignoreCase = true) ||
                    contact.fullName.contains("mother", ignoreCase = true) ||
                    contact.relationship.equals("mother", ignoreCase = true) -> R.drawable.female

            contact.fullName.contains("dad", ignoreCase = true) ||
                    contact.fullName.contains("father", ignoreCase = true) ||
                    contact.relationship.equals("father", ignoreCase = true) -> R.drawable.male

            else -> R.drawable.male
        }
    }

    private fun updateContactCount() {
        val emailCount = contactsList.count { !it.email.isNullOrBlank() }
        tvContactCount.text = "Contacts to Notify ($emailCount with email / ${contactsList.size} total)"
    }

    private fun sendEmergencyAlert() {
        val contactsWithEmail = contactsList.filter { !it.email.isNullOrBlank() }

        if (contactsWithEmail.isEmpty()) {
            Toast.makeText(this, "No contacts with email found.", Toast.LENGTH_LONG).show()
            return
        }

        // Refresh location before sending
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }

        showProgress()
        btnSendAlert.isEnabled = false
        btnSendAlert.alpha = 0.5f

        val message = buildAlertMessage(etAdditionalMessage.text.toString())

        // Save alert to local DB or Firebase based on connectivity
        lifecycleScope.launch {
            saveAlertToStorage(message, contactsWithEmail)
        }

        // Only try to send emails if online
        if (NetworkUtils.isNetworkAvailable(this)) {
            emailsSent = 0
            emailsFailed = 0
            totalEmailsToSend = contactsWithEmail.size

            for (contact in contactsWithEmail) {
                sendEmail(contact.email!!, contact.fullName, message)
            }
        } else {
            // Offline mode - just save locally
            tvProgressStatus.text = "Alert saved locally (offline)"
            Toast.makeText(this, "No internet. Alert saved locally and will sync when online.", Toast.LENGTH_LONG).show()

            btnSendAlert.postDelayed({
                hideProgress()
                finish()
            }, 2000)
        }
    }

    private suspend fun saveAlertToStorage(message: String, contacts: List<EmergencyContact>) {
        val contactsJson = gson.toJson(contacts.map {
            hashMapOf(
                "name" to it.fullName,
                "phone" to it.phoneNumber,
                "email" to (it.email ?: ""),
                "priority" to it.priorityLevel
            )
        })

        val locationString = currentLocation?.let {
            "$locationAddress (${it.latitude}, ${it.longitude})"
        } ?: "Location unavailable"

        val result = alertRepository.saveAlert(
            userId = userId,
            userEmail = SENDER_EMAIL,
            type = "Travel Emergency",
            message = message,
            additionalMessage = etAdditionalMessage.text.toString(),
            contactsJson = contactsJson,
            contactsNotified = contacts.size,
            location = locationString
        )

        result.onSuccess {
            Log.d(TAG, "Alert saved: ${it}")
        }.onFailure {
            Log.e(TAG, "Failed to save alert", it)
        }
    }

    private fun showProgress() {
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgressStatus.text = "Sending alerts..."
    }

    private fun hideProgress() {
        progressContainer.visibility = View.GONE
        btnSendAlert.isEnabled = true
        btnSendAlert.alpha = 1.0f
    }

    private fun updateProgress(completed: Int, total: Int) {
        val progress = if (total > 0) (completed * 100) / total else 0
        progressBar.progress = progress
        tvProgressStatus.text = "Sending alerts... ($completed/$total)"
    }

    private fun buildAlertMessage(additionalMessage: String): String {
        val sb = StringBuilder()
        sb.append("🚨 TRAVEL EMERGENCY ALERT 🚨\n\n")
        sb.append("From: $SENDER_EMAIL\n")
        sb.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

        // Add location information - CRITICAL FOR TRAVEL EMERGENCIES
        sb.append("📍 CURRENT LOCATION:\n")
        if (currentLocation != null) {
            sb.append("Address: $locationAddress\n")
            sb.append("Coordinates: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}\n")
            sb.append("Google Maps: ${getGoogleMapsLink()}\n\n")
        } else {
            sb.append("Location: Unable to determine current location\n\n")
        }

        if (additionalMessage.isNotEmpty()) sb.append("Message: $additionalMessage\n\n")

        finishData?.let { data ->
            sb.append("=== Medical Information ===\n")
            if (data.allergies.isNotEmpty()) sb.append("Allergies: ${data.allergies}\n")
            if (data.medication.isNotEmpty()) sb.append("Medication: ${data.medication}\n")
            if (data.notes.isNotEmpty()) sb.append("Medical Notes: ${data.notes}\n")
            if (data.locationEnabled) sb.append("\n📍 Location sharing is enabled\n")
        }

        sb.append("\n🌍 TRAVEL EMERGENCY - IMMEDIATE ASSISTANCE REQUIRED 🌍")
        sb.append("\nThis is an automated emergency alert. Please respond immediately.")
        return sb.toString()
    }

    private fun sendEmail(toEmail: String, contactName: String, message: String) {
        val url = "https://api.emailjs.com/api/v1.0/email/send"

        val jsonBody = JSONObject().apply {
            put("service_id", EMAILJS_SERVICE_ID)
            put("template_id", EMAILJS_TEMPLATE_ID)
            put("user_id", EMAILJS_PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("contact_name", contactName)
                put("alert_message", message)
                // Add separate location fields for better email formatting
                if (currentLocation != null) {
                    put("location_address", locationAddress)
                    put("location_coordinates", "${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
                    put("google_maps_link", getGoogleMapsLink())
                }
            })
        }

        val request = object : JsonObjectRequest(
            POST, url, jsonBody,
            { response ->
                emailsSent++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkIfAllEmailsSent()
            },
            { error ->
                emailsFailed++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkIfAllEmailsSent()
                Log.e(TAG, "Error sending email: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Content-Type" to "application/json",
                    "origin" to "http://localhost",
                    "Authorization" to "Bearer $EMAILJS_PUBLIC_KEY"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun checkIfAllEmailsSent() {
        if (emailsSent + emailsFailed >= totalEmailsToSend) {
            runOnUiThread {
                updateProgress(totalEmailsToSend, totalEmailsToSend)


                btnSendAlert.postDelayed({
                    hideProgress()
                    if (emailsSent > 0) finish()
                }, 3000)
            }
        }
    }
}