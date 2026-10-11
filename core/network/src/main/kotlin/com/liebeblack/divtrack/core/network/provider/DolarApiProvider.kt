package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.network.mapper.IsoParsers
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.service.DolarApiService
import javax.inject.Inject

/** Proveedor primario: entrega oficial y paralelo en una sola llamada. */
class DolarApiProvider @Inject constructor(
    private val service: DolarApiService,
    private val timeProvider: TimeProvider,
) : RateProvider {

    override val id: String = PROVIDER_ID
    override val priority: Int = PRIORITY

    override suspend fun fetchLatest(): RemoteRateSet {
        val rates = service.getDollars().mapNotNull { dto ->
            val value = dto.usableValue ?: return@mapNotNull null
            if (value <= 0.0) return@mapNotNull null
            // Sin clave de fuente no se puede decir a qué tasa pertenece el valor: se descarta
            // ese elemento, y no la respuesta completa (que es lo que pasaría si el DTO
            // exigiera el campo: la app se quedaría sin ninguna tasa por un elemento raro).
            val sourceKey = dto.fuente?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            RemoteRate(
                sourceKey = sourceKey,
                value = value,
                updatedAtMillis = IsoParsers.instantMillisOrNull(dto.fechaActualizacion),
            )
        }

        return RemoteRateSet(
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
