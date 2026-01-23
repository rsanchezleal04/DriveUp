package com.example.driveup.store

data class StoreItem(
    val name: String,
    val description: String,
    val price: Int,
    var purchased: Boolean = false
)
