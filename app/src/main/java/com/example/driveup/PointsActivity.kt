package com.example.driveup

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PointsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points)

        val tvPoints = findViewById<TextView>(R.id.tvPoints)
        val tvKm = findViewById<TextView>(R.id.tvTotalKm)

        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val points = doc.getLong("points") ?: 0
                val totalKm = doc.getDouble("totalKm") ?: 0.0

                tvPoints.text = points.toString()
                tvKm.text = "%.1f km".format(totalKm)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando puntos", Toast.LENGTH_SHORT).show()
            }
    }
}
