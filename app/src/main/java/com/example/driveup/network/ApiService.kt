package com.example.driveup.network

import com.example.driveup.models.LocationData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {

    @Headers(
        "Content-Type: application/json"
    )
    @POST("receive-location")
    suspend fun sendLocation(
        @Body data: LocationData
    ): Response<Unit>
}


