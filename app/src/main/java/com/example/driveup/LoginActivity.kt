package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveup.Models.User

// 🔥 Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore



class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 🔥 SI YA HAY SESIÓN → DIRECTO A MAIN
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            login(etEmail.text.toString(), etPassword.text.toString())
        }

        btnRegister.setOnClickListener {
            register(etEmail.text.toString(), etPassword.text.toString())
        }
    }


    private fun login(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                goToMain()
            }

            .addOnFailureListener {
                toast("Error: ${it.message}")
            }
    }

    private fun register(email: String, pass: String) {
        if (email.isBlank() || pass.length < 6) {
            toast("Email inválido o contraseña muy corta")
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                val uid = it.user?.uid ?: return@addOnSuccessListener

                val user = User(uid, email, 0, 0.0)

                db.collection("users")
                    .document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        toast("Cuenta creada correctamente 🎉")
                        goToMain()
                    }
                    .addOnFailureListener { e ->
                        toast("Error guardando usuario: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                toast(
                    when {
                        e.message?.contains("email address is already in use") == true ->
                            "Este email ya está registrado"
                        else ->
                            "Error creando cuenta: ${e.message}"
                    }
                )
            }
    }



    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
