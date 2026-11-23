package com.example.driveup

import android.content.Context

/** Direcciones guardadas de Casa y Trabajo (se quedan en el móvil). */
object SavedPlaces {
    private const val PREFS = "driveup_prefs"

    enum class Place(val key: String, val label: String) {
        HOME("place_home", "Casa"),
        WORK("place_work", "Trabajo")
    }

    fun get(context: Context, place: Place): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(place.key, null)
            ?.takeIf { it.isNotBlank() }

    fun set(context: Context, place: Place, address: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (address.isNullOrBlank()) remove(place.key) else putString(place.key, address.trim())
        }.apply()
    }
}
