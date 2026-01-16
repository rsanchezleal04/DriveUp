package com.example.driveup.Models

data class User(
    val uid: String = "",
    val email: String = "",
    val points: Int = 0,
    val totalKm: Double = 0.0
)
