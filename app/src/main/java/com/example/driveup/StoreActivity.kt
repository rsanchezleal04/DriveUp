package com.example.driveup

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.Models.User
import com.example.driveup.store.StoreAdapter
import com.example.driveup.store.StoreItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View

class StoreActivity : AppCompatActivity() {

    private lateinit var tvPoints: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnBack: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var userPoints = 0
    private val userCodes = mutableMapOf<String, String>()

    private lateinit var adapter: StoreAdapter

    // ===== LISTA BASE =====
    private val allItems = mutableListOf(
        StoreItem("Carrefour 20%", "20% en carrefour. Hasta 100 euros", 15000, "Supermercados"),
        StoreItem("McFlurry Gratis", "Un mcflurry gratis", 2000, "Restaurante"),
        StoreItem("Descuento Repsol 10€", "10 euros de descuento en repsol", 20000, "Gasolina"),
        StoreItem("Chandal Nike", "Chandal Nike Tech gratis", 70000, "Ropa"),
        StoreItem("Entradas Cinesa", "2 entradas gratis", 100000, "Ocio"),
        StoreItem("Amazon Prime", "1 mes de Amazon Prime", 4500, "Amazón"),
        StoreItem("Spotify Premium", "3 meses de Spotify Premium", 17000, "Ocio"),
        StoreItem("Uber 10€", "Cupón Uber de 10€", 28000, "Transporte")
    )

    // ===== CATEGORÍAS FILTRO =====
    private val filterCategories = listOf(
        "Todas",
        "Ropa", "Supermercados", "Gasolina", "Viajes", "Tcnología",
        "Maquillaje y cosmeticos", "Ocio", "Coches", "Gimnasio",
        "Ornamento", "Restaurante", "Libros", "Amazón", "Farmacia",
        "Talleres", "Cerveza", "HigienePersonal",
        "Compañia de telefono", "Vinilos", "Transporte"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        tvPoints = findViewById(R.id.tvPointsValue)
        recyclerView = findViewById(R.id.storeRecyclerView)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StoreAdapter(this, mutableListOf(), userCodes) {
            userPoints = it
            tvPoints.text = "$userPoints pts"
        }
        recyclerView.adapter = adapter

        spinnerCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filterCategories
        )

        loadUserData()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
            val user = snapshot.toObject(User::class.java)

            userPoints = user?.points ?: 0
            tvPoints.text = "$userPoints pts"

            userCodes.clear()
            user?.codes?.forEach { (k, v) -> userCodes[k] = v }

            val purchased = user?.purchasedItems ?: emptyList()
            allItems.forEach { it.purchased = purchased.contains(it.name) }

            val userCategories = user?.categories ?: emptyMap()

            // ORDEN POR CATEGORÍAS MÁS COMPRADAS
            val sortedItems = allItems.sortedByDescending {
                userCategories[it.category] ?: 0
            }

            adapter.updateItems(sortedItems)

            spinnerCategory.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>, view: View?, position: Int, id: Long
                    ) {
                        val selected = filterCategories[position]
                        if (selected == "Todas") {
                            adapter.updateItems(sortedItems)
                        } else {
                            adapter.updateItems(
                                sortedItems.filter { it.category == selected }
                            )
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
        }
    }
}