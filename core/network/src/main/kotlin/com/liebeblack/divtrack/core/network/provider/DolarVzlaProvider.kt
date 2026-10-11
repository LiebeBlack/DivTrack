package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.mapper.IsoParsers
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.service.DolarVzlaService
import javax.inject.Inject

/**
 * Respaldo del **oficial**: lee el cierre del BCV desde un CDN de archivos estáticos.
 *
 * Existe para el fallo más concreto del proyecto: el oficial de DolarAPI depende de que su
 * espejo de bcv.org.ve siga refrescando, y cuando deja de hacerlo la app servía un cierre
 * viejo con aspecto de actual. Este proveedor publica el mismo dato pero por otra ruta
 * (BCV → generador del sitio → CDN, sin API intermedia), así que basta con que traiga una
 * fecha más reciente para que la resolución por frescura del registro lo imponga.
 *
 * Prioridad 30 (último): DolarAPI y ExchangeRate-API aportan oficial con hora exacta y,
 * sobre todo, tienen también el paralelo; este solo aporta oficial, así que se consulta
 * cuando la cola llega hasta el final — que es justo cuando el oficial no llegó fresco.
 *
 * Contribución esperada: `oficial`. `rates` vacío si el JSON vino sin bloque `current` o
 * con un `usd` inutilizable: el registro lo cuenta como fallo de este proveedor.
 */
class DolarVzlaProvider @Inject constructor(
    private val service: DolarVzlaService,
    private val timeProvider: TimeProvider,
) : RateProvider {

    override val id: String = PROVIDER_ID
    override val priority: Int = PRIORITY

    override suspend fun fetchLatest(): RemoteRateSet {
        val current = service.getCurrentBcvRate().current
        val value = current?.usd

        val rates = buildList {
            if (value != null && value > 0.0) {
                add(
                    RemoteRate(
                        sourceKey = SourceKeys.OFICIAL,
                        value = value,
                        // El CDN publica solo la fecha del cierre (`YYYY-MM-DD`, sin hora):
                        // `IsoParsers` la lee como medianoche de Venezuela, que es la misma
                        // convención con la que DolarAPI fecha el oficial, de modo que la
                        // comparación por frescura es entre iguales. Una fecha ilegible da
                        // `null` (edad desconocida) en vez de descartar la tasa.
                        updatedAtMillis = IsoParsers.instantMillisOrNull(current?.date),
                    ),
                )
            }
        }

        return RemoteRateSet(
            providerId = id,
            rates = rates,
            fetchedAtMillis = timeProvider.nowMillis(),
        )
    }

    companion object {
        const val PROVIDER_ID: String = "DolarVZLA"
        const val PRIORITY: Int = 30
    }
}
