package com.example.driveup

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.Models.User
import com.example.driveup.store.StoreAdapter
import com.example.driveup.store.StoreItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

    private val allItems = mutableListOf<StoreItem>()

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

            val userCategories = user?.categories ?: emptyMap()

            listenStoreItems(userCategories)
        }
    }

    private fun listenStoreItems(userCategories: Map<String, Long>) {
        db.collection("store_items")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                allItems.clear()

                for (doc in snapshots.documents) {
                    val item = doc.toObject(StoreItem::class.java) ?: continue

                    if (item.active) {
                        item.purchased = userCodes.containsKey(item.name)
                        allItems.add(item)
                    }
                }

                val prioritySorted = allItems.sortedByDescending { it.priority }

                val finalSorted = prioritySorted.sortedByDescending {
                    userCategories[it.category] ?: 0
                }

                adapter.updateItems(finalSorted)

                applySpinnerFilter(finalSorted)
            }
    }

    private fun applySpinnerFilter(sortedItems: List<StoreItem>) {
        spinnerCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    val selected = filterCategories[position]
                    if (selected == "Todas") {
                        adapter.updateItems(sortedItems)
                    } else {
                        adapter.updateItems(sortedItems.filter { it.category == selected })
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }
}
