package com.example.driveup.navigation

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private val MIN_UPDATE_INTERVAL = 15_000L // 15 segundos

/**
 * Manager que mantiene el límite de velocidad actual
 * en memoria y lo actualiza en background.
 */
class SpeedLimitManager(private val provider: SpeedLimitProvider
) {

    private var lastUpdateTime = 0L


    // Cache thread-safe del último límite conocido
    private val cachedLimit = AtomicInteger(50)


    /**
     * Actualiza el límite según la posición actual
     *
     * Se ejecuta en background para no bloquear UI
     */
    fun update(
        scope: CoroutineScope,
        location: Location
    ) {

        val now = System.currentTimeMillis()

        // 👉 Throttling: evitamos llamar demasiado
        if (now - lastUpdateTime < MIN_UPDATE_INTERVAL) {
            return
        }

        lastUpdateTime = now


        scope.launch(Dispatchers.IO) {

            val limit = provider.getSpeedLimit(location)

            cachedLimit.set(limit)
        }
    }



    /**
     * Devuelve el último límite conocido
     */
    fun getCurrentLimit(): Int {
        return cachedLimit.get()
    }
}
