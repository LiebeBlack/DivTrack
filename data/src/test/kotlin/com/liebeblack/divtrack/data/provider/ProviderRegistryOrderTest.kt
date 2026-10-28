package com.liebeblack.divtrack.data.provider

import com.liebeblack.divtrack.core.common.logging.NoOpLogger
import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.data.fake.FakeRateProvider
import com.liebeblack.divtrack.data.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El registro decide QUÉ proveedor se consulta y en QUÉ orden: aquí se fijan las reglas
 * de orden (automático y con preferencia del usuario) y el diagnóstico de fuentes.
 */
class ProviderRegistryOrderTest {

    private fun registry(vararg providers: FakeRateProvider) = ProviderRegistry(
        providers = providers.toSet(),
        errorMapper = NetworkErrorMapper(),
        logger = NoOpLogger,
        timeProvider = FakeTimeProvider(),
    )

    private val dolarApi = FakeRateProvider(
        id = ProviderIds.DOLARAPI,
        priority = 0,
        rates = listOf(rate(SourceKeys.OFICIAL, 860.18), rate(SourceKeys.PARALELO, 955.71)),
    )

    private val yadio = FakeRateProvider(
        id = ProviderIds.YADIO,
        priority = 10,
        rates = listOf(rate(SourceKeys.PARALELO, 954.55)),
    )

    private val exchangeRateApi = FakeRateProvider(
        id = ProviderIds.EXCHANGERATEAPI,
        priority = 20,
        rates = listOf(rate(SourceKeys.OFICIAL, 860.18)),
    )

    @Test
    fun `sin preferencia respeta el orden por prioridad`() = runTest {
        val outcome = registry(dolarApi, yadio, exchangeRateApi).fetchLatest()

        assertEquals(2, outcome.rates.size)
        assertEquals(ProviderIds.DOLARAPI, outcome.providerIds.first())
    }

    @Test
    fun `la preferencia del usuario se consulta primero aunque tenga prioridad baja`() = runTest {
        val outcome = registry(dolarApi, yadio, exchangeRateApi)
            .fetchLatest(preferredProviderId = ProviderIds.EXCHANGERATEAPI)

        assertEquals(2, outcome.rates.size)
        assertEquals(ProviderIds.EXCHANGERATEAPI, outcome.providerIds.first())
    }

    @Test
    fun `con el preferido completo el resto de proveedores no se consulta`() = runTest {
        val completa = FakeRateProvider(
            id = ProviderIds.EXCHANGERATEAPI,
            priority = 20,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 860.18),
                // El preferido también puede traer el paralelo si lo publica.
                rate(SourceKeys.PARALELO, 955.71),
            ),
        )

        val outcome = registry(completa, dolarApi, yadio).fetchLatest(preferredProviderId = ProviderIds.EXCHANGERATEAPI)

        assertEquals(2, outcome.rates.size)
        assertEquals(0, dolarApi.fetchLatestCalls)
        assertEquals(0, yadio.fetchLatestCalls)
    }

    @Test
    fun `una preferencia desconocida cae al orden automatico`() = runTest {
        val outcome = registry(dolarApi, yadio, exchangeRateApi)
            .fetchLatest(preferredProviderId = "ProveedorFantasma")

        assertEquals(2, outcome.rates.size)
        assertEquals(ProviderIds.DOLARAPI, outcome.providerIds.first())
    }

    @Test
    fun `una preferencia nula es orden automatico`() = runTest {
        val outcome = registry(dolarApi, yadio, exchangeRateApi).fetchLatest(preferredProviderId = null)

        assertEquals(2, outcome.rates.size)
        assertEquals(ProviderIds.DOLARAPI, outcome.providerIds.first())
    }

    @Test
    fun `testAll reporta ok con las tasas que trae cada fuente`() = runTest {
        val diagnostics = registry(dolarApi, yadio, exchangeRateApi).testAll()

        assertTrue(diagnostics.allOk)
        assertEquals(3, diagnostics.total)
        val dolarApiStatus = diagnostics.statuses.single { it.providerId == ProviderIds.DOLARAPI }
        assertEquals(listOf(SourceKeys.OFICIAL, SourceKeys.PARALELO), dolarApiStatus.sources)
    }

    @Test
    fun `testAll marca caida la fuente que no responde`() = runTest {
        val caida = FakeRateProvider(id = ProviderIds.YADIO, priority = 10, failing = true)

        val diagnostics = registry(dolarApi, caida, exchangeRateApi).testAll()

        assertTrue(!diagnostics.allOk)
        assertEquals(2, diagnostics.reachableCount)
        val yadioStatus = diagnostics.statuses.single { it.providerId == ProviderIds.YADIO }
        assertTrue(!yadioStatus.isOk)
        assertEquals(ProviderIds.YADIO, yadioStatus.error?.providerId)
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
