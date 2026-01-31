package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
//import com.example.driveup.Models.User
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
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etAge = findViewById<EditText>(R.id.etAge)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        spinnerGender.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Hombre", "Mujer", "Otro")
        )

        btnRegister.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val ageText = etAge.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()
                || username.isEmpty() || phone.isEmpty() || ageText.isEmpty()
            ) {
                toast("Rellena todos los campos")
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                toast("Las contraseñas no coinciden")
                return@setOnClickListener
            }

            if (!email.matches(Regex("^[A-Za-z0-9._%+-]+@gmail\\.com$"))) {
                toast("El email debe ser @gmail.com")
                return@setOnClickListener
            }

            if (password.length < 6) {
                toast("La contraseña debe tener mínimo 6 caracteres")
                return@setOnClickListener
            }

            if (!phone.matches(Regex("^[0-9]{9}$"))) {
                toast("El teléfono debe tener 9 números")
                return@setOnClickListener
            }

            val age = ageText.toIntOrNull()
            if (age == null || age < 18) {
                toast("Debes ser mayor de edad")
                return@setOnClickListener
            }

            createAccount(email, password, username, phone, age, gender)
        }
    }

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

                val firebaseUser = result.user ?: return@addOnSuccessListener
                val uid = firebaseUser.uid

                val phoneRef = db.collection("phone_index").document(phone)
                val userRef = db.collection("users").document(uid)

                db.runTransaction { tx ->

                    val phoneSnap = tx.get(phoneRef)
                    if (phoneSnap.exists()) {
                        throw Exception("TEL_DUPLICADO")
                    }

                    tx.set(phoneRef, mapOf("uid" to uid))

                    val user = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "username" to username,
                        "phone" to phone,
                        "age" to age,
                        "gender" to gender,
                        "points" to 0,
                        "totalKm" to 0.0,
                        "categories" to emptyMap<String, Long>(),
                        "codes" to emptyMap<String, String>(),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    tx.set(userRef, user)
                }
                    .addOnSuccessListener {
                        firebaseUser.sendEmailVerification()
                        toast("Cuenta creada. Revisa tu email")
                        startActivity(Intent(this, VerifyEmailActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        firebaseUser.delete()
                        if (e.message == "TEL_DUPLICADO") {
                            toast("Este teléfono ya está registrado")
                        } else {
                            toast(e.message ?: "Error creando usuario")
                        }
                    }
            }
            .addOnFailureListener {
                toast(it.localizedMessage ?: "Error creando cuenta")
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
