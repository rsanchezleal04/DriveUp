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
    val items: List<StoreItem>,
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
        holder.btnBuy.isEnabled = true

        holder.btnBuy.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val userRef = db.collection("users").document(uid)

            // ================= VER CÓDIGO =================
            if (item.purchased) {
                val code = codesMap[item.name] ?: "No disponible"
                android.app.AlertDialog.Builder(context)
                    .setTitle("Código de ${item.name}")
                    .setMessage("Tu código: $code")
                    .setPositiveButton("Aceptar") { d, _ -> d.dismiss() }
                    .show()
                return@setOnClickListener
            }

            // ================= CONFIRMACIÓN DE COMPRA =================
            android.app.AlertDialog.Builder(context)
                .setTitle("Confirmar compra")
                .setMessage(
                    "${item.description}\n\n" +
                            "¿Quieres gastar ${item.price} pts?"
                )
                .setPositiveButton("Sí") { dialog, _ ->

                    db.runTransaction { transaction ->

                        val snapshot = transaction.get(userRef)

                        val currentPoints =
                            snapshot.getLong("points")?.toInt() ?: 0

                        val purchasedItems =
                            snapshot.get("purchasedItems") as? MutableList<String>
                                ?: mutableListOf()

                        val categories =
                            snapshot.get("categories") as? MutableMap<String, Long>
                                ?: mutableMapOf()

                        if (currentPoints < item.price) {
                            throw Exception("No tienes suficientes puntos")
                        }

                        // ================= ACTUALIZACIONES =================
                        val newPoints = currentPoints - item.price
                        transaction.update(userRef, "points", newPoints)

                        purchasedItems.add(item.name)
                        transaction.update(userRef, "purchasedItems", purchasedItems)

                        // ---- Categorías (contador) ----
                        val currentCount = categories[item.category] ?: 0
                        categories[item.category] = currentCount + 1
                        transaction.update(userRef, "categories", categories)

                        // ---- Código ----
                        val code = generateCouponCode()
                        codesMap[item.name] = code
                        transaction.update(userRef, "codes", codesMap)

                        item.purchased = true
                        newPoints
                    }
                        .addOnSuccessListener { newPoints ->
                            notifyItemChanged(position)
                            onPointsUpdated(newPoints)

                            val code = codesMap[item.name] ?: "No disponible"
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Compra realizada")
                                .setMessage(
                                    "Has comprado '${item.name}'\n\n"  +
                                            "Tu código: $code"
                                )
                                .setPositiveButton("Aceptar") { d, _ -> d.dismiss() }
                                .show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                context,
                                e.message ?: "Error al comprar",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    dialog.dismiss()
                }
                .setNegativeButton("No") { d, _ -> d.dismiss() }
                .show()
        }
    }

    // ================= CÓDIGO CUPÓN =================
    private fun generateCouponCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..4).joinToString("-") {
            (1..4).map { chars.random() }.joinToString("")
        }
    }
}