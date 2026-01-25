package com.example.driveup.store

data class StoreItem(
    val name: String = "",
    val description: String = "",
    val price: Int = 0,
    val category: String = "",
    val active: Boolean = true,
    val priority: Int = 0,
    val code: String = "",
    var purchased: Boolean = false
)
