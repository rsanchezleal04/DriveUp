package com.example.driveup.navigation

import android.location.Location
import kotlin.math.max

class TripStatsManager {

    // ===== Datos óptimos (OSRM) =====
    private var optimalDistanceMeters: Double = 0.0
    private var optimalDurationSeconds: Double = 0.0

    // ===== Datos reales =====
    private var tripStartTime: Long = 0L
    private var realDistanceMeters: Double = 0.0
    private var lastLocation: Location? = null

    private var tripActive = false

    // ===== CONFIG =====
    private val POINTS_PER_KM = 10

    // INICIO DEL VIAJE
    fun startTrip(
        optimalDistanceMeters: Double,
        optimalDurationSeconds: Double
    ) {
        this.optimalDistanceMeters = optimalDistanceMeters
        this.optimalDurationSeconds = optimalDurationSeconds

        realDistanceMeters = 0.0
        lastLocation = null
        tripStartTime = System.currentTimeMillis()
        tripActive = true
    }

    // ACTUALIZAR UBICACIÓN
    fun onLocationUpdate(location: Location) {
        if (!tripActive) return

        lastLocation?.let { last ->
            val delta = last.distanceTo(location)
            if (delta > 0) {
                realDistanceMeters += delta
            }
        }

        lastLocation = location
    }

    // FINALIZAR VIAJE
    fun finishTrip(): TripResult {
        if (!tripActive) {
            return TripResult.EMPTY
        }

        tripActive = false

        val realTimeSeconds =
            max(1.0, (System.currentTimeMillis() - tripStartTime) / 1000.0)

        val optimalSpeed = optimalDistanceMeters / optimalDurationSeconds
        val realSpeed = realDistanceMeters / realTimeSeconds

        val ratio = realSpeed / optimalSpeed

        val multiplier = when {
            ratio <= 1.0 -> 1.0
            ratio <= 1.10 -> 0.9
            ratio <= 1.20 -> 0.5
            else -> 0.0
        }

        val basePoints =
            (optimalDistanceMeters / 1000.0) * POINTS_PER_KM

        val finalPoints = (basePoints * multiplier).toInt()

        return TripResult(
            optimalDistanceMeters = optimalDistanceMeters,
            realDistanceMeters = realDistanceMeters,
            optimalDurationSeconds = optimalDurationSeconds,
            realDurationSeconds = realTimeSeconds,
            ratio = ratio,
            pointsEarned = finalPoints
        )
    }
}
