package com.example.driveup.navigation

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


/**
 * Proveedor de límites de velocidad usando OpenStreetMap (Overpass API)
 *
 * Hace una consulta alrededor de la posición actual
 * y busca la etiqueta "maxspeed" en las carreteras cercanas.
 */
class OsmSpeedLimitProvider : SpeedLimitProvider {

    // Cliente HTTP para llamadas a Overpass
    private val client = OkHttpClient()


    /**
     * Obtiene el límite de velocidad en la ubicación actual
     */
    override suspend fun getSpeedLimit(location: Location): Int =
        withContext(Dispatchers.IO) {

            try {

                // Coordenadas actuales
                val lat = location.latitude
                val lon = location.longitude


                /*
                 * Query Overpass:
                 *
                 * - Busca "ways" (carreteras)
                 * - En un radio de 20 metros
                 * - Que tengan tag "highway"
                 */
                val query = """
                [out:json];
                way(around:20,$lat,$lon)["highway"];
                out tags;
                """.trimIndent()


                // URL completa codificada
                val url =
                    "https://overpass-api.de/api/interpreter?data=" +
                            query.replace("\n", "")


                // Petición HTTP
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "DriveUp") // importante para Overpass
                    .build()


                // Ejecutamos llamada
                val res = client
                    .newCall(req)
                    .execute()
                    .body
                    ?.string()
                    ?: return@withContext defaultLimit()


                // Parseamos JSON
                val json = JSONObject(res)

                val elements =
                    json.optJSONArray("elements")
                        ?: return@withContext defaultLimit()


                // Recorremos carreteras encontradas

                    var fallbackHighway: String? = null


                    for (i in 0 until elements.length()) {

                        val tags = elements
                            .getJSONObject(i)
                            .optJSONObject("tags")
                            ?: continue


                        // ================= MAXSPEED REAL =================

                        if (tags.has("maxspeed")) {

                            val raw = tags.getString("maxspeed")

                            val speed = raw
                                .replace("km/h", "")
                                .replace("mph", "")
                                .trim()
                                .toIntOrNull()

                            if (speed != null) {
                                return@withContext speed
                            }
                        }


                        // ================= GUARDAMOS HIGHWAY =================

                        if (fallbackHighway == null && tags.has("highway")) {

                            fallbackHighway = tags.getString("highway")
                        }
                    }


// ================= FALLBACK POR TIPO =================

                    fallbackHighway?.let {

                        return@withContext highwayToDefault(it)
                    }


                    // Nada encontrado
                    defaultLimit()




                // Si no se encontró nada → fallback
                defaultLimit()

            } catch (e: Exception) {

                // Cualquier error → fallback seguro
                defaultLimit()
            }
        }


    /**
     * Valores por defecto según tipo de vía
     */
    private fun highwayToDefault(type: String): Int {

        return when (type.lowercase()) {

            "motorway" -> 120
            "motorway_link" -> 80

            "trunk" -> 100
            "trunk_link" -> 80

            "primary" -> 90
            "primary_link" -> 70

            "secondary" -> 80
            "secondary_link" -> 60

            "tertiary" -> 60
            "tertiary_link" -> 50

            "residential" -> 30
            "living_street" -> 20

            "service" -> 20
            "unclassified" -> 50

            else -> defaultLimit()
        }
    }

    /**
     * Límite por defecto si OSM falla
     */
    private fun defaultLimit(): Int = 50
}
