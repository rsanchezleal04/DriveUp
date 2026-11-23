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

    /** Puntos actuales del usuario (los pone StoreActivity) para avisar antes de comprar. */
    var userPoints: Int = 0

    // Color para productos comprados
    private val purchasedColor = Color.parseColor("#3D8BFD")
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
        // backgroundTintList (no setBackgroundColor) para conservar las esquinas redondeadas
        holder.btnBuy.backgroundTintList = android.content.res.ColorStateList.valueOf(
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
                com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Código de ${item.name}")
                    .setMessage("Tu código: $code")
                    .setPositiveButton("Aceptar", null)
                    .show()
                return@setOnClickListener
            }

            if (userPoints < item.price) {
                showNotEnoughPoints(item, userPoints)
                return@setOnClickListener
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Confirmar compra")
                .setMessage("${item.description}\n\n¿Gastar ${item.price} pts?")
                .setPositiveButton("Sí") { _, _ ->
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(userRef)
                        val currentPoints = snapshot.getLong("points")?.toInt() ?: 0

                        if (currentPoints < item.price) {
                            throw NotEnoughPointsException(currentPoints)
                        }

                        val newPoints = currentPoints - item.price
                        transaction.update(userRef, "points", newPoints)

                        codesMap[item.name] = item.code
                        transaction.update(userRef, "codes", codesMap)

                        item.purchased = true
                        newPoints
                    }
                        .addOnSuccessListener {
                            userPoints = it
                            notifyItemChanged(position)
                            onPointsUpdated(it)
                        }
                        .addOnFailureListener { e ->
                            // Firestore envuelve la excepción: buscar la nuestra en la causa
                            var cause: Throwable? = e
                            while (cause != null && cause !is NotEnoughPointsException) cause = cause.cause
                            if (cause is NotEnoughPointsException) {
                                userPoints = cause.currentPoints
                                onPointsUpdated(cause.currentPoints)
                                showNotEnoughPoints(item, cause.currentPoints)
                            } else {
                                Toast.makeText(context, "No se pudo completar la compra", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }


    private class NotEnoughPointsException(val currentPoints: Int) : Exception("No tienes suficientes puntos")

    /** Ventana bonita de "te faltan X puntos" con barra de progreso. */
    private fun showNotEnoughPoints(item: StoreItem, have: Int) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_not_enough_points, null)
        val fmt = java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "ES"))
        val missing = (item.price - have).coerceAtLeast(0)

        view.findViewById<TextView>(R.id.tvNepMessage).text =
            "Te faltan ${fmt.format(missing)} pts para\n${item.name}"
        view.findViewById<TextView>(R.id.tvNepHave).text = "Tienes ${fmt.format(have)} pts"
        view.findViewById<TextView>(R.id.tvNepNeed).text = "${fmt.format(item.price)} pts"

        val percent = if (item.price > 0) (have * 100L / item.price).toInt().coerceIn(0, 100) else 100
        view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressNep)
            .setProgressCompat(maxOf(percent, 2), true)   // un poquito siempre visible

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()
        view.findViewById<Button>(R.id.btnNepOk).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** Permite a la Activity cambiar la lista (orden / filtro) */
    fun updateItems(newItems: List<StoreItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
