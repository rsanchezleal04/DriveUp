package com.example.driveup.navigation

import android.location.Location

interface SpeedLimitProvider {

    suspend fun getSpeedLimit(location: Location): Int
}
