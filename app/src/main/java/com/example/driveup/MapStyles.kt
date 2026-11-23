package com.example.driveup

import android.content.Context
import org.maplibre.android.maps.Style

/**
 * Tipos de mapa disponibles. Todos son gratuitos y sin clave:
 *  - Estándar: OpenFreeMap (claro u oscuro según el tema)
 *  - Híbrido: imágenes de Esri World Imagery con calles y etiquetas encima
 *  - Relieve: OpenTopoMap
 */
enum class MapType(val key: String, val label: String, val iconRes: Int) {
    STANDARD("standard", "Estándar", R.drawable.ic_map_standard),
    HYBRID("hybrid", "Híbrido", R.drawable.ic_map_hybrid),
    TERRAIN("terrain", "Relieve", R.drawable.ic_map_terrain);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

object MapStyles {
    private const val PREFS = "driveup_prefs"
    private const val KEY_MAP_TYPE = "map_type"

    fun saved(context: Context): MapType =
        MapType.fromKey(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAP_TYPE, null)
        )

    fun save(context: Context, type: MapType) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MAP_TYPE, type.key).apply()
    }

    fun builder(context: Context, type: MapType): Style.Builder = when (type) {
        MapType.STANDARD -> Style.Builder().fromUri(
            if (ThemePrefs.isNight(context)) "https://tiles.openfreemap.org/styles/dark"
            else "https://tiles.openfreemap.org/styles/liberty"
        )
        MapType.HYBRID -> Style.Builder().fromJson(rasterStyle(listOf(ESRI_IMAGERY, ESRI_TRANSPORT, ESRI_LABELS)))
        MapType.TERRAIN -> Style.Builder().fromJson(rasterStyle(listOf(OPEN_TOPO), maxZoom = 17))
    }

    private data class Raster(val id: String, val url: String, val attribution: String)

    private val ESRI_IMAGERY = Raster(
        "esri-imagery",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "© Esri, Maxar, Earthstar Geographics"
    )
    private val ESRI_TRANSPORT = Raster(
        "esri-transport",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}",
        "© Esri"
    )
    private val ESRI_LABELS = Raster(
        "esri-labels",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
        "© Esri"
    )
    private val OPEN_TOPO = Raster(
        "opentopo",
        "https://tile.opentopomap.org/{z}/{x}/{y}.png",
        "© OpenTopoMap (CC-BY-SA), © OpenStreetMap"
    )

    /** Estilo MapLibre mínimo con una o varias capas raster apiladas. */
    private fun rasterStyle(layers: List<Raster>, maxZoom: Int = 19): String {
        val sources = layers.joinToString(",") {
            """"${it.id}":{"type":"raster","tiles":["${it.url}"],"tileSize":256,"maxzoom":$maxZoom,"attribution":"${it.attribution}"}"""
        }
        val styleLayers = layers.joinToString(",") {
            """{"id":"${it.id}","type":"raster","source":"${it.id}"}"""
        }
        return """{"version":8,"sources":{$sources},"layers":[$styleLayers]}"""
    }
}
