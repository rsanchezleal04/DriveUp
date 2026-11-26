package com.example.driveup

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

/** Una sugerencia de dirección. toString() es lo que se escribe en el campo al elegirla. */
data class PlaceSuggestion(val title: String, val subtitle: String, val formatted: String) {
    override fun toString() = formatted
}

/**
 * Adaptador del autocompletado: dos líneas (nombre + resto de la dirección) con icono.
 * No filtra por su cuenta: muestra tal cual lo que devuelve el servidor
 * (ArrayAdapter filtraba por "empieza por" y escondía resultados válidos).
 */
class PlaceSuggestionAdapter(
    private val context: Context,
    private val items: List<PlaceSuggestion>
) : BaseAdapter(), Filterable {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_place_suggestion, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.tvSuggestionTitle).text = item.title
        view.findViewById<TextView>(R.id.tvSuggestionSubtitle).apply {
            text = item.subtitle
            visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
        }
        return view
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) =
            FilterResults().apply { values = items; count = items.size }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?) = resultValue?.toString() ?: ""
    }
}
