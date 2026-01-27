package com.example.driveup.navigation

data class TripResult(
    val optimalDistanceMeters: Double,
    val realDistanceMeters: Double,
    val optimalDurationSeconds: Double,
    val realDurationSeconds: Double,
    val ratio: Double,
    val pointsEarned: Int
)
