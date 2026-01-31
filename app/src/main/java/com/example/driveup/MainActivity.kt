package com.example.driveup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.driveup.services.LocationForegroundService
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.example.driveup.navigation.SpeedManager
import com.google.firebase.auth.FirebaseAuth
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlin.math.*
import com.example.driveup.navigation.TripStatsManager
import com.example.driveup.navigation.TripResult
import com.google.firebase.firestore.FirebaseFirestore
import android.text.Editable
import android.text.TextWatcher
import java.net.URLEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.view.inputmethod.InputMethodManager
import com.example.driveup.navigation.OsmSpeedLimitProvider
import com.example.driveup.navigation.SpeedLimitManager
import com.google.firebase.firestore.FieldValue



data class NavigationStep(
    val instruction: String,
    val distance: Double,
    val location: LatLng,
    var preAnnounced: Boolean = false,
    var finalAnnounced: Boolean = false
)



class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap

    private lateinit var etDestination: AutoCompleteTextView
    private lateinit var btnRoute: Button

    private lateinit var btnPreviewRoute: Button

    private lateinit var btnCenter: ImageButton
    private lateinit var tvEta: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var navigationBar: LinearLayout

    private val client = OkHttpClient()
    private lateinit var fusedLocation: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var currentLocation: Location? = null
    private var routePoints: List<LatLng> = emptyList()
    private var destinationLatLng: LatLng? = null
    private var navigating = false
    private var previewing = false
    private var firstZoomDone = false
    private var mapReady = false
    private var lastRecalcTime = 0L
    private var lastEtaUpdate = 0L
    private val ARRIVAL_DISTANCE_METERS = 25f
    private var arrived = false
    private lateinit var etOrigin: AutoCompleteTextView
    private var followUser = true
    private var navigationSteps: List<NavigationStep> = emptyList()
    private var currentStepIndex = 0
    private lateinit var tts: TextToSpeech
    private lateinit var tvStepDistance: TextView
    private var isRecalculating = false
    private lateinit var ivTurnIcon: ImageView
    private lateinit var btnMute: ImageButton
    private var voiceEnabled = true
    private val speedManager = SpeedManager()
    private val speedLimitManager =
        SpeedLimitManager(OsmSpeedLimitProvider())
    private lateinit var tvSpeed: TextView
    private val tripStatsManager = TripStatsManager()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var btnStore: ImageButton


    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // ================= UI =================
        mapView = findViewById(R.id.mapView)
        etOrigin = findViewById(R.id.etOrigin)
        etDestination = findViewById(R.id.etDestination)

        btnRoute = findViewById(R.id.btnRoute)
        btnPreviewRoute = findViewById(R.id.btnPreviewRoute)
        btnCenter = findViewById(R.id.btnCenterLocation)
        tvEta = findViewById(R.id.tvEta)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvStepDistance = findViewById(R.id.tvStepDistance)
        navigationBar = findViewById(R.id.navigationBar)
        ivTurnIcon = findViewById(R.id.ivTurnIcon)
        btnMute = findViewById(R.id.btnMute)
        tvSpeed = findViewById(R.id.tvSpeed)

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)

        // ================= MAPA =================
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            map = it
            map.setStyle(
                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
            ) {
                mapReady = true
                checkPermission()

                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == 1) { // gesto del usuario
                        followUser = false
                        map.locationComponent.cameraMode = CameraMode.NONE
                    }
                }
            }
        }

        // ================= BOTONES =================
        btnRoute.setOnClickListener {
            startService(Intent(this, LocationForegroundService::class.java))
            calculateRoute()
        }

        btnPreviewRoute.setOnClickListener {
            if (previewing) {
                exitPreview()
            } else {
                previewRoute()
            }
        }


        btnCenter.setOnClickListener {
            centerToUserLocation()
        }

        btnMute.setOnClickListener {
            voiceEnabled = !voiceEnabled
            updateMuteIcon()
        }

        btnStore = findViewById(R.id.btnStore)

        btnStore.setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }



        findViewById<Button>(R.id.btnCancelRoute).setOnClickListener {
            confirmCancelRoute()
        }

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            showSettingsMenu(it)
        }

        // ================= TTS =================
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale("es", "ES")
            }
        }

        // ================= AUTOCOMPLETADO =================
        setupAutocomplete(etDestination)
        setupAutocomplete(etOrigin)
    }



    // ================= PERMISOS =================

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) enableLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun enableLocation() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        map.getStyle { style ->
            val options = LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .build()

            val activationOptions =
                LocationComponentActivationOptions.builder(this, style)
                    .locationComponentOptions(options)
                    .useDefaultLocationEngine(true)
                    .build()

            val lc = map.locationComponent
            lc.activateLocationComponent(activationOptions)
            lc.isLocationComponentEnabled = true
            lc.cameraMode = CameraMode.NONE
            lc.renderMode = RenderMode.COMPASS

            startLocationUpdates()
        }
    }

    // ================= UBICACIÓN =================

    private fun startLocationUpdates() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return


        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            500
        )
            .setMinUpdateIntervalMillis(300)
            .setMinUpdateDistanceMeters(0f)
            .build()


        locationCallback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                val loc = result.lastLocation ?: return


                // ================= POSICIÓN =================

                currentLocation = loc
                updateCameraFollowingCar(loc)
                zoomToLocationOnce(loc)


                // ================= VELOCIDAD =================

                val speed = if (loc.hasSpeed()) {
                    speedManager.update(loc)
                } else {
                    speedManager.getCurrentSpeed()
                }

                tvSpeed.text = speed.toString()


                // ================= SPEED LIMIT =================

                // Actualiza en background (OSM / API)
                speedLimitManager.update(
                    lifecycleScope,
                    loc
                )

                val speedLimit =
                    speedLimitManager.getCurrentLimit()


                // ================= STATS =================

                tripStatsManager.onLocationUpdate(
                    loc,
                    speed,
                    speedLimit
                )


                // ================= NAVEGACIÓN =================

                if (navigating) {

                    checkRouteDeviation(loc)
                    updateDynamicEta(loc)
                    checkArrival(loc)

                    updateCurrentInstruction(loc)
                    updateNavigationInstruction(loc)
                    updateStepDistance(loc)
                }
            }
        }


        fusedLocation.requestLocationUpdates(
            request,
            locationCallback!!,
            mainLooper
        )
    }


    // ================= ZOOM INICIAL =================

    private fun zoomToLocationOnce(location: Location) {
        if (firstZoomDone || !mapReady) return
        firstZoomDone = true

        map.locationComponent.cameraMode = CameraMode.NONE

        val position = CameraPosition.Builder()
            .target(getOffsetLatLng(location, 40.0))
            .zoom(17.0)
            .tilt(45.0)
            .bearing(location.bearing.toDouble())
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            1200
        )
    }

    // ================= BOTÓN CENTRAR =================

    private fun centerToUserLocation() {
        val loc = currentLocation ?: return

        followUser = true

        val position = CameraPosition.Builder()
            .target(getOffsetLatLng(loc, 40.0))
            .zoom(17.0)
            .tilt(45.0)
            .bearing(loc.bearing.toDouble())
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            1000
        )
    }

    //====================== TRACKING=============================
    private fun updateCameraFollowingCar(loc: Location) {
        if (!followUser) return
        if (!mapReady) return

        // Bearing solo si velocidad ≥ 2 km/h (≈ 0.55 m/s)
        val bearing = if (loc.hasSpeed() && loc.speed >= 0.55f) {
            loc.bearing.toDouble()
        } else {
            map.cameraPosition.bearing
        }

        val target = getOffsetLatLng(loc, 40.0)

        val position = CameraPosition.Builder()
            .target(target)
            .zoom(17.0)
            .tilt(45.0)
            .bearing(bearing)
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            600
        )
    }






    // ================= RUTA =================

    private fun calculateRoute() {
        val destText = etDestination.text.toString().trim()
        if (destText.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {

            val loc = currentLocation ?: return@launch
            val originLatLng = LatLng(loc.latitude, loc.longitude)

            val dest = geocode(destText) ?: return@launch

            // Estados
            previewing = false
            navigating = true

            destinationLatLng = dest
            fetchRoute(originLatLng, dest)
        }
    }



    private fun fetchRoute(o: LatLng, d: LatLng) {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                    "${o.longitude},${o.latitude};${d.longitude},${d.latitude}" +
                    "?overview=full&geometries=geojson&steps=true"


        val res = client.newCall(Request.Builder().url(url).build())
            .execute().body?.string() ?: return

        runOnUiThread { drawRoute(res) }
    }

    private fun onNavigationStarted() {
        navigating = true
        updateStoreVisibility()

        // Ocultar inputs
        etOrigin.clearFocus()
        etDestination.clearFocus()

        etOrigin.visibility = View.GONE
        etDestination.visibility = View.GONE
        btnRoute.visibility = View.GONE

        // MUY IMPORTANTE: desactivar autocompletado
        etDestination.dismissDropDown()
        etDestination.isEnabled = false

        findViewById<View>(R.id.btnCancelRoute).visibility = View.VISIBLE
        btnPreviewRoute.visibility = View.GONE
    }

    /*private fun cancelRoute() {

        if (!navigating) return

        navigating = false
        arrived = false

        // Guardar puntos SOLO hasta ahora
        val tripResult = tripStatsManager.finishTrip()
        saveTripResult(tripResult)

        // Limpiar navegación
        clearRoute()
        navigationSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null

        // UI
        navigationBar.visibility = View.GONE
        tvInstruction.text = ""
        tvStepDistance.text = ""
        tvEta.text = ""

        // Restaurar inputs
        etOrigin.visibility = View.VISIBLE
        etDestination.visibility = View.VISIBLE
        btnRoute.visibility = View.VISIBLE

        etDestination.isEnabled = true
        etDestination.text.clear()
        etOrigin.text.clear()

        // Botón cancelar
        findViewById<View>(R.id.btnCancelRoute).visibility = View.GONE

        toast("Ruta cancelada")
    }*/

    private fun confirmCancelRoute() {

        if (previewing) {
            exitPreview()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Cancelar ruta")
            .setMessage("¿Quieres cancelar el viaje actual?")
            .setPositiveButton("Sí") { _, _ ->
                cancelRouteWithSummary()
            }
            .setNegativeButton("No", null)
            .show()
    }


    private fun cancelRouteWithSummary() {

        if (!navigating) return

        navigating = false
        arrived = false
        updateStoreVisibility()


        // Finalizar viaje
        val tripResult = tripStatsManager.finishTrip()
        saveTripResult(tripResult)

        // Limpiar navegación
        clearRoute()
        navigationSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null

        // UI
        navigationBar.visibility = View.GONE
        tvInstruction.text = ""
        tvStepDistance.text = ""
        tvEta.text = ""

        etOrigin.visibility = View.VISIBLE
        etDestination.visibility = View.VISIBLE
        btnRoute.visibility = View.VISIBLE

        etDestination.isEnabled = true
        etDestination.text.clear()
        etOrigin.text.clear()

        findViewById<View>(R.id.btnCancelRoute).visibility = View.GONE

        btnPreviewRoute.visibility = View.VISIBLE
        showTripSummary(tripResult)
    }



    private fun drawRoute(json: String) {

        if (!mapReady) return

        val route = JSONObject(json)
            .getJSONArray("routes")
            .getJSONObject(0)


        // ================= DISTANCIA / ETA =================

        val distanceMeters = route.getDouble("distance")
        val durationSeconds = route.getDouble("duration")

        val distanceKm = distanceMeters / 1000
        val durationMin = durationSeconds / 60

        tvEta.text = formatTime(distanceKm, durationMin)
        navigationBar.visibility = View.VISIBLE


        // ================= GEOMETRÍA =================

        val coords = route
            .getJSONObject("geometry")
            .getJSONArray("coordinates")

        val points = mutableListOf<Point>()
        val latLngs = mutableListOf<LatLng>()

        for (i in 0 until coords.length()) {

            val c = coords.getJSONArray(i)

            points.add(
                Point.fromLngLat(
                    c.getDouble(0),
                    c.getDouble(1)
                )
            )

            latLngs.add(
                LatLng(
                    c.getDouble(1),
                    c.getDouble(0)
                )
            )
        }

        routePoints = latLngs


        // ================= DIBUJAR RUTA =================

        map.getStyle { style ->

            if (style.getSource("route") == null) {

                style.addSource(
                    GeoJsonSource(
                        "route",
                        LineString.fromLngLats(points)
                    )
                )

                style.addLayer(
                    LineLayer("route-layer", "route")
                        .withProperties(
                            lineColor("#2196F3"),
                            lineWidth(6f)
                        )
                )

            } else {

                (style.getSource("route") as GeoJsonSource)
                    .setGeoJson(
                        LineString.fromLngLats(points)
                    )
            }
        }


        // ================= PREVIEW =================

        if (previewing) {

            tvInstruction.text = "Vista previa de la ruta"
            tvStepDistance.text = ""

            return
        }


        // ================= TRIP STATS =================
        // Solo iniciamos viaje la primera vez

        if (!isRecalculating) {
            tripStatsManager.startTrip()
        }

        isRecalculating = false


        // ================= ESTADO =================

        onNavigationStarted()

        arrived = false
        navigating = true


        // ================= INSTRUCCIONES =================

        val legs = route.getJSONArray("legs")
        val stepsJson = legs.getJSONObject(0).getJSONArray("steps")

        val steps = mutableListOf<NavigationStep>()

        for (i in 0 until stepsJson.length()) {

            val step = stepsJson.getJSONObject(i)

            val maneuver = step.getJSONObject("maneuver")

            val type = maneuver.getString("type")
            val modifier = maneuver.optString("modifier", "")
            val name = step.optString("name", "")

            val distance = step.getDouble("distance")

            val loc = maneuver.getJSONArray("location")

            val latLng = LatLng(
                loc.getDouble(1),
                loc.getDouble(0)
            )

            val instruction =
                buildInstruction(type, modifier, name)

            steps.add(
                NavigationStep(
                    instruction = instruction,
                    distance = distance,
                    location = latLng
                )
            )
        }

        navigationSteps = steps
        currentStepIndex = 0


        if (navigationSteps.isNotEmpty()) {

            val first = navigationSteps[0]

            tvInstruction.text = first.instruction

            updateTurnIcon(first.instruction)
        }
    }




    // ================= RECÁLCULO =================

    private fun checkRouteDeviation(loc: Location) {

        if (!navigating) return
        if (routePoints.isEmpty()) return
        if (destinationLatLng == null) return


        var minDistance = Float.MAX_VALUE


        for (p in routePoints) {

            val tmp = Location("").apply {
                latitude = p.latitude
                longitude = p.longitude
            }

            val d = loc.distanceTo(tmp)

            if (d < minDistance) {
                minDistance = d
            }
        }


        // ================= DESVÍO =================

        val now = System.currentTimeMillis()

        if (
            minDistance > 50 &&
            now - lastRecalcTime > 8000
        ) {

            lastRecalcTime = now
            isRecalculating = true


            val current = LatLng(
                loc.latitude,
                loc.longitude
            )

            val dest = destinationLatLng ?: return


            lifecycleScope.launch(Dispatchers.IO) {

                runOnUiThread {

                    clearRoute()

                    navigationSteps = emptyList()
                    currentStepIndex = 0
                    arrived = false

                    tvInstruction.text = "Recalculando ruta…"
                    tvStepDistance.text = ""
                }


                fetchRoute(current, dest)
            }
        }
    }



    private fun updateDynamicEta(loc: Location) {
        if (destinationLatLng == null) return
        if (System.currentTimeMillis() - lastEtaUpdate < 5000) return

        lastEtaUpdate = System.currentTimeMillis()

        lifecycleScope.launch(Dispatchers.IO) {
            val url =
                "https://router.project-osrm.org/route/v1/driving/" +
                        "${loc.longitude},${loc.latitude};" +
                        "${destinationLatLng!!.longitude},${destinationLatLng!!.latitude}" +
                        "?overview=false"

            val res = client.newCall(Request.Builder().url(url).build())
                .execute().body?.string() ?: return@launch

            val route = JSONObject(res)
                .getJSONArray("routes")
                .getJSONObject(0)

            val km = route.getDouble("distance") / 1000
            val min = route.getDouble("duration") / 60

            runOnUiThread {
                tvEta.text = formatTime(km, min)
            }
        }
    }


    // ================= UTIL =================

    private fun geocode(text: String): LatLng? {
        val url =
            "https://nominatim.openstreetmap.org/search?q=${text.replace(" ", "+")}&format=json&limit=1"
        val res = client.newCall(
            Request.Builder().url(url).header("User-Agent", "DriveUp").build()
        ).execute().body?.string() ?: return null

        val arr = JSONArray(res)
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return LatLng(o.getDouble("lat"), o.getDouble("lon"))
    }

    private fun formatTime(km: Double, minutes: Double): String {
        val mins = minutes.toInt()
        val h = mins / 60
        val m = mins % 60
        val time = if (h > 0) "${h}h ${m} min" else "$m min"
        return "⏱ $time · 📏 %.1f km".format(km)
    }

    // ================= ARRIVAL =================

    private fun checkArrival(loc: Location) {
        if (arrived || destinationLatLng == null) return

        val dest = Location("").apply {
            latitude = destinationLatLng!!.latitude
            longitude = destinationLatLng!!.longitude
        }

        val distance = loc.distanceTo(dest)

        if (distance <= ARRIVAL_DISTANCE_METERS) {
            onArrived()
        }
    }

    private fun onArrived() {
        arrived = true
        navigating = false
        updateStoreVisibility()


        toast("Has llegado a tu destino")

        clearRoute()
        btnPreviewRoute.visibility = View.VISIBLE

        val tripResult = tripStatsManager.finishTrip()
        saveTripResult(tripResult)
        showTripSummary(tripResult)

        // UI
        navigationBar.visibility = LinearLayout.GONE

        // Cámara
        map.locationComponent.cameraMode = CameraMode.TRACKING

        navigationSteps = emptyList()
        currentStepIndex = 0
        tvInstruction.text = ""

        speedManager.reset()
        tvSpeed.text = "0"

    }

    private fun clearRoute() {
        map.getStyle { style ->
            if (style.getLayer("route-layer") != null) {
                style.removeLayer("route-layer")
            }
            if (style.getSource("route") != null) {
                style.removeSource("route")
            }
        }

        routePoints = emptyList()
    }


    // ================= CAMERA =================
    private fun getOffsetLatLng(
        location: Location,
        offsetMeters: Double
    ): LatLng {

        val bearing = location.bearing.toDouble()

        val earthRadius = 6378137.0 // metros
        val distance = offsetMeters / earthRadius

        val lat1 = Math.toRadians(location.latitude)
        val lon1 = Math.toRadians(location.longitude)
        val brng = Math.toRadians(bearing)

        val lat2 = asin(
            sin(lat1) * cos(distance) +
                    cos(lat1) * sin(distance) * cos(brng)
        )

        val lon2 = lon1 + atan2(
            sin(brng) * sin(distance) * cos(lat1),
            cos(distance) - sin(lat1) * sin(lat2)
        )

        return LatLng(
            Math.toDegrees(lat2),
            Math.toDegrees(lon2)
        )
    }



    // ================= DIRECCIONS =================

    private fun buildInstruction(
        type: String,
        modifier: String,
        name: String
    ): String {

        val street = if (name.isNotEmpty()) " en $name" else ""

        return when (type) {

            "fork" -> when (modifier) {
                "left" -> "↖️ Mantente a la izquierda$street"
                "right" -> "↗️ Mantente a la derecha$street"
                else -> "Mantente en tu carril$street"
            }

            "merge" -> when (modifier) {
                "left" -> "↖️ Incorpórate por la izquierda$street"
                "right" -> "↗️ Incorpórate por la derecha$street"
                else -> "Incorpórate$street"
            }

            "turn" -> when (modifier) {
                "left" -> "⬅️ Gira a la izquierda$street"
                "right" -> "➡️ Gira a la derecha$street"
                "slight_left" -> "↖️ Mantente a la izquierda$street"
                "slight_right" -> "↗️ Mantente a la derecha$street"
                "sharp_left" -> "⬅️ Gira bruscamente a la izquierda$street"
                "sharp_right" -> "➡️ Gira bruscamente a la derecha$street"
                "straight" -> "⬆️ Sigue recto$street"
                else -> "Gira$street"
            }

            "roundabout" -> "⟳ Entra en la rotonda$street"

            "depart" -> "Comienza la ruta"
            "arrive" -> "Has llegado a tu destino"

            else -> "Continúa$street"
        }
    }


    private fun updateCurrentInstruction(loc: Location) {
        if (navigationSteps.isEmpty()) return
        if (currentStepIndex >= navigationSteps.size) return

        val step = navigationSteps[currentStepIndex]

        val stepLoc = Location("").apply {
            latitude = step.location.latitude
            longitude = step.location.longitude
        }

        val distance = loc.distanceTo(stepLoc)

        if (distance < 20) { // metros
            currentStepIndex++
            if (currentStepIndex < navigationSteps.size) {
                tvInstruction.text = navigationSteps[currentStepIndex].instruction
            }
        }
    }

    private fun speak(text: String) {
        if (!voiceEnabled) return
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }


    private fun updateMuteIcon() {
        if (voiceEnabled) {
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            toast("Voz activada 🔊")
        } else {
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
            toast("Voz desactivada 🔇")
        }
    }


    private fun updateNavigationInstruction(loc: Location) {
        if (navigationSteps.isEmpty()) return
        if (currentStepIndex >= navigationSteps.size) return

        val step = navigationSteps[currentStepIndex]

        val stepLoc = Location("").apply {
            latitude = step.location.latitude
            longitude = step.location.longitude
        }

        val distance = loc.distanceTo(stepLoc).toInt()

        // ================= AVISO PREVIO =================
        if (distance in 40..100 && !step.preAnnounced) {
            step.preAnnounced = true

            val msg = "En $distance metros, ${step.instruction}"
            tvInstruction.text = msg
            speak(msg)
            updateTurnIconFromInstruction(step)
        }

        // ================= AVISO FINAL =================
        if (distance <= 25 && !step.finalAnnounced) {
            step.finalAnnounced = true

            val msg = "Ahora, ${step.instruction}"
            tvInstruction.text = msg
            speak(msg)
            updateTurnIconFromInstruction(step)
        }

        // ================= PASO COMPLETADO =================
        if (distance <= 15) {
            currentStepIndex++

            if (currentStepIndex < navigationSteps.size) {
                val next = navigationSteps[currentStepIndex]
                tvInstruction.text = next.instruction
                updateTurnIconFromInstruction(next)
            }
        }
    }


    private fun updateStepDistance(loc: Location) {
        if (navigationSteps.isEmpty()) return
        if (currentStepIndex >= navigationSteps.size) return

        val step = navigationSteps[currentStepIndex]

        val stepLoc = Location("").apply {
            latitude = step.location.latitude
            longitude = step.location.longitude
        }

        val meters = loc.distanceTo(stepLoc).toInt()
        tvStepDistance.text = "$meters m"
    }

    private fun alignStepWithCurrentLocation(loc: Location) {
        var closestIndex = 0
        var minDistance = Float.MAX_VALUE

        for (i in navigationSteps.indices) {
            val stepLoc = Location("").apply {
                latitude = navigationSteps[i].location.latitude
                longitude = navigationSteps[i].location.longitude
            }

            val d = loc.distanceTo(stepLoc)
            if (d < minDistance) {
                minDistance = d
                closestIndex = i
            }
        }

        currentStepIndex = closestIndex
        tvInstruction.text = navigationSteps[currentStepIndex].instruction

        // 🔄 Resetear avisos tras recálculo
        for (step in navigationSteps) {
            step.preAnnounced = false
            step.finalAnnounced = false
        }
    }


    private fun updateTurnIcon(instruction: String) {

        val icon = when {
            instruction.contains("izquierda") -> android.R.drawable.arrow_up_float
            instruction.contains("derecha") -> android.R.drawable.arrow_down_float
            instruction.contains("recto") -> android.R.drawable.arrow_up_float
            instruction.contains("rotonda") -> android.R.drawable.ic_menu_rotate
            else -> android.R.drawable.ic_media_play
        }

        ivTurnIcon.setImageResource(icon)
    }

    private fun updateTurnIconFromInstruction(step: NavigationStep) {
        when {
            step.instruction.contains("izquierda", true) ->
                ivTurnIcon.setImageResource(R.drawable.ic_turn_left)

            step.instruction.contains("derecha", true) ->
                ivTurnIcon.setImageResource(R.drawable.ic_turn_right)

            step.instruction.contains("recto", true) ->
                ivTurnIcon.setImageResource(R.drawable.ic_straight)

            step.instruction.contains("rotonda", true) ->
                ivTurnIcon.setImageResource(R.drawable.ic_roundabout)

            step.instruction.contains("llegado", true) ->
                ivTurnIcon.setImageResource(R.drawable.ic_arrive)
        }
    }
//------------------------------LOGOUT------------------------------------
    private fun logout() {
        FirebaseAuth.getInstance().signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_settings, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {

                R.id.action_logout -> {
                    confirmLogout()
                    true
                }

                else -> false
            }
        }


        popup.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


//=================== PUNTOS ===============================
private fun saveTripResult(tripResult: TripResult) {

    val uid = auth.currentUser?.uid ?: return
    val userRef = db.collection("users").document(uid)

    db.runTransaction { transaction ->

        val snapshot = transaction.get(userRef)

        val currentKm = snapshot.getDouble("totalKm") ?: 0.0

        val newKm = currentKm + (tripResult.realDistanceMeters / 1000.0)

        transaction.update(
            userRef,
            mapOf(
                "points" to FieldValue.increment(
                    tripResult.pointsEarned.toLong()
                ),
                "totalKm" to newKm
            )
        )
    }
        .addOnSuccessListener {
            toast("+${tripResult.pointsEarned} puntos")
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
            toast("Error guardando puntos")
        }
}



    private fun showTripSummary(tripResult: TripResult) {

        val view =
            layoutInflater.inflate(
                R.layout.dialog_trip_summary,
                null
            )


        // ================= CONVERSIONES =================

        val realKm =
            tripResult.realDistanceMeters / 1000.0

        val realHours =
            tripResult.realDurationSeconds / 3600.0


        // ================= VELOCIDAD MEDIA =================

        val realSpeed =
            if (realHours > 0)
                realKm / realHours
            else 0.0


        // ================= RATIOS =================

        val greenPercent =
            (tripResult.greenRatio * 100).toInt()

        val penaltyPercent =
            (tripResult.penalty * 100).toInt()


        // ================= TEXTO =================

        view.findViewById<TextView>(R.id.tvDistance).text =
            "Distancia: %.2f km".format(realKm)

        view.findViewById<TextView>(R.id.tvRealSpeed).text =
            "Velocidad media: %.1f km/h".format(realSpeed)

        view.findViewById<TextView>(R.id.tvEfficiency).text =
            "Conducción responsable: $greenPercent%"

        view.findViewById<TextView>(R.id.tvPenalty).text =
            "Penalización: -$penaltyPercent%"

        view.findViewById<TextView>(R.id.tvPoints).text =
            "Puntos ganados: ${tripResult.pointsEarned}"


        // ================= DIÁLOGO =================

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()


        view.findViewById<Button>(R.id.btnContinue)
            .setOnClickListener {
                dialog.dismiss()
            }

        dialog.show()
    }




    //=================== VER RUTA ================================
    private fun previewRoute() {
        val destText = etDestination.text.toString().trim()
        val originText = etOrigin.text.toString().trim()

        if (destText.isEmpty()) {
            toast("Selecciona un destino")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {

            val origin: LatLng = if (originText.isEmpty()) {
                val loc = currentLocation
                if (loc == null) {
                    withContext(Dispatchers.Main) {
                        toast("No se puede obtener ubicación actual")
                    }
                    return@launch
                } else LatLng(loc.latitude, loc.longitude)
            } else {
                geocode(originText) ?: return@launch
            }

            val dest = geocode(destText) ?: return@launch

            // Cambiamos estado
            previewing = true
            navigating = false

            destinationLatLng = dest

            // Cambiar texto del botón
            withContext(Dispatchers.Main) {
                btnPreviewRoute.text = "Dejar de ver ruta"
            }

            fetchRoute(origin, dest)
        }
    }




    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()

        locationCallback?.let {
            fusedLocation.removeLocationUpdates(it)
        }

        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts.shutdown()
    }

    private fun exitPreview() {
        previewing = false

        clearRoute()
        routePoints = emptyList()

        navigationBar.visibility = View.GONE
        tvEta.text = ""
        tvInstruction.text = ""
        tvStepDistance.text = ""

        btnPreviewRoute.text = "Ver ruta"

        toast("Vista previa cancelada")
    }


    //======================= AUTOCOMPLETADO ===============================
    private fun setupAutocomplete(editText: AutoCompleteTextView) {

        var searchJob: Job? = null
        var autocompleteLocked = false

        // ================= CAMBIO DE TEXTO =================
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Si el usuario escribe o borra → desbloqueamos autocomplete
                if (before > 0 || count > 0) {
                    autocompleteLocked = false
                }
            }

            override fun afterTextChanged(s: Editable?) {

                // No buscamos si:
                // - no tiene foco
                // - está bloqueado
                if (!editText.isFocused || autocompleteLocked) return

                val query = s.toString().trim()

                // Muy corto → nada de sugerencias
                if (query.length < 3) {
                    editText.dismissDropDown()
                    return
                }

                // Cancelamos búsquedas anteriores
                searchJob?.cancel()
                searchJob = lifecycleScope.launch(Dispatchers.IO) {
                    delay(300) // debounce

                    try {
                        val url =
                            "https://api.geoapify.com/v1/geocode/autocomplete" +
                                    "?text=${URLEncoder.encode(query, "UTF-8")}" +
                                    "&lang=es&limit=5&apiKey=bfde33420b5842fea5085295622148e9"

                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: return@launch

                        val suggestions = mutableListOf<String>()
                        val features = JSONObject(body).optJSONArray("features") ?: JSONArray()

                        for (i in 0 until features.length()) {
                            val props = features.getJSONObject(i).getJSONObject("properties")
                            suggestions.add(props.getString("formatted"))
                        }

                        withContext(Dispatchers.Main) {
                            val adapter = ArrayAdapter(
                                this@MainActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                suggestions
                            )
                            editText.setAdapter(adapter)

                            if (!autocompleteLocked && editText.isFocused) {
                                editText.showDropDown()
                            }
                        }

                    } catch (_: Exception) {}
                }
            }
        })

        // ================= SELECCIÓN DE SUGERENCIA =================
        editText.setOnItemClickListener { parent, _, position, _ ->

            val selected = parent.getItemAtPosition(position) as String

            autocompleteLocked = true
            editText.setText(selected)
            editText.setSelection(selected.length)

            editText.dismissDropDown()
            editText.clearFocus()
            hideKeyboard(editText)
        }

        // ================= ENTER / ✓ DEL TECLADO =================
        editText.setOnEditorActionListener { _, _, _ ->
            autocompleteLocked = true
            editText.dismissDropDown()
            editText.clearFocus()
            hideKeyboard(editText)
            true
        }

        // ================= CLICK EN EL CAMPO =================
        // Permite volver a escribir si el usuario se equivocó
        editText.setOnClickListener {
            autocompleteLocked = false
            editText.requestFocus()
        }

        // ================= CLICK FUERA =================
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                editText.dismissDropDown()
            }
        }
    }



    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    //================================ TIENDA =================================
    private fun updateStoreVisibility() {
        btnStore.visibility =
            if (!navigating && !previewing) View.VISIBLE else View.GONE
    }


    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
