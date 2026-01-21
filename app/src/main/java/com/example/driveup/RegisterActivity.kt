package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.driveup.Models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etAge = findViewById<EditText>(R.id.etAge)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // Spinner género
        spinnerGender.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Hombre", "Mujer", "Otro")
        )

        btnRegister.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val ageText = etAge.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()

            // ================= VALIDACIONES =================

            if (email.isEmpty() || password.isEmpty() || username.isEmpty()
                || phone.isEmpty() || ageText.isEmpty()
            ) {
                toast("Rellena todos los campos")
                return@setOnClickListener
            }

            // 📧 Email solo @gmail.com
            if (!email.matches(Regex("^[A-Za-z0-9._%+-]+@gmail\\.com$"))) {
                toast("El email debe ser @gmail.com")
                return@setOnClickListener
            }

            // 🔐 Contraseña mínima
            if (password.length < 6) {
                toast("La contraseña debe tener mínimo 6 caracteres")
                return@setOnClickListener
            }

            // 📱 Teléfono 9 números
            if (!phone.matches(Regex("^[0-9]{9}$"))) {
                toast("El teléfono debe tener 9 números")
                return@setOnClickListener
            }

            // 🎂 Edad mínima
            val age = ageText.toIntOrNull()
            if (age == null || age < 18) {
                toast("Debes ser mayor de edad")
                return@setOnClickListener
            }

            // 🔍 NUEVO: comprobar duplicidad de teléfono
            checkPhoneAndRegister(
                email,
                password,
                username,
                phone,
                age,
                gender
            )
        }
    }

    // ================= COMPROBAR TELÉFONO =================

    private fun checkPhoneAndRegister(
        email: String,
        password: String,
        username: String,
        phone: String,
        age: Int,
        gender: String
    ) {
        db.collection("users")
            .whereEqualTo("phone", phone)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.isEmpty) {
                    toast("Este número de teléfono ya está registrado")
                    return@addOnSuccessListener
                }

                // Teléfono libre → crear cuenta
                createAccount(email, password, username, phone, age, gender)
            }
            .addOnFailureListener {
                toast("Error comprobando el teléfono")
            }
    }

    // ================= FIREBASE =================

    private fun createAccount(
        email: String,
        password: String,
        username: String,
        phone: String,
        age: Int,
        gender: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->

                val uid = result.user?.uid ?: return@addOnSuccessListener

                val user = User(
                    uid = uid,
                    email = email,
                    username = username,
                    phone = phone,
                    age = age,
                    gender = gender,
                    points = 0,
                    totalKm = 0.0
                )

                db.collection("users")
                    .document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        toast("Cuenta creada correctamente")
                        goToMain()
                    }
                    .addOnFailureListener { e ->
                        toast("Error guardando datos: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                toast(e.message ?: "Error creando la cuenta")
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
