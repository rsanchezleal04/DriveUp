package com.example.driveup.navigation

import android.location.Location
import kotlin.math.roundToInt
import kotlin.math.abs

class SpeedManager {

    private var lastSpeedKmh = 0f
    private var smoothedSpeed = 0f

    // Factor de suavizado
    // Más alto = más reactivo
    private val ALPHA_FAST = 0.6f
    private val ALPHA_SLOW = 0.2f

    fun update(location: Location): Int {

        if (!location.hasSpeed()) {
            return lastSpeedKmh.roundToInt()
        }

        val rawSpeed = location.speed * 3.6f

        // Ruido cuando estamos casi parados
        if (rawSpeed < 0.8f) {
            smoothedSpeed = 0f
            lastSpeedKmh = 0f
            return 0
        }

        val delta = abs(rawSpeed - smoothedSpeed)

        // Si hay cambio fuerte → reacción rápida
        val alpha = if (delta > 8f) ALPHA_FAST else ALPHA_SLOW

        smoothedSpeed =
            alpha * rawSpeed + (1f - alpha) * smoothedSpeed

        lastSpeedKmh = smoothedSpeed

        return smoothedSpeed.roundToInt()
    }

    fun getCurrentSpeed(): Int {
        return lastSpeedKmh.roundToInt()
    }

    fun reset() {
        smoothedSpeed = 0f
        lastSpeedKmh = 0f
    }
}
