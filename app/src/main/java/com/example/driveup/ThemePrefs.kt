package com.example.driveup

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Guarda y aplica el modo claro/oscuro elegido por el usuario. */
object ThemePrefs {
    private const val PREFS = "driveup_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    fun apply(context: Context) {
        val mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Cambia al modo contrario del que se está viendo ahora mismo y lo recuerda. */
    fun toggle(context: Context) {
        val mode = if (isNight(context)) AppCompatDelegate.MODE_NIGHT_NO
                   else AppCompatDelegate.MODE_NIGHT_YES
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NIGHT_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

class DriveUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.apply(this)
    }
}
