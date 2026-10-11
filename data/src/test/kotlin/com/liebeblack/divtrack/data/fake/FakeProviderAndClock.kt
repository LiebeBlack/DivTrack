package com.liebeblack.divtrack.data.fake

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.provider.RateProvider
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** Reloj fijo: el "cierre anterior" deja de depender del día en que se ejecuten los tests. */
class FakeTimeProvider(
    var today: LocalDate = LocalDate.of(2026, 10, 1),
    var nowMillis: Long = 1_800_000_000_000L,
) : TimeProvider {

    override fun nowMillis(): Long = nowMillis

    override fun nowInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(nowMillis)

    override fun today(): LocalDate = today

    override fun zone(): ZoneId = ZoneId.of("America/Caracas")
}

/**
 * Proveedor remoto simulado.
 *
 * @param rates tasas que devuelve en [fetchLatest]; si [failing] es true, lanza en su lugar.
 * @param authoritativeSources fuentes para las que el fake se comporta como la referencia
 *   (por ejemplo, el oficial de DolarAPI, que no se deja pisar por nadie: ADR 40).
 */
class FakeRateProvider(
    override val id: String,
    override val priority: Int,
    private val rates: List<RemoteRate> = emptyList(),
    private val failing: Boolean = false,
    private val fetchedAtMillis: Long = 1_800_000_000_000L,
    override val authoritativeSources: Set<String> = emptySet(),
) : RateProvider {

    var fetchLatestCalls: Int = 0
        private set

    override suspend fun fetchLatest(): RemoteRateSet {
        fetchLatestCalls++
        if (failing) throw IOException("proveedor $id sin conexión")
        return RemoteRateSet(
            providerId = id,
            rates = rates,
            fetchedAtMillis = fetchedAtMillis,
        )
    }
}
