package com.example.driveup.navigation

import android.location.Location
import kotlin.math.roundToInt

class SpeedManager {

    private val speedBuffer = ArrayDeque<Float>()
    private val BUFFER_SIZE = 5

    private var lastSpeedKmh = 0

    /**
     * Actualiza la velocidad con una nueva localización
     * @return velocidad en km/h (entero, suavizado)
     */
    fun update(location: Location): Int {

        if (!location.hasSpeed()) {
            return lastSpeedKmh
        }

        val speedKmh = location.speed * 3.6f

        // Ignorar ruido cuando estamos prácticamente parados
        if (speedKmh < 1f) {
            speedBuffer.clear()
            lastSpeedKmh = 0
            return 0
        }

        // Guardar en buffer
        speedBuffer.addLast(speedKmh)

        if (speedBuffer.size > BUFFER_SIZE) {
            speedBuffer.removeFirst()
        }

        // Media móvil
        val avg = speedBuffer.average().toFloat()
        lastSpeedKmh = avg.roundToInt()

        return lastSpeedKmh
    }

    /**
     * Velocidad actual sin actualizar
     */
    fun getCurrentSpeed(): Int {
        return lastSpeedKmh
    }

    /**
     * Reset completo (por ejemplo al cerrar navegación)
     */
    fun reset() {
        speedBuffer.clear()
        lastSpeedKmh = 0
    }
}
