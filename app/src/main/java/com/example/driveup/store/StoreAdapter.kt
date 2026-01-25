package com.example.driveup.store

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StoreAdapter(
    private val context: Context,
    val items: MutableList<StoreItem>,
    private val codesMap: MutableMap<String, String>,
    private val onPointsUpdated: (newPoints: Int) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    inner class StoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
        val btnBuy: Button = view.findViewById(R.id.btnBuy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name
        holder.tvPrice.text = "${item.price} pts"
        holder.btnBuy.text = if (item.purchased) "Ver código" else "Comprar"

        holder.btnBuy.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val userRef = db.collection("users").document(uid)

            // ===== VER CÓDIGO =====
            if (item.purchased) {
                val code = codesMap[item.name] ?: "No disponible"
                android.app.AlertDialog.Builder(context)
                    .setTitle("Código de ${item.name}")
                    .setMessage("Tu código: $code")
                    .setPositiveButton("Aceptar", null)
                    .show()
                return@setOnClickListener
            }

            // ===== CONFIRMAR COMPRA =====
            android.app.AlertDialog.Builder(context)
                .setTitle("Confirmar compra")
                .setMessage("${item.description}\n\n¿Quieres gastar ${item.price} pts?")
                .setPositiveButton("Sí") { _, _ ->

                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(userRef)

                        val currentPoints = snapshot.getLong("points")?.toInt() ?: 0
                        val purchasedItems =
                            snapshot.get("purchasedItems") as? MutableList<String> ?: mutableListOf()
                        val categories =
                            snapshot.get("categories") as? MutableMap<String, Long> ?: mutableMapOf()

                        if (currentPoints < item.price) {
                            throw Exception("No tienes suficientes puntos")
                        }

                        val newPoints = currentPoints - item.price
                        transaction.update(userRef, "points", newPoints)

                        purchasedItems.add(item.name)
                        transaction.update(userRef, "purchasedItems", purchasedItems)

                        categories[item.category] = (categories[item.category] ?: 0) + 1
                        transaction.update(userRef, "categories", categories)

                        // ✅ USAR CÓDIGO DE FIRESTORE
                        val code = item.code
                        codesMap[item.name] = code
                        transaction.update(userRef, "codes", codesMap)

                        item.purchased = true
                        newPoints
                    }
                        .addOnSuccessListener { newPoints ->
                            notifyItemChanged(position)
                            onPointsUpdated(newPoints)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    /** Permite a la Activity cambiar la lista (orden / filtro) */
    fun updateItems(newItems: List<StoreItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
