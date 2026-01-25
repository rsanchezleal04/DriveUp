package com.example.driveup

import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.Models.User
import com.example.driveup.store.StoreAdapter
import com.example.driveup.store.StoreItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StoreActivity : AppCompatActivity() {

    private lateinit var tvPoints: TextView        // TextView donde mostramos los puntos
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnBack: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var userPoints: Int = 0
    private lateinit var adapter: StoreAdapter
    private var userCodes: MutableMap<String, String> = mutableMapOf() // Códigos de productos en memoria

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        // Referencias a elementos del layout
        tvPoints = findViewById(R.id.tvPointsValue)
        recyclerView = findViewById(R.id.storeRecyclerView)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() } // Botón volver

        // RecyclerView vertical
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Lista de productos
        val items = listOf(
            StoreItem("Carrefour 20%", "20% en carrefour. Hasta 100 euros", 15000, "Supermercado"),
            StoreItem("McFlurry Gratis", "Un mcflurry gratis", 2000, "ComidaRapida"),
            StoreItem("Descuento Repsol 10€", "10 euros de descuento en repsol", 20000, "Gasolina"),
            StoreItem("Chandal Nike", "Chandal Nike Tech gratis", 70000, "Ropa"),
            StoreItem("Entradas cinesa", "2 entradas gratis para cualquier cine Cinesa", 100000, "Ocio"),
            StoreItem("Amazon Prime", "1 mes de Amazon Prime", 4500, "Ocio"),
            StoreItem("Spotify Premium", "3 meses de Spotify Premium", 17000, "Ocio"),
            StoreItem("Uber 10€", "Cupón Uber de 10€", 28000, "Transporte")
        )

        // Adapter del RecyclerView
        adapter = StoreAdapter(this, items, userCodes) { newPoints ->
            // Callback para actualizar puntos en pantalla
            userPoints = newPoints
            tvPoints.text = "$userPoints pts"
        }
        recyclerView.adapter = adapter

        // Cargar puntos y productos comprados del usuario
        loadUserData()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                // Obtenemos el objeto User desde Firestore
                val user = snapshot.toObject(User::class.java)

                // Actualizamos puntos en pantalla
                userPoints = user?.points ?: 0
                tvPoints.text = "$userPoints pts"

                // Guardamos códigos en memoria
                userCodes.clear()
                user?.codes?.forEach { (k, v) -> userCodes[k] = v }

                // Lista de productos ya comprados
                val purchasedList = user?.purchasedItems ?: emptyList()

                // Marcamos los productos como comprados en el adapter
                adapter.items.forEach { item ->
                    if (purchasedList.contains(item.name)) item.purchased = true
                }

                // Refrescamos el RecyclerView
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                tvPoints.text = "0 pts"
            }
    }
}
