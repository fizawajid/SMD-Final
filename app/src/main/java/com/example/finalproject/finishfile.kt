package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class finishfile : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.finishpage)

        val etAllergy = findViewById<EditText>(R.id.etAllergy)
        val etMedicine = findViewById<EditText>(R.id.etMedicine)
        val etNotes = findViewById<EditText>(R.id.etNotes)
        val switchLocation = findViewById<Switch>(R.id.switchLocation)
        val btn = findViewById<Button>(R.id.btnSignIn)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

        btn.setOnClickListener {

            val allergies = etAllergy.text.toString()
            val medication = etMedicine.text.toString()
            val notes = etNotes.text.toString()
            val locationEnabled = switchLocation.isChecked

            val data = FinishData(
                allergies = allergies,
                medication = medication,
                notes = notes,
                locationEnabled = locationEnabled
            )

            val ref = FirebaseDatabase.getInstance()
                .getReference("finish")
                .child(userId)

            ref.setValue(data).addOnSuccessListener {
                Toast.makeText(this, "Setup saved successfully!", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, dashboard::class.java))
                finish()

            }.addOnFailureListener {
                Toast.makeText(this, "Error saving data: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}


data class FinishData(
    val allergies: String = "",
    val medication: String = "",
    val notes: String = "",
    val locationEnabled: Boolean = false
)

