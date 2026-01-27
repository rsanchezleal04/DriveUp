package com.example.driveup.navigation

import android.location.Location
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TripStatsManager {

    // ================= TOTAL ACUMULADO =================
    private var totalPoints = 0
    private var totalDistanceMeters = 0.0
    private var totalTimeSeconds = 0.0
    private var totalOptimalDistanceMeters = 0.0
    private var totalOptimalDurationSeconds = 0.0

    // ================= RATIO PONDERADO =================
    private var totalWeightedRatio = 0.0
    private var totalWeightedDistance = 0.0

    // ================= SEGMENTO ACTUAL =================
    private var optimalDistanceMeters = 0.0
    private var optimalDurationSeconds = 0.0

    private var segmentStartTime = 0L
    private var segmentDistanceMeters = 0.0
    private var lastLocation: Location? = null

    private var segmentActive = false

    // ================= CONFIG =================
    private val POINTS_PER_KM = 10
    private val MIN_VALID_DISTANCE = 100.0 // metros


    // ===================================================
    // INICIAR VIAJE
    // ===================================================
    fun startTrip(
        optimalDistanceMeters: Double,
        optimalDurationSeconds: Double
    ) {
        resetAll()
        startNewSegment(optimalDistanceMeters, optimalDurationSeconds)
    }


    // ===================================================
    // NUEVO SEGMENTO
    // ===================================================
    fun startNewSegment(
        optimalDistanceMeters: Double,
        optimalDurationSeconds: Double
    ) {

        this.optimalDistanceMeters = optimalDistanceMeters

        // Duración mínima coherente
        this.optimalDurationSeconds =
            if (optimalDurationSeconds > 5)
                optimalDurationSeconds
            else
                (optimalDistanceMeters / 1000.0) * 60


        totalOptimalDistanceMeters += optimalDistanceMeters

        // ⚠️ IMPORTANTE: sumamos la corregida
        totalOptimalDurationSeconds += this.optimalDurationSeconds


        segmentDistanceMeters = 0.0
        lastLocation = null
        segmentStartTime = System.currentTimeMillis()
        segmentActive = true
    }


    // ===================================================
    // ACTUALIZAR POSICIÓN
    // ===================================================
    fun onLocationUpdate(location: Location) {

        if (!segmentActive) return

        lastLocation?.let { last ->

            val delta = last.distanceTo(location)

            // Filtro GPS
            if (delta in 0.5..150.0) {
                segmentDistanceMeters += delta
                totalDistanceMeters += delta
            }
        }

        lastLocation = location
    }


    // ===================================================
    // CERRAR SEGMENTO
    // ===================================================
    fun closeCurrentSegment(): Int {

        if (!segmentActive) return 0


        val realTimeSeconds =
            max(1.0, (System.currentTimeMillis() - segmentStartTime) / 1000.0)


        // Segmento inválido (ruido / cancelación)
        if (segmentDistanceMeters < MIN_VALID_DISTANCE) {
            segmentActive = false
            return 0
        }


        val realKm = segmentDistanceMeters / 1000.0
        val realHours = realTimeSeconds / 3600.0
        val optimalHours = optimalDurationSeconds / 3600.0
        val optimalKm = optimalDistanceMeters / 1000.0


        // ================= VELOCIDADES =================

        val idealSpeed =
            if (optimalHours > 0)
                optimalKm / optimalHours
            else 0.0


        val idealSpeedWithMargin = idealSpeed + 5.0


        val realSpeed =
            if (realHours > 0)
                realKm / realHours
            else 0.0


        val ratio =
            if (idealSpeedWithMargin > 0)
                realSpeed / idealSpeedWithMargin
            else 1.0


        val cappedRatio = min(ratio, 1.0)


        // ================= RATIO PONDERADO =================

        totalWeightedRatio += cappedRatio * realKm
        totalWeightedDistance += realKm


        // ================= MULTIPLICADOR =================

        val multiplier = when {
            ratio < 0.5 -> 0.0
            ratio <= 1.0 -> 1.0
            ratio <= 1.10 -> 0.75
            ratio <= 1.20 -> 0.5
            else -> 0.0
        }


        // ================= PUNTOS =================

        val basePoints = realKm * POINTS_PER_KM
        val segmentPoints = (basePoints * multiplier).roundToInt()


        // ================= ACUMULAR =================

        totalPoints += segmentPoints
        totalTimeSeconds += realTimeSeconds


        segmentActive = false

        return segmentPoints
    }


    // ===================================================
    // FINALIZAR VIAJE
    // ===================================================
    fun finishTrip(): TripResult {

        closeCurrentSegment()


        // 🚨 VIAJE DEMASIADO CORTO → INVALIDO
        if (totalDistanceMeters < MIN_VALID_DISTANCE) {

            return TripResult(
                optimalDistanceMeters = 0.0,
                realDistanceMeters = 0.0,
                optimalDurationSeconds = 0.0,
                realDurationSeconds = 0.0,
                ratio = 0.0,
                pointsEarned = 0
            )
        }


        val avgRatio =
            if (totalWeightedDistance > 0)
                totalWeightedRatio / totalWeightedDistance
            else 0.0


        return TripResult(
            optimalDistanceMeters = totalOptimalDistanceMeters,
            realDistanceMeters = totalDistanceMeters,
            optimalDurationSeconds = totalOptimalDurationSeconds,
            realDurationSeconds = totalTimeSeconds,
            ratio = avgRatio,
            pointsEarned = totalPoints
        )
    }


    // ====================== RESET ======================
    private fun resetAll() {

        totalPoints = 0
        totalDistanceMeters = 0.0
        totalTimeSeconds = 0.0
        totalOptimalDistanceMeters = 0.0
        totalOptimalDurationSeconds = 0.0

        totalWeightedRatio = 0.0
        totalWeightedDistance = 0.0

        segmentActive = false
        lastLocation = null
    }
}
