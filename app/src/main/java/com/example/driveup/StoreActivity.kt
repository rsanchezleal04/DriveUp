package com.example.driveup

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.models.User
import com.example.driveup.store.StoreAdapter
import com.example.driveup.store.StoreItem
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StoreActivity : AppCompatActivity() {

    private lateinit var tvPoints: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var chipCategories: ChipGroup
    private lateinit var btnBack: View

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var userPoints = 0
    private val userCodes = mutableMapOf<String, String>()

    private lateinit var adapter: StoreAdapter

    private val allItems = mutableListOf<StoreItem>()
    private var sortedItems: List<StoreItem> = emptyList()
    private var selectedCategory = "Todas"

    // ===== CATEGORÍAS FILTRO =====
    private val filterCategories = listOf(
        "Todas",
        "Ropa", "Supermercados", "Gasolina", "Viajes", "Tecnología",
        "Maquillaje y cosmeticos", "Ocio", "Coches", "Gimnasio",
        "Ornamento", "Restaurante", "Libros", "Amazón", "Farmacia",
        "Talleres", "Cerveza", "Higiene Personal",
        "Compañia de telefono", "Vinilos", "Transporte"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        tvPoints = findViewById(R.id.tvPointsValue)
        tvEmpty = findViewById(R.id.tvStoreEmpty)
        recyclerView = findViewById(R.id.storeRecyclerView)
        chipCategories = findViewById(R.id.chipCategories)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = StoreAdapter(this, mutableListOf(), userCodes) {
            userPoints = it
            tvPoints.text = "$userPoints pts"
        }
        recyclerView.adapter = adapter

        setupCategoryChips()
        loadUserData()
    }

    private fun setupCategoryChips() {
        filterCategories.forEachIndexed { index, name ->
            // Inflar desde XML para que el estilo deje la pestaña pulsable y marcable
            val chip = layoutInflater.inflate(R.layout.item_category_chip, chipCategories, false) as Chip
            chip.id = View.generateViewId()
            chip.text = name
            chipCategories.addView(chip)
            if (index == 0) chipCategories.check(chip.id)
        }

        chipCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            selectedCategory = chip.text.toString()
            applyFilter()
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
            val user = snapshot.toObject(User::class.java)

            userPoints = user?.points ?: 0
            adapter.userPoints = userPoints
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

                sortedItems = prioritySorted.sortedByDescending {
                    userCategories[it.category] ?: 0
                }

                applyFilter()
            }
    }

    private fun applyFilter() {
        val items = if (selectedCategory == "Todas") sortedItems
                    else sortedItems.filter { it.category == selectedCategory }
        adapter.updateItems(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
