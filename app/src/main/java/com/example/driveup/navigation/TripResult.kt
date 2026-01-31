package com.example.driveup.navigation

data class TripResult(

    val realDistanceMeters: Double,
    val realDurationSeconds: Double,

    val greenRatio: Double,
    val yellowRatio: Double,
    val redRatio: Double,
    val extremeRatio: Double,

    val penalty: Double,

    val pointsEarned: Int
){
    companion object {

        fun empty(): TripResult {
            return TripResult(
                realDistanceMeters = 0.0,
                realDurationSeconds = 0.0,

                greenRatio = 0.0,
                yellowRatio = 0.0,
                redRatio = 0.0,
                extremeRatio = 0.0,

                penalty = 0.0,
                pointsEarned = 0
            )
        }
    }
}

