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

// Adapter para mostrar los productos de la tienda
class StoreAdapter(
    private val context: Context,
    val items: List<StoreItem>,              // Lista de productos
    private val codesMap: MutableMap<String, String>, // Códigos de productos en memoria
    private val onPointsUpdated: (newPoints: Int) -> Unit // Callback para actualizar puntos
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ViewHolder representa un item de producto
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

        // Mostramos nombre y precio
        holder.tvName.text = item.name
        holder.tvPrice.text = "${item.price} pts"

        // Botón según estado: Comprar o Ver código
        holder.btnBuy.text = if (item.purchased) "Ver código" else "Comprar"
        holder.btnBuy.isEnabled = true

        holder.btnBuy.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val userRef = db.collection("users").document(uid)

            if (item.purchased) {
                // Si ya comprado, mostramos código almacenado
                val code = codesMap[item.name] ?: "No disponible"
                android.app.AlertDialog.Builder(context)
                    .setTitle("Código de ${item.name}")
                    .setMessage("Tu código: $code")
                    .setPositiveButton("Aceptar") { dialog, _ -> dialog.dismiss() }
                    .show()
                return@setOnClickListener
            }

            // Si no comprado, mostramos diálogo de confirmación con descripción y precio
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("Confirmar compra")
            builder.setMessage("${item.description}\n\n¿Quieres gastar ${item.price} pts para comprar '${item.name}'?")
            builder.setPositiveButton("Sí") { dialog, _ ->

                // Transacción para restar puntos y marcar producto comprado
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentPoints = snapshot.getLong("points")?.toInt() ?: 0
                    val purchasedItems = snapshot.get("purchasedItems") as? MutableList<String> ?: mutableListOf()

                    if (currentPoints >= item.price) {
                        val newPoints = currentPoints - item.price
                        transaction.update(userRef, "points", newPoints)

                        purchasedItems.add(item.name)
                        transaction.update(userRef, "purchasedItems", purchasedItems)

                        // Generamos código único y guardamos en memoria y Firestore
                        val code = generateCouponCode()
                        codesMap[item.name] = code
                        transaction.update(userRef, "codes", codesMap)

                        item.purchased = true
                        newPoints
                    } else {
                        throw Exception("No tienes suficientes puntos")
                    }
                }.addOnSuccessListener { newPoints ->
                    // Actualizamos UI
                    notifyItemChanged(position)
                    onPointsUpdated(newPoints)

                    // Mostramos código generado
                    val code = codesMap[item.name] ?: "No disponible"
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Compra realizada")
                        .setMessage("Has comprado '${item.name}'\nTu código: $code")
                        .setPositiveButton("Aceptar") { dialog2, _ -> dialog2.dismiss() }
                        .show()
                }.addOnFailureListener { e ->
                    Toast.makeText(context, e.message ?: "Error al comprar", Toast.LENGTH_SHORT).show()
                }

                dialog.dismiss()
            }
            builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
    }

    // Genera un código tipo XXXX-XXXX-XXXX-XXXX
    private fun generateCouponCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..4).joinToString("-") { (1..4).map { chars.random() }.joinToString("") }
    }
}
