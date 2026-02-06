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
import android.view.ViewGroup
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate


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
    private lateinit var tripTopBarOrange: LinearLayout
    private lateinit var tvInstructionTop: TextView
    private lateinit var tvNextDistance: TextView
    private lateinit var tvEtaTop: TextView
    private lateinit var ivTurnIconSmall: ImageView
    private lateinit var tvTotalDistanceTop: TextView
    private lateinit var btnCancelRoute: Button
    private lateinit var previewPanel: LinearLayout
    private lateinit var tvPreviewTime: TextView
    private lateinit var tvPreviewDistance: TextView
    private lateinit var btnStartFromPreview: Button
    private lateinit var btnClosePreview: Button
    private lateinit var routeInputContainer: LinearLayout
    private lateinit var cancelButtonContainer: LinearLayout




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
        tripTopBarOrange = findViewById(R.id.tripTopBarOrange)
        tvInstructionTop = findViewById(R.id.tvInstructionTop)
        tvNextDistance = findViewById(R.id.tvNextDistance)
        tvEtaTop = findViewById(R.id.tvEtaTop)
        ivTurnIconSmall = findViewById(R.id.ivTurnIconSmall)
        tvTotalDistanceTop = findViewById(R.id.tvTotalDistanceTop)
        btnCancelRoute = findViewById(R.id.btnCancelRoute)
        previewPanel = findViewById(R.id.previewPanel)
        tvPreviewTime = findViewById(R.id.tvPreviewTime)
        tvPreviewDistance = findViewById(R.id.tvPreviewDistance)
        btnStartFromPreview = findViewById(R.id.btnStartFromPreview)
        btnClosePreview = findViewById(R.id.btnClosePreview)
        routeInputContainer = findViewById(R.id.routeInputContainer)
        cancelButtonContainer = findViewById(R.id.cancelButtonContainer)

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

        btnCancelRoute.setOnClickListener {
            confirmCancelRoute()
        }

        btnStartFromPreview.setOnClickListener {
            startNavigationFromPreview()
        }

        btnClosePreview.setOnClickListener {
            exitPreview()
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


        updateStoreVisibility()
    }

    //============================== AUX A ONCREATE =================================
    private fun startNavigationFromPreview() {
        if (!previewing || destinationLatLng == null) return

        // IMPORTANTE: Recalcular desde ubicación actual, no desde origen del preview
        val currentLoc = currentLocation
        if (currentLoc == null) {
            toast("No se puede obtener ubicación actual")
            return
        }

        // Ocultar panel de preview
        previewPanel.visibility = View.GONE

        // Cambiar a modo navegación (esto recalculará la ruta desde ubicación actual)
        previewing = false
        navigating = true

        // Iniciar navegación desde ubicación actual
        val originLatLng = LatLng(currentLoc.latitude, currentLoc.longitude)

        lifecycleScope.launch(Dispatchers.IO) {
            fetchRoute(originLatLng, destinationLatLng!!)
        }

        // Iniciar stats de viaje
        tripStatsManager.startTrip()

        toast("Navegación iniciada desde tu ubicación actual")
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
        previewing = false
        updateStoreVisibility()

        runOnUiThread {
            // Ocultar inputs
            etOrigin.clearFocus()
            etDestination.clearFocus()
            etOrigin.visibility = View.GONE
            etDestination.visibility = View.GONE
            btnRoute.visibility = View.GONE
            btnPreviewRoute.visibility = View.GONE

            // Ocultar panel de preview si está visible
            previewPanel.visibility = View.GONE

            // Ocultar inputs container
            routeInputContainer.visibility = View.GONE

            // Desactivar autocompletado y habilitar inputs
            etDestination.dismissDropDown()
            etDestination.isEnabled = true
            etOrigin.isEnabled = true

            // Mostrar barra naranja de navegación
            tripTopBarOrange.visibility = View.VISIBLE

            // Ocultar barra inferior
            navigationBar.visibility = View.GONE

            // Mostrar botón cancelar CON NUEVO DISEÑO
            cancelButtonContainer.visibility = View.VISIBLE
        }
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

        // Ocultar barra naranja
        tripTopBarOrange.visibility = View.GONE

        // OCULTAR CONTENEDOR DEL BOTÓN CANCELAR
        cancelButtonContainer.visibility = View.GONE

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

        // MOSTRAR routeInputContainer COMPLETO (con inputs)
        routeInputContainer.visibility = View.VISIBLE
        etOrigin.visibility = View.VISIBLE
        etDestination.visibility = View.VISIBLE
        btnRoute.visibility = View.VISIBLE
        btnPreviewRoute.visibility = View.VISIBLE

        etDestination.isEnabled = true
        etDestination.text.clear()
        etOrigin.text.clear()

        // ELIMINA esta línea ya que el botón está dentro del contenedor:
        // btnCancelRoute.visibility = View.GONE

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

        // ================= MODO PREVIEW =================
        if (previewing) {
            runOnUiThread {
                // Actualizar panel de preview (nuevo diseño simplificado)
                tvPreviewTime.text = formatTimeForPreview(durationMin)
                tvPreviewDistance.text = "%.1f km".format(distanceKm)

                // Mostrar panel de preview y ocultar otros elementos
                previewPanel.visibility = View.VISIBLE
                navigationBar.visibility = View.GONE
                tripTopBarOrange.visibility = View.GONE
                routeInputContainer.visibility = View.GONE

                // Ocultar tienda en modo preview
                updateStoreVisibility()
            }
            return
        }

        // ================= MODO NAVEGACIÓN =================
        runOnUiThread {
            tvEta.text = formatTime(distanceKm, durationMin)
            navigationBar.visibility = View.VISIBLE
        }

        // ================= TRIP STATS =================
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

            val instruction = buildInstruction(type, modifier, name)
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

        runOnUiThread {
            if (navigationSteps.isNotEmpty()) {
                val first = navigationSteps[0]
                tvInstruction.text = first.instruction
                updateTurnIcon(first.instruction)

                // Actualizar barra superior
                tvInstructionTop.text = first.instruction
                tvNextDistance.text = "${first.distance.toInt()} m"
                updateTurnIconSmall(first.instruction)
            }

            // Actualizar ETA en barra superior
            val mins = durationMin.toInt()
            tvEtaTop.text = when {
                mins > 60 -> "${mins/60}h ${mins%60}m"
                else -> "$mins min"
            }

            // Actualizar distancia total en barra superior
            tvTotalDistanceTop.text = when {
                distanceKm < 1 -> "${(distanceKm * 1000).toInt()} m"
                else -> "%.1f km".format(distanceKm)
            }
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
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${loc.longitude},${loc.latitude};" +
                    "${destinationLatLng!!.longitude},${destinationLatLng!!.latitude}" +
                    "?overview=false"

            val res = client.newCall(Request.Builder().url(url).build())
                .execute().body?.string() ?: return@launch

            val route = JSONObject(res)
                .getJSONArray("routes")
                .getJSONObject(0)

            val distanceMeters = route.getDouble("distance")
            val durationSeconds = route.getDouble("duration")

            val distanceKm = distanceMeters / 1000
            val durationMin = durationSeconds / 60

            runOnUiThread {
                // Actualizar ETA en barra superior
                val mins = durationMin.toInt()
                tvEtaTop.text = when {
                    mins > 60 -> "${mins/60}h ${mins%60}m"
                    else -> "$mins min"
                }

                // Actualizar distancia total en barra superior
                tvTotalDistanceTop.text = when {
                    distanceKm < 1 -> "${(distanceKm * 1000).toInt()} m"
                    else -> "%.1f km".format(distanceKm)
                }

                // También actualizar en barra inferior (para preview)
                tvEta.text = formatTime(distanceKm, durationMin)
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

        // Ocultar barra naranja
        tripTopBarOrange.visibility = View.GONE

        // OCULTAR CONTENEDOR DEL BOTÓN CANCELAR
        cancelButtonContainer.visibility = View.GONE

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

    private fun updateNavigationInstruction(loc: Location) {
        if (navigationSteps.isEmpty()) return
        if (currentStepIndex >= navigationSteps.size) return

        val step = navigationSteps[currentStepIndex]
        val stepLoc = Location("").apply {
            latitude = step.location.latitude
            longitude = step.location.longitude
        }

        val distance = loc.distanceTo(stepLoc).toInt()

        // Actualizar barra superior
        tvInstructionTop.text = step.instruction
        tvNextDistance.text = "$distance m"

        // Calcular tiempo aproximado basado en velocidad actual

        // Actualizar icono pequeño
        updateTurnIconSmall(step.instruction)

        // Resto del código de avisos por voz...
        // ================= AVISO PREVIO =================
        if (distance in 40..100 && !step.preAnnounced) {
            step.preAnnounced = true
            val msg = "En $distance metros, ${step.instruction}"
            speak(msg)
        }

        // ================= AVISO FINAL =================
        if (distance <= 25 && !step.finalAnnounced) {
            step.finalAnnounced = true
            val msg = "Ahora, ${step.instruction}"
            speak(msg)
        }

        // ================= PASO COMPLETADO =================
        if (distance <= 15) {
            currentStepIndex++
            if (currentStepIndex < navigationSteps.size) {
                val next = navigationSteps[currentStepIndex]
                updateTurnIconSmall(next.instruction)
            }
        }
    }

    private fun updateTurnIconSmall(instruction: String) {
        when {
            instruction.contains("izquierda", true) ->
                ivTurnIconSmall.setImageResource(R.drawable.ic_turn_left)
            instruction.contains("derecha", true) ->
                ivTurnIconSmall.setImageResource(R.drawable.ic_turn_right)
            instruction.contains("recto", true) ->
                ivTurnIconSmall.setImageResource(R.drawable.ic_straight)
            else -> ivTurnIconSmall.setImageResource(android.R.drawable.ic_menu_directions)
        }
        ivTurnIconSmall.visibility = View.VISIBLE
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

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_settings, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {

                R.id.action_dark_mode -> {
                    toggleDarkMode()
                    true
                }

                R.id.action_battery_saver -> {
                    toggleBatterySaver()
                    true
                }


                R.id.action_map_type -> {
                    showMapTypeDialog()
                    true
                }

                R.id.btnMute -> {
                    updateMuteIcon()
                    true
                }



                R.id.action_logout -> {
                    confirmLogout()
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    /* -----------------------------
       FUNCIONES DE AJUSTES
       ----------------------------- */

    private fun toggleDarkMode() {
        val nightMode = AppCompatDelegate.getDefaultNightMode()
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun toggleBatterySaver() {
        // Aquí puedes reducir frecuencia de GPS, desactivar animaciones, etc.
        Toast.makeText(this, "Modo ahorro de batería activado/desactivado", Toast.LENGTH_SHORT).show()
    }


    private fun showMapTypeDialog() {
        val opciones = arrayOf("Normal", "Terreno", "Híbrido")

        AlertDialog.Builder(this)
            .setTitle("Tipo de mapa")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> setMapType("normal")
                    1 -> setMapType("terrain")
                    2 -> setMapType("hybrid")
                }
            }
            .show()
    }


    private fun setMapType(type: String) {
        val apiKey = "bfde33420b5842fea5085295622148e9"

        val styleUrl = when (type) {
            "normal" ->
                "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=$apiKey"

            "satellite" ->
                "https://maps.geoapify.com/v1/styles/satellite/style.json?apiKey=$apiKey"

            "terrain" ->
                "https://maps.geoapify.com/v1/styles/terrain/style.json?apiKey=$apiKey"

            "hybrid" ->
                "https://maps.geoapify.com/v1/styles/satellite-hybrid/style.json?apiKey=$apiKey"

            else ->
                "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=$apiKey"
        }

        mapView.getMapAsync { map ->
            map.setStyle(styleUrl)
        }

        Toast.makeText(this, "Mapa cambiado a: $type", Toast.LENGTH_SHORT).show()
    }

    private fun updateMuteIcon() {
        AlertDialog.Builder(this)
            .setTitle("Mutear")
        if (voiceEnabled) {
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            toast("Voz activada 🔊")
        } else {
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
            toast("Voz desactivada 🔇")
        }
    }


    private fun logout() {
        // Lógica real de cerrar sesión
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
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

        val root = findViewById<ViewGroup>(android.R.id.content)

        val view = layoutInflater.inflate(
            R.layout.dialog_trip_summary,
            root,
            false
        )

        // ================= CONVERSIONES =================

        val realKm = tripResult.realDistanceMeters / 1000.0
        val realHours = tripResult.realDurationSeconds / 3600.0

        // ================= VELOCIDAD MEDIA =================

        val realSpeed =
            if (realHours > 0) realKm / realHours else 0.0

        // ================= RATIOS =================

        val greenPercent = (tripResult.greenRatio * 100).toInt()
        val penaltyPercent = (tripResult.penalty * 100).toInt()

        // ================= TEXTO =================

        view.findViewById<TextView>(R.id.tvDistance).text =
            "%.1f km".format(realKm)

        view.findViewById<TextView>(R.id.tvRealSpeed).text =
            "%.1f km/h".format(realSpeed)

        view.findViewById<TextView>(R.id.tvEfficiency).text =
            "$greenPercent%"

        view.findViewById<TextView>(R.id.tvPenalty).text =
            "-$penaltyPercent%"

        view.findViewById<TextView>(R.id.tvPoints).text =
            "+${tripResult.pointsEarned}"

        // ================= MOSTRAR OVERLAY =================

        root.addView(view)

        view.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            root.removeView(view)
        }
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

            previewing = true
            navigating = false
            destinationLatLng = dest

            withContext(Dispatchers.Main) {
                // Solo mostrar panel de preview
                previewPanel.visibility = View.VISIBLE
                routeInputContainer.visibility = View.GONE

                // Ocultar tienda
                updateStoreVisibility()

                // Ocultar otras barras
                tripTopBarOrange.visibility = View.GONE
                navigationBar.visibility = View.GONE

                btnPreviewRoute.text = "Ver ruta"
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
        navigating = false

        // Limpiar ruta
        clearRoute()
        routePoints = emptyList()
        destinationLatLng = null

        runOnUiThread {
            // Ocultar panel de preview
            previewPanel.visibility = View.GONE

            // Mostrar inputs de ruta
            routeInputContainer.visibility = View.VISIBLE

            // Restaurar visibilidad de tienda (modo normal)
            updateStoreVisibility()

            // Limpiar texto (solo tiempo y distancia, no calles)
            tvPreviewTime.text = ""
            tvPreviewDistance.text = ""
        }

        toast("Vista previa cancelada")
    }


    //======================= AUTOCOMPLETADO ===============================
    private fun setupAutocomplete(editText: AutoCompleteTextView) {
        var searchJob: Job? = null
        var autocompleteLocked = false

        // ================= CAMBIO 1: Configurar la ventana del dropdown =================
        editText.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
        editText.dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        editText.dropDownVerticalOffset = 0
        editText.dropDownHorizontalOffset = 0

        // Estilo para el dropdown (opcional, ayuda con el rendimiento)
        editText.setDropDownBackgroundDrawable(
            ContextCompat.getDrawable(this, android.R.drawable.dialog_holo_light_frame)
        )

        // ================= CAMBIO 2: TextWatcher optimizado =================
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (before > 0 || count > 0) {
                    autocompleteLocked = false
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (!editText.isFocused || autocompleteLocked) return

                val query = s.toString().trim()
                if (query.length < 3) {
                    editText.dismissDropDown()
                    return
                }

                searchJob?.cancel()
                searchJob = lifecycleScope.launch(Dispatchers.IO) {
                    delay(400) // Aumentar debounce a 400ms

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
                            // CAMBIO 3: Verificar que el editText aún esté enfocado
                            if (!editText.isFocused || autocompleteLocked) return@withContext

                            // ADAPTADOR CON COLORES PERSONALIZADOS (SOLO CAMBIO)
                            val adapter = object : ArrayAdapter<String>(
                                this@MainActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                android.R.id.text1,
                                suggestions
                            ) {
                                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getView(position, convertView, parent)
                                    val textView = view.findViewById<TextView>(android.R.id.text1)
                                    textView.setTextColor(Color.BLACK)  // Texto negro
                                    textView.setBackgroundColor(Color.WHITE)  // Fondo blanco
                                    return view
                                }

                                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getDropDownView(position, convertView, parent)
                                    val textView = view.findViewById<TextView>(android.R.id.text1)
                                    textView.setTextColor(Color.BLACK)  // Texto negro en dropdown
                                    textView.setBackgroundColor(Color.WHITE)  // Fondo blanco en dropdown
                                    return view
                                }
                            }

                            editText.setAdapter(adapter)

                            // Mostrar dropdown solo si hay sugerencias
                            if (suggestions.isNotEmpty() && !autocompleteLocked && editText.isFocused) {
                                editText.showDropDown()
                            }
                        }

                    } catch (_: Exception) {}
                }
            }
        })

        // ================= CAMBIO 4: Manejo de selección mejorado =================
        editText.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as String

            autocompleteLocked = true
            editText.setText(selected)
            editText.setSelection(selected.length)

            // CAMBIO: Ocultar dropdown inmediatamente
            editText.dismissDropDown()

            // CAMBIO: Pequeño delay antes de quitar el foco y ocultar teclado
            editText.postDelayed({
                editText.clearFocus()
                hideKeyboard(editText)
            }, 50) // 50ms de delay
        }

        // ================= CAMBIO 5: EditorActionListener optimizado =================
        editText.setOnEditorActionListener { _, _, _ ->
            autocompleteLocked = true
            editText.dismissDropDown()

            // Pequeño delay
            editText.postDelayed({
                editText.clearFocus()
                hideKeyboard(editText)
            }, 50)
            true
        }

        // ================= CAMBIO 6: FocusChangeListener mejorado =================
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                editText.dismissDropDown()
                // No ocultamos el teclado aquí para evitar conflictos
            }
        }

        // ================= CAMBIO 7: ClickListener simplificado =================
        editText.setOnClickListener {
            autocompleteLocked = false
            // No pedir focus automáticamente, dejar que el usuario lo haga
        }
    }



    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    //================================ TIENDA =================================
    private fun updateStoreVisibility() {
        val shouldShow = !navigating && !previewing
        findViewById<FrameLayout>(R.id.storeButtonContainer)?.visibility =
            if (shouldShow) View.VISIBLE else View.GONE
        btnStore.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }


    // =========================== YA VERÉ DONDEMETERLA============================
    private fun calculateNextStepTime(distanceMeters: Int, currentSpeedKmh: Float): String {
        if (currentSpeedKmh < 5) {
            // Si vamos muy lento, asumir 5 km/h para cálculo
            val timeMinutes = distanceMeters / (5000.0 / 60.0) // 5 km/h en m/min
            return if (timeMinutes < 1) "${(timeMinutes * 60).toInt()} seg" else "${timeMinutes.toInt()} min"
        }

        val speedMps = currentSpeedKmh / 3.6
        val timeSeconds = distanceMeters / speedMps

        return when {
            timeSeconds < 60 -> "${timeSeconds.toInt()} seg"
            else -> "${(timeSeconds / 60).toInt()} min"
        }
    }


    private fun formatTimeForPreview(minutes: Double): String {
        val mins = minutes.toInt()
        return when {
            mins >= 60 -> "${mins/60}h ${mins%60}m"
            else -> "$mins min"
        }
    }


    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
