package com.liebeblack.divtrack.data.provider

import com.liebeblack.divtrack.core.common.logging.NoOpLogger
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.data.fake.FakeRateProvider
import com.liebeblack.divtrack.data.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    private fun registry(vararg providers: FakeRateProvider) = ProviderRegistry(
        providers = providers.toSet(),
        errorMapper = NetworkErrorMapper(),
        logger = NoOpLogger,
        // Reloj fijo explícito: el registro no depende del reloj real ni siquiera por defecto.
        timeProvider = FakeTimeProvider(),
    )

    @Test
    fun `gana el proveedor con mejor prioridad para cada tasa`() = runTest {
        val preferred = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
        )
        val fallback = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 900.0),
                rate(SourceKeys.PARALELO, 954.55),
            ),
        )

        val outcome = registry(preferred, fallback).fetchLatest()

        assertEquals(2, outcome.rates.size)
        val oficial = outcome.rates.first { it.rate.sourceKey == SourceKeys.OFICIAL }
        assertEquals(859.06, oficial.rate.value, DELTA)
        // La procedencia viaja con la tasa: sin esto la UI no puede decir de dónde salió.
        assertEquals("DolarAPI", oficial.providerId)
        assertEquals(listOf("DolarAPI", "Yadio"), outcome.providerIds)
    }

    @Test
    fun `no consulta al resto cuando ya tiene todas las tasas`() = runTest {
        val complete = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 859.06),
                rate(SourceKeys.PARALELO, 954.55),
            ),
        )
        val never = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(rate(SourceKeys.PARALELO, 950.0)),
        )

        val outcome = registry(complete, never).fetchLatest()

        assertEquals(2, outcome.rates.size)
        assertEquals(0, never.fetchLatestCalls)
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun `los fallos se acumulan sin descartar las tasas disponibles`() = runTest {
        val broken = FakeRateProvider(id = "DolarAPI", priority = 0, failing = true)
        val working = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(rate(SourceKeys.PARALELO, 954.55)),
        )

        val outcome = registry(broken, working).fetchLatest()

        assertEquals(1, outcome.rates.size)
        assertEquals(1, outcome.failures.size)
        assertEquals("DolarAPI", outcome.failures.single().providerId)
    }

    private fun rate(sourceKey: String, value: Double) = RemoteRate(
        sourceKey = sourceKey,
        value = value,
        updatedAtMillis = null,
    )

    private companion object {
        const val DELTA = 0.000001
    }
}
