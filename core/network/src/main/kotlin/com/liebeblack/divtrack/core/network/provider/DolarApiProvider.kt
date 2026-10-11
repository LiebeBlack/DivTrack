package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.network.dto.DolarApiRateDto
import com.liebeblack.divtrack.core.network.mapper.IsoParsers
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.service.DolarApiService
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Proveedor primario: entrega oficial y paralelo.
 *
 * El **oficial** lo pide al endpoint dedicado (`v1/dolares/oficial`), que es el producto
 * de esta app; el **paralelo** a la lista (`v1/dolares`), que es donde ese dato tiene
 * endpoint. Las dos llamadas van en paralelo y se respaldan entre sí: si una falla, la
 * otra sigue entregando su tasa, y solo cuando fallan las dos se registra la caída del
 * proveedor (con el error del oficial, que es el más explicativo).
 */
class DolarApiProvider @Inject constructor(
    private val service: DolarApiService,
    private val timeProvider: TimeProvider,
) : RateProvider {

    override val id: String = PROVIDER_ID
    override val priority: Int = PRIORITY

    override suspend fun fetchLatest(): RemoteRateSet = coroutineScope {
        // En paralelo, contra el mismo host: el techo de tiempo del proveedor sigue siendo el
        // de UNA petición, no la suma de las dos. Ninguna lanza hacia acá (devuelven `Result`),
        // así que una no puede tumbar a la otra dentro del scope.
        val officialDeferred = async { fetchingPreservingCancellation { service.getOfficial() } }
        val listDeferred = async { fetchingPreservingCancellation { service.getDollars() } }

        val official = officialDeferred.await()
        val list = listDeferred.await()

        // Sin ninguna de las dos no hay tasa que traer: se propaga la del oficial, que es la
        // más explicativa, y el registro la cuenta como caída de este proveedor.
        if (official.isFailure && list.isFailure) {
            throw official.exceptionOrNull() ?: IllegalStateException("DolarAPI no devolvió nada")
        }

        val rates = buildList {
            val publishedKeys = mutableSetOf<String>()

            // 1) El oficial, del endpoint dedicado cuando respondió. Si falló (o vino algo
            //    inutilizable), la lista de abajo lo respalda.
            official.getOrNull()?.toRemoteRate()?.let { rate ->
                if (publishedKeys.add(rate.sourceKey)) add(rate)
            }

            // 2) Todo lo que traiga la lista: el paralelo, y el oficial de respaldo. Un mismo
            //    sourceKey no se publica dos veces — gana el dedicado, que es el que se pidió
            //    a propósito, y el registro compite por frescura contra otros proveedores.
            list.getOrElse { emptyList() }.forEach { dto ->
                val rate = dto.toRemoteRate() ?: return@forEach
                if (publishedKeys.add(rate.sourceKey)) add(rate)
            }
        }

        RemoteRateSet(
            providerId = id,
            rates = rates,
            fetchedAtMillis = timeProvider.nowMillis(),
        )
    }

    companion object {
        const val PROVIDER_ID: String = "DolarAPI"
        const val PRIORITY: Int = 0
    }
}

/**
 * `runCatching` a mano: el de la librería estándar se traga la cancelación de la corrutina,
 * y rompería la cancelación estructural de la pasada. Aquí la cancelación se propaga y solo
 * los fallos reales terminan en `Result.failure`.
 */
private suspend fun <T> fetchingPreservingCancellation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

/**
 * DTO → tasa del registro, con las defensas de siempre: un valor inutilizable o una clave
 * de fuente ausente descartan **ese elemento**, no la respuesta entera (si el DTO exigiera
 * `fuente`, un elemento raro tumbaría la llamada entera y la app se quedaría sin el oficial).
 */
private fun DolarApiRateDto.toRemoteRate(): RemoteRate? {
    val value = usableValue?.takeIf { candidate -> candidate > 0.0 } ?: return null
    val sourceKey = fuente?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null

    return RemoteRate(
        sourceKey = sourceKey,
        value = value,
        updatedAtMillis = IsoParsers.instantMillisOrNull(fechaActualizacion),
    )
}
