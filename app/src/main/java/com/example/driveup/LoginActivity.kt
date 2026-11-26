package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var etUser: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                checkUserInFirestore(currentUser.uid)
            } else {
                auth.signOut()
                toast("Verifica tu email antes de entrar")
            }
            return
        }

        setContentView(R.layout.activity_login)

        etUser = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // ===== ENTER EN USERNAME → PASA A PASSWORD =====
        etUser.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPassword.requestFocus()
                true
            } else {
                false
            }
        }

        // ===== ENTER EN PASSWORD → LOGIN =====
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                doLogin()
                true
            } else {
                false
            }
        }

        btnLogin.setOnClickListener {
            hideKeyboard()
            doLogin()
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            showResetPassword()
        }
    }

    // ===== LÓGICA DE LOGIN =====
    private fun doLogin() {
        val input = etUser.text.toString().trim()
        val pass = etPassword.text.toString().trim()

        if (input.isEmpty() || pass.isEmpty()) {
            toast("Introduce usuario/email y contraseña")
            return
        }

        login(input, pass)
    }

    private fun login(input: String, pass: String) {

        if (input.contains("@")) {
            auth.signInWithEmailAndPassword(input, pass)
                .addOnSuccessListener { handlePostLogin() }
                .addOnFailureListener { e -> toast(loginErrorMessage(e)) }
            return
        }

        db.collection("users")
            .whereEqualTo("username", input)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    toast("Usuario no encontrado")
                    return@addOnSuccessListener
                }

                val email = snap.documents[0].getString("email") ?: return@addOnSuccessListener

                auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { handlePostLogin() }
                    .addOnFailureListener { e -> toast(loginErrorMessage(e)) }
            }
            .addOnFailureListener {
                toast("Error al iniciar sesión")
            }
    }

    /** Traduce el error de Firebase a un mensaje claro en vez de "Credenciales incorrectas" para todo. */
    private fun loginErrorMessage(e: Exception): String = when (e) {
        is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") "Esta cuenta está desactivada"
            else "No existe ninguna cuenta con ese correo"
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
            "Correo o contraseña incorrectos"
        is com.google.firebase.FirebaseTooManyRequestsException ->
            "Demasiados intentos. Espera unos minutos o restablece la contraseña"
        is com.google.firebase.FirebaseNetworkException ->
            "Sin conexión a internet"
        else -> "No se pudo iniciar sesión: ${e.localizedMessage ?: "error desconocido"}"
    }

    /** Envía un correo para restablecer la contraseña. */
    private fun showResetPassword() {
        val prefill = etUser.text.toString().trim().takeIf { it.contains("@") } ?: ""
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = "Tu correo"
            setText(prefill)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val box = FrameLayout(this).apply {
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
            addView(input)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Restablecer contraseña")
            .setMessage("Te enviaremos un correo para crear una contraseña nueva.")
            .setView(box)
            .setPositiveButton("Enviar") { _, _ ->
                val email = input.text.toString().trim()
                if (!email.contains("@")) { toast("Escribe un correo válido"); return@setPositiveButton }
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener { toast("Correo enviado. Revisa tu bandeja (y spam)") }
                    .addOnFailureListener { e -> toast(loginErrorMessage(e)) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun handlePostLogin() {
        val user = auth.currentUser ?: return

        if (!user.isEmailVerified) {
            toast("Verifica tu email antes de entrar")
            auth.signOut()
            return
        }

        checkUserInFirestore(user.uid)
    }

    private fun checkUserInFirestore(uid: String) {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    goToMain()
                } else {
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

    // ===== CERRAR TECLADO =====
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
