package com.example.driveup.navigation

import android.location.Location
import android.os.Build
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class SpeedManager {

    private var lastSpeed = 0f
    private var lastTime = 0L
    private var smoothedSpeed = 0f
    private var lastValidSpeedTime = 0L

    private val MAX_JUMP = 35f          // km/h máximo salto permitido por update
    private val STOP_THRESHOLD = 4f     // km/h: por debajo, se considera parado (ruido del GPS)
    private val MAX_ACCURACY_M = 25f    // con peor precisión (p. ej. en interiores) la velocidad no es fiable
    private val STALE_MS = 3000L        // sin velocidad válida durante 3 s → 0

    fun update(location: Location): Int {

        val now = location.time

        if (!location.hasSpeed() || !isReliable(location)) {
            // Sin dato fiable: no dejar congelada la última velocidad
            if (now - lastValidSpeedTime > STALE_MS) stop(now)
            return smoothedSpeed.roundToInt()
        }

        val rawSpeed = location.speed * 3.6f

        // El GPS da algo de velocidad aunque estés quieto: ignorarla si está
        // dentro de su propio margen de error o por debajo del umbral
        val speedError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            location.hasSpeedAccuracy()
        ) location.speedAccuracyMetersPerSecond * 3.6f else 0f

        if (rawSpeed < STOP_THRESHOLD || rawSpeed <= speedError) {
            stop(now)
            lastValidSpeedTime = now
            return 0
        }

        lastValidSpeedTime = now

        if (lastTime == 0L || smoothedSpeed == 0f) {
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

    /** true si la posición es lo bastante precisa como para fiarse de su velocidad. */
    fun isReliable(location: Location): Boolean =
        !location.hasAccuracy() || location.accuracy <= MAX_ACCURACY_M

    private fun stop(now: Long) {
        smoothedSpeed = 0f
        lastSpeed = 0f
        lastTime = now
    }

    fun getCurrentSpeed(): Int {
        return smoothedSpeed.roundToInt()
    }

    fun reset() {
        smoothedSpeed = 0f
        lastSpeed = 0f
        lastTime = 0L
        lastValidSpeedTime = 0L
    }
}
