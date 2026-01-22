/*package com.example.driveup

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.driveup.Models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class VerifyPhoneActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var btnVerify: Button

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var verificationId: String? = null

    private var email: String? = null
    private var password: String? = null
    private var username: String? = null
    private var phone: String? = null
    private var age: Int = 0
    private var gender: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify)

        etCode = findViewById(R.id.etCode)
        btnVerify = findViewById(R.id.btnVerify)

        auth = FirebaseAuth.getInstance()

        verificationId = intent.getStringExtra("verificationId")
        email = intent.getStringExtra("email")
        password = intent.getStringExtra("password")
        username = intent.getStringExtra("username")
        phone = intent.getStringExtra("phone")
        age = intent.getIntExtra("age", 0)
        gender = intent.getStringExtra("gender")

        btnVerify.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                toast("Introduce el código")
                return@setOnClickListener
            }

            verifyCode(code)
        }
    }

    private fun verifyCode(code: String) {
        val credential = verificationId?.let { PhoneAuthProvider.getCredential(it, code) }
        if (credential != null) {
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    toast("Teléfono verificado")
                    // Crear la cuenta después de verificar
                    createAccount(
                        email!!, password!!, username!!, phone!!, age, gender!!
                    )
                }
                .addOnFailureListener {
                    toast("Código incorrecto")
                }
        }
    }

    // ================= CREAR CUENTA (igual que RegisterActivity) =================
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
                        toast("Cuenta creada correctamente 🎉")
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

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
*/