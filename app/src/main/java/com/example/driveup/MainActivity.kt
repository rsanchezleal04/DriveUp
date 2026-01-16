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

    private lateinit var tvSpeed: TextView



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

                // Si el usuario mueve el mapa → modo libre
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

        btnCenter.setOnClickListener {
            centerToUserLocation()
        }

        btnMute.setOnClickListener {
            voiceEnabled = !voiceEnabled
            updateMuteIcon()
        }

        // ================= TTS =================
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale("es", "ES")
            }
        }

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        btnSettings.setOnClickListener {
            showSettingsMenu(it)
        }

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
            lc.cameraMode = CameraMode.TRACKING
            lc.renderMode = RenderMode.COMPASS

            startLocationUpdates()
        }
    }

    // ================= UBICACIÓN =================

    private fun startLocationUpdates() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLocation = loc

                val speed = speedManager.update(loc)
                tvSpeed.text = speed.toString()

                zoomToLocationOnce(loc)

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
            .target(LatLng(location.latitude, location.longitude))
            .zoom(17.0)
            .tilt(45.0)
            .bearing(location.bearing.toDouble())
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            1200
        )

        mapView.postDelayed({
            if (followUser) {
                map.locationComponent.cameraMode = CameraMode.TRACKING
            }
        }, 1300)

    }

    // ================= BOTÓN CENTRAR =================

    private fun centerToUserLocation() {
        val loc = currentLocation ?: return

        followUser = true

        val position = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(17.0)
            .tilt(45.0)
            .bearing(loc.bearing.toDouble())
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            1000
        )

        mapView.postDelayed({
            if (followUser) {
                map.locationComponent.cameraMode = CameraMode.TRACKING
            }
        }, 1100)
    }




    // ================= RUTA =================

    private fun calculateRoute() {
        val destText = etDestination.text.toString().trim()
        val originText = etOrigin.text.toString().trim()

        if (destText.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {

            val originLatLng: LatLng = if (originText.isEmpty()) {
                val loc = currentLocation ?: return@launch
                LatLng(loc.latitude, loc.longitude)
            } else {
                geocode(originText) ?: return@launch
            }

            val dest = geocode(destText) ?: return@launch

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

    private fun drawRoute(json: String) {
        if (!mapReady) return

        val route = JSONObject(json)
            .getJSONArray("routes")
            .getJSONObject(0)

        // ================= DISTANCIA / ETA =================

        val distanceKm = route.getDouble("distance") / 1000
        val durationMin = route.getDouble("duration") / 60

        tvEta.text = formatTime(distanceKm, durationMin)
        navigationBar.visibility = LinearLayout.VISIBLE
        navigating = true
        arrived = false

        // ================= GEOMETRÍA RUTA =================

        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = mutableListOf<Point>()
        val latLngs = mutableListOf<LatLng>()

        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            points.add(Point.fromLngLat(c.getDouble(0), c.getDouble(1)))
            latLngs.add(LatLng(c.getDouble(1), c.getDouble(0)))
        }

        routePoints = latLngs

        map.getStyle { style ->
            if (style.getSource("route") == null) {
                style.addSource(
                    GeoJsonSource("route", LineString.fromLngLats(points))
                )
                style.addLayer(
                    LineLayer("route-layer", "route").withProperties(
                        lineColor("#2196F3"),
                        lineWidth(6f)
                    )
                )
            } else {
                (style.getSource("route") as GeoJsonSource)
                    .setGeoJson(LineString.fromLngLats(points))
            }
        }

        // ================= INSTRUCCIONES DE GIRO =================

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
            val latLng = LatLng(loc.getDouble(1), loc.getDouble(0))

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

        if (isRecalculating && currentLocation != null) {
            alignStepWithCurrentLocation(currentLocation!!)
            isRecalculating = false
        }

        if (navigationSteps.isNotEmpty()) {
            tvInstruction.text = navigationSteps[0].instruction
            updateTurnIcon(navigationSteps[0].instruction)

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
            if (d < minDistance) minDistance = d
        }

        if (minDistance > 50 && System.currentTimeMillis() - lastRecalcTime > 8000) {
            lastRecalcTime = System.currentTimeMillis()
            isRecalculating = true

            val current = LatLng(loc.latitude, loc.longitude)
            val dest = destinationLatLng ?: return

            lifecycleScope.launch(Dispatchers.IO) {

                // Limpiar completamente estado anterior
                runOnUiThread {
                    clearRoute()
                    navigationSteps = emptyList()
                    currentStepIndex = 0
                    arrived = false
                    navigating = false
                    tvInstruction.text = "Recalculando ruta…"
                }

                // Pedir una ruta NUEVA desde tu posición real
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

        toast("Has llegado a tu destino 🚗")

        clearRoute()

        // UI
        navigationBar.visibility = LinearLayout.GONE

        // Cámara
        map.locationComponent.cameraMode = CameraMode.TRACKING

        navigationSteps = emptyList()
        currentStepIndex = 0
        tvInstruction.text = ""


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

            "depart" -> "🚗 Comienza la ruta"
            "arrive" -> "🏁 Has llegado a tu destino"

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




    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
