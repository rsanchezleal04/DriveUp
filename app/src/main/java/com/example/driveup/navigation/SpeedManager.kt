package com.example.driveup.navigation

import android.location.Location
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class SpeedManager {

    private var lastSpeed = 0f
    private var lastTime = 0L
    private var smoothedSpeed = 0f

    private val MAX_JUMP = 35f      // km/h máximo salto permitido por update
    private val STOP_THRESHOLD = 0.7f

    fun update(location: Location): Int {

        if (!location.hasSpeed()) {
            return smoothedSpeed.roundToInt()
        }

        val now = location.time
        val rawSpeed = location.speed * 3.6f

        if (rawSpeed < STOP_THRESHOLD) {
            smoothedSpeed = 0f
            lastSpeed = 0f
            lastTime = now
            return 0
        }

        if (lastTime == 0L) {
            lastTime = now
            smoothedSpeed = rawSpeed
            lastSpeed = rawSpeed
            return rawSpeed.roundToInt()
        }

        val dt = ((now - lastTime).coerceAtLeast(1)) / 1000f
        lastTime = now

        val accel = (rawSpeed - lastSpeed) / dt
        lastSpeed = rawSpeed

        // Elegir alpha dinámico
        val alpha = when {
            abs(accel) > 25f -> 0.75f   // acelerón o frenazo
            abs(accel) > 12f -> 0.55f
            else -> 0.25f              // estable
        }

        var filtered = alpha * rawSpeed + (1f - alpha) * smoothedSpeed

        // Limitar saltos GPS absurdos
        val diff = filtered - smoothedSpeed
        if (abs(diff) > MAX_JUMP) {
            filtered = smoothedSpeed + MAX_JUMP * sign(diff)
        }

        smoothedSpeed = filtered

        return smoothedSpeed.roundToInt()
    }

    fun getCurrentSpeed(): Int {
        return smoothedSpeed.roundToInt()
    }

    fun reset() {
        smoothedSpeed = 0f
        lastSpeed = 0f
        lastTime = 0L
    }
}
