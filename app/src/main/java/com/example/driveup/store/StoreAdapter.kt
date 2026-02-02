package com.example.driveup.store

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.driveup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import android.widget.ImageView



class StoreAdapter(
    private val context: Context,
    val items: MutableList<StoreItem>,
    private val codesMap: MutableMap<String, String>,
    private val onPointsUpdated: (newPoints: Int) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Color para productos comprados
    private val purchasedColor = Color.parseColor("#73C2FB")
    // Color para productos no comprados: color definido en colors.xml
    private val normalColor = ContextCompat.getColor(context, R.color.driveup_orange)

    inner class StoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgItem: ImageView = view.findViewById(R.id.imgItem)
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvDescription: TextView = view.findViewById(R.id.tvItemDescription)
        val btnBuy: Button = view.findViewById(R.id.btnBuy)
        val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name
        holder.tvDescription.text = item.description
        holder.btnBuy.text = if (item.purchased) "Ver código" else "Comprar"
        holder.btnBuy.setBackgroundColor(
            if (item.purchased) purchasedColor else normalColor
        )
        holder.tvPrice.text = "${item.price} pts"


        //IMAGEN DESDE FIRESTORE
        Glide.with(context)
            .load(item.imageUrl)
            .placeholder(R.drawable.ic_placeholder_image)
            .into(holder.imgItem)

        holder.btnBuy.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val userRef = db.collection("users").document(uid)

            if (item.purchased) {
                val code = codesMap[item.name] ?: "No disponible"
                android.app.AlertDialog.Builder(context)
                    .setTitle("Código de ${item.name}")
                    .setMessage("Tu código: $code")
                    .setPositiveButton("Aceptar", null)
                    .show()
                return@setOnClickListener
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Confirmar compra")
                .setMessage("${item.description}\n\n¿Gastar ${item.price} pts?")
                .setPositiveButton("Sí") { _, _ ->
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(userRef)
                        val currentPoints = snapshot.getLong("points")?.toInt() ?: 0

                        if (currentPoints < item.price) {
                            throw Exception("No tienes suficientes puntos")
                        }

                        val newPoints = currentPoints - item.price
                        transaction.update(userRef, "points", newPoints)

                        codesMap[item.name] = item.code
                        transaction.update(userRef, "codes", codesMap)

                        item.purchased = true
                        newPoints
                    }
                        .addOnSuccessListener {
                            notifyItemChanged(position)
                            onPointsUpdated(it)
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
