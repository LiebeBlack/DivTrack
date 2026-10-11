package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.service.YadioService
import javax.inject.Inject

/**
 * Proveedor de respaldo: publica únicamente el paralelo (es la fuente upstream de esa tasa),
 * así que si DolarAPI cae, el paralelo sigue llegando y solo se degrada el oficial.
 *
 * Es también el único proveedor que sirve el paralelo cuando DolarAPI está bloqueado desde la
 * red del usuario, que es el caso real más frecuente en Venezuela. Su `timestamp` viaja al
 * registro como marca de tiempo del dato, de modo que la resolución por frescura y el aviso de
 * "fuente que dejó de publicar" funcionan igual con Yadio que con DolarAPI.
 */
class YadioProvider @Inject constructor(
    private val service: YadioService,
    private val timeProvider: TimeProvider,
) : RateProvider {

    override val id: String = PROVIDER_ID
    override val priority: Int = PRIORITY

    override suspend fun fetchLatest(): RemoteRateSet {
        val dto = service.getRates()
        val vesValue = dto.ves

        val rates = buildList {
            if (vesValue != null && vesValue > 0.0) {
                add(
                    RemoteRate(
                        sourceKey = SourceKeys.PARALELO,
                        value = vesValue,
                        // Yadio sí publica la hora de su cotización (`timestamp`, en ms): sin
                        // ella, su lectura entraba en el registro como "sin marca de tiempo" y
                        // no podía competir por frescura con DolarAPI. Si algún día dejara de
                        // mandarla, vuelve a `null` y el registro lo trata como desconocida.
                        updatedAtMillis = dto.updatedAtMillis,
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
        const val PROVIDER_ID: String = "Yadio"
        const val PRIORITY: Int = 10
    }
}
