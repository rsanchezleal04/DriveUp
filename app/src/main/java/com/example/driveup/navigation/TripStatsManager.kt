package com.example.driveup.navigation

import android.location.Location
import kotlin.math.max
import kotlin.math.roundToInt

class TripStatsManager {

    // ================= CONFIG =================

    private val POINTS_PER_KM = 10.0
    private val MIN_VALID_DISTANCE = 100.0 // metros

    private val YELLOW_PENALTY = 0.15
    private val RED_PENALTY = 0.45
    private val EXTREME_PENALTY = 0.90


    // ================= TOTALES =================

    private var totalDistanceMeters = 0.0
    private var totalDrivingSeconds = 0.0


    // ================= TIEMPOS POR ZONA =================

    private var greenSeconds = 0.0
    private var yellowSeconds = 0.0
    private var redSeconds = 0.0
    private var extremeSeconds = 0.0


    // ================= TRACKING =================

    private var lastLocation: Location? = null
    private var lastUpdateTime = 0L

    private var tripActive = false


    // ===================================================
    // INICIAR VIAJE
    // ===================================================

    fun startTrip() {

        resetAll()
        tripActive = true
    }


    // ===================================================
    // UPDATE PRINCIPAL
    // ===================================================

    fun onLocationUpdate(
        location: Location,
        speedKmh: Int,
        speedLimit: Int
    ) {

        if (!tripActive) return

        val now = System.currentTimeMillis()


        // Primera lectura
        if (lastUpdateTime == 0L) {

            lastUpdateTime = now
            lastLocation = location
            return
        }


        val dt = (now - lastUpdateTime) / 1000.0
        lastUpdateTime = now

        if (dt <= 0) return


        // ================= IGNORAR SI CASI PARADO =================

        if (speedKmh < 3) {

            lastLocation = location
            lastUpdateTime = now
            return
        }


        // ================= DISTANCIA =================

        lastLocation?.let { last ->

            val delta = last.distanceTo(location)

            if (delta in 0.5..150.0) {
                totalDistanceMeters += delta
            }
        }

        lastLocation = location


        // ================= TIEMPO =================

        totalDrivingSeconds += dt


        // ================= ZONAS =================

        val excess = speedKmh - speedLimit


        when {

            excess <= 5 -> {
                greenSeconds += dt
            }

            excess in 6..13 -> {
                yellowSeconds += dt
            }

            excess in 14..20 -> {
                redSeconds += dt
            }

            else -> {
                extremeSeconds += dt
            }
        }
    }


    // ===================================================
    // FINALIZAR VIAJE
    // ===================================================

    fun finishTrip(): TripResult {

        tripActive = false


        // Viaje inválido
        if (totalDistanceMeters < MIN_VALID_DISTANCE) {

            return TripResult.empty()
        }


        val totalKm = totalDistanceMeters / 1000.0


        // ================= RATIOS =================

        val totalTime =
            max(1.0, totalDrivingSeconds)


        val greenRatio = greenSeconds / totalTime
        val yellowRatio = yellowSeconds / totalTime
        val redRatio = redSeconds / totalTime
        val extremeRatio = extremeSeconds / totalTime


        // ================= BASE =================

        var points = totalKm * POINTS_PER_KM


        // ================= CASTIGO EXTREMO =================

        if (extremeRatio > 0.40) {

            return TripResult(
                totalDistanceMeters,
                totalDrivingSeconds,
                greenRatio,
                yellowRatio,
                redRatio,
                extremeRatio,
                1.0,
                0
            )
        }


        // ================= PENALTY =================

        val penaltyRaw =
            yellowRatio * YELLOW_PENALTY +
                    redRatio * RED_PENALTY +
                    extremeRatio * EXTREME_PENALTY


        val penalty =
            penaltyRaw.coerceIn(0.0, 0.95)


        points *= (1.0 - penalty)


        // ================= BONUS =================

        when {

            greenRatio >= 1.0 -> {
                points *= 1.5
            }

            greenRatio >= 0.9 -> {
                points *= 1.25
            }
        }


        // ================= CLAMP =================

        points = max(0.0, points)


        return TripResult(
            realDistanceMeters = totalDistanceMeters,
            realDurationSeconds = totalDrivingSeconds,

            greenRatio = greenRatio,
            yellowRatio = yellowRatio,
            redRatio = redRatio,
            extremeRatio = extremeRatio,

            penalty = penalty,
            pointsEarned = points.roundToInt()
        )
    }


    // ===================================================
    // RESET
    // ===================================================

    private fun resetAll() {

        totalDistanceMeters = 0.0
        totalDrivingSeconds = 0.0

        greenSeconds = 0.0
        yellowSeconds = 0.0
        redSeconds = 0.0
        extremeSeconds = 0.0

        lastLocation = null
        lastUpdateTime = 0L

        tripActive = false
    }
}
