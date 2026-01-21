package com.example.driveup.Models

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val phone: String = "",
    val age: Int = 0,
    val gender: String = "",
    val points: Int = 0,
    val totalKm: Double = 0.0
)
