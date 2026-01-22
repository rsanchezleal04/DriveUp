package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        auth = FirebaseAuth.getInstance()

        val btnContinue = findViewById<Button>(R.id.btnContinue)
        val btnResend = findViewById<Button>(R.id.btnResendEmail)

        btnContinue.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnResend.setOnClickListener {
            val user = auth.currentUser

            if (user == null) {
                toast("No hay usuario")
                return@setOnClickListener
            }

            user.reload()
                .addOnSuccessListener {
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            toast("Correo reenviado")
                        }
                        .addOnFailureListener {
                            toast(it.localizedMessage ?: "Error reenviando correo")
                        }
                }
                .addOnFailureListener {
                    toast("Error actualizando usuario")
                }
        }

    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
