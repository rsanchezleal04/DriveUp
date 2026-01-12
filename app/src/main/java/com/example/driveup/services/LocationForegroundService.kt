package com.example.driveup.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.driveup.NotificationUtils
import com.example.driveup.models.LocationData
import com.example.driveup.network.RetrofitClient
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import com.example.driveup.location.LocationBus

class LocationForegroundService : Service() {

    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // Control de envíos al servidor
    private var lastSentTime = 0L
    private var lastLat = 0.0
    private var lastLng = 0.0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(10_000) // cada 10s
            .setMinUpdateDistanceMeters(20f)
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    handleNewLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = NotificationUtils.createNotification(
            this,
            "Seguimiento de ubicación activo",
            "DriveUp está usando tu GPS en tiempo real"
        )

        startForeground(1, notification)
        startLocationUpdates()

        return START_NOT_STICKY
    }

    private fun handleNewLocation(location: Location) {

        // Validación básica
        if (location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) return

        val now = System.currentTimeMillis()

        val distance = FloatArray(1)
        Location.distanceBetween(
            lastLat,
            lastLng,
            location.latitude,
            location.longitude,
            distance
        )

        // Reglas de envío al servidor
        if (now - lastSentTime >= 30_000 && distance[0] >= 30) {
            lastSentTime = now
            lastLat = location.latitude
            lastLng = location.longitude

            val data = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = now
            )

            sendLocationToServer(data)
        }

        // 🔵 Actualización para el mapa
        LocationBus.location.postValue(location)
    }

    private fun startLocationUpdates() {

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocation.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToServer(data: LocationData) {
        serviceScope.launch {
            try {
                val response = RetrofitClient.api.sendLocation(data)
                if (response.isSuccessful) {
                    Log.d("LocationService", "Ubicación enviada")
                } else {
                    Log.e("LocationService", "Error servidor: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Error red: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        fusedLocation.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
