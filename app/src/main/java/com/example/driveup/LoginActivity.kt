package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 🔥 Si ya hay sesión activa → Main
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserInFirestore(currentUser.uid)
            return
        }


        setContentView(R.layout.activity_login)

        val etUser = findViewById<EditText>(R.id.etEmail) // email o username
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val input = etUser.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (input.isEmpty() || pass.isEmpty()) {
                toast("Introduce usuario/email y contraseña")
                return@setOnClickListener
            }

            login(input, pass)
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // 🔐 LOGIN CON EMAIL O USERNAME
    private fun login(input: String, pass: String) {

        // Caso 1 → email
        if (input.contains("@")) {
            auth.signInWithEmailAndPassword(input, pass)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener {
                    toast("Credenciales incorrectas")
                }
            return
        }

        // Caso 2 → username
        db.collection("users")
            .whereEqualTo("username", input)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    toast("Usuario no encontrado")
                    return@addOnSuccessListener
                }

                val email = snap.documents[0].getString("email")
                if (email == null) {
                    toast("Error interno")
                    return@addOnSuccessListener
                }

                auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { goToMain() }
                    .addOnFailureListener {
                        toast("Credenciales incorrectas")
                    }
            }
            .addOnFailureListener {
                toast("Error al iniciar sesión")
            }
    }

    private fun checkUserInFirestore(uid: String) {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    goToMain()
                } else {
                    // 🔥 Usuario Auth existe pero Firestore NO → sesión inválida
                    auth.signOut()
                    toast("Tu cuenta ya no existe. Regístrate de nuevo.")
                }
            }
            .addOnFailureListener {
                auth.signOut()
                toast("Error verificando usuario")
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
