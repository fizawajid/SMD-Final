package com.example.finalproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.finalproject.database.AlertEntity
import com.example.finalproject.repository.AlertRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OfflineAlertsActivity : AppCompatActivity() {

    private lateinit var alertsContainer: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var tvPendingCount: TextView
    private lateinit var emptyState: LinearLayout

    private lateinit var alertRepository: AlertRepository
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_alerts)

        alertRepository = AlertRepository(this)

        initializeViews()
        setupClickListeners()
        loadOfflineAlerts()
    }

    private fun initializeViews() {
        alertsContainer = findViewById(R.id.alerts_container)
        btnBack = findViewById(R.id.btn_back)
        tvPendingCount = findViewById(R.id.tv_pending_count)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
    }

    private fun loadOfflineAlerts() {
        lifecycleScope.launch {
            val pendingAlerts = alertRepository.getPendingAlerts()
            val userAlerts = pendingAlerts.filter { it.userId == userId }

            if (userAlerts.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                alertsContainer.visibility = View.GONE
                tvPendingCount.text = "0 pending alerts"
            } else {
                emptyState.visibility = View.GONE
                alertsContainer.visibility = View.VISIBLE
                tvPendingCount.text = "${userAlerts.size} pending alert${if (userAlerts.size != 1) "s" else ""}"

                displayAlerts(userAlerts)
            }
        }
    }

    private fun displayAlerts(alerts: List<AlertEntity>) {
        alertsContainer.removeAllViews()

        for (alert in alerts) {
            val alertView = createAlertView(alert)
            alertsContainer.addView(alertView)
        }
    }

    private fun createAlertView(alert: AlertEntity): View {
        val alertView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.margin_medium))
            }
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.main_card_border, null)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium)
            )
            // Long press to show delete option
            setOnLongClickListener {
                showDeleteDialog(alert)
                true
            }
        }

        // Header
        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(32, 32).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.margin_medium)
            }
            setImageResource(getAlertIcon(alert.type))
        }
        headerRow.addView(icon)

        val titleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = alert.type
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleLayout.addView(tvTitle)

        val tvStatus = TextView(this).apply {
            text = "Pending Sync"
            setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
            textSize = 11f
        }
        titleLayout.addView(tvStatus)

        headerRow.addView(titleLayout)

        // Delete button
        val btnDelete = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            setColorFilter(resources.getColor(android.R.color.holo_red_light, null))
            setOnClickListener {
                showDeleteDialog(alert)
            }
        }
        headerRow.addView(btnDelete)

        alertView.addView(headerRow)

        // Details
        val tvDateTime = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
            }
            text = formatTimestamp(alert.timestamp)
            setTextColor(resources.getColor(R.color.grey_primary, null))
            textSize = 12f
        }
        alertView.addView(tvDateTime)

        if (alert.additionalMessage.isNotEmpty()) {
            val tvMessage = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
                }
                text = alert.additionalMessage
                setTextColor(resources.getColor(android.R.color.white, null))
                textSize = 13f
            }
            alertView.addView(tvMessage)
        }

        val tvContacts = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
            }
            text = "${alert.contactsNotified} contact${if (alert.contactsNotified != 1) "s" else ""} to notify"
            setTextColor(resources.getColor(R.color.grey_primary, null))
            textSize = 11f
        }
        alertView.addView(tvContacts)

        return alertView
    }

    private fun showDeleteDialog(alert: AlertEntity) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Alert")
            .setMessage("Are you sure you want to delete this pending alert?\n\nType: ${alert.type}\nTime: ${formatTimestamp(alert.timestamp)}")
            .setPositiveButton("Delete") { _, _ ->
                deleteAlert(alert)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAlert(alert: AlertEntity) {
        lifecycleScope.launch {
            val result = alertRepository.deleteAlert(alert.id)
            result.onSuccess {
                Toast.makeText(
                    this@OfflineAlertsActivity,
                    "Alert deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()
                loadOfflineAlerts() // Reload the list
            }.onFailure {
                Toast.makeText(
                    this@OfflineAlertsActivity,
                    "Failed to delete alert: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getAlertIcon(type: String): Int {
        return when {
            type.contains("Personal", ignoreCase = true) -> R.drawable.red_warning
            type.contains("Travel", ignoreCase = true) -> R.drawable.group
            type.contains("Medical", ignoreCase = true) -> R.drawable.error
            else -> R.drawable.error
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}