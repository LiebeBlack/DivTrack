package com.liebeblack.divtrack.data.repository

import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.logging.NoOpLogger
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.database.DatabaseRebuildTracker
import com.liebeblack.divtrack.core.database.entity.RateHistoryEntity
import com.liebeblack.divtrack.core.datastore.model.UserPreferences
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.data.fake.FakeConnectivityObserver
import com.liebeblack.divtrack.data.fake.FakeRateLocalDataSource
import com.liebeblack.divtrack.data.fake.FakeRateProvider
import com.liebeblack.divtrack.data.fake.FakeTimeProvider
import com.liebeblack.divtrack.data.fake.FakeUserPreferencesDataSource
import com.liebeblack.divtrack.data.provider.ProviderRegistry
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.SyncSummary
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estas pruebas fijan las cuatro promesas del patrón Online-First que sostiene toda la app:
 *
 *  1. La red nunca es la fuente de verdad: escribe en Room y Room re-emite a la UI.
 *  2. Un fallo de red no destruye los datos que el usuario ya tenía.
 *  3. La resolución es por tasa, no por proveedor: que una API caiga no deja la app vacía.
 *  4. Sin conectividad se falla al instante: ni timeout ni peticiones inútiles.
 *  5. Un fallo de almacenamiento se cuenta, no se propaga: la sincronización termina como
 *     `Result.Error` de tipo `Storage` y lo que ya estaba guardado queda intacto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateRepositoryImplTest {

    private val localDataSource = FakeRateLocalDataSource()
    private val preferences = FakeUserPreferencesDataSource()
    private val clock = FakeTimeProvider(today = LocalDate.of(2026, 10, 1))
    private val connectivity = FakeConnectivityObserver()
    private val rebuildTracker = DatabaseRebuildTracker()

    private fun repository(vararg providers: FakeRateProvider) = RateRepositoryImpl(
        providerRegistry = ProviderRegistry(
            providers = providers.toSet(),
            errorMapper = NetworkErrorMapper(),
            logger = NoOpLogger,
            // El mismo reloj fijo que usa el repositorio: nada depende de la hora real.
            timeProvider = clock,
        ),
        localDataSource = localDataSource,
        databaseRebuildTracker = rebuildTracker,
        preferencesDataSource = preferences,
        timeProvider = clock,
        logger = NoOpLogger,
        connectivityObserver = connectivity,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `completa las dos tasas aunque un proveedor falle`() = runTest {
        // El proveedor preferido se cae entero. Su relevo tiene que traer las dos tasas:
        // que una API desaparezca no puede dejar al usuario a medias.
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
                failing = true,
            ),
            FakeRateProvider(
                id = "Yadio",
                priority = 10,
                rates = listOf(
                    rate(SourceKeys.OFICIAL, 860.0),
                    rate(SourceKeys.PARALELO, 954.55),
                ),
            ),
        )

        val result = repository.refreshRates()

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success<SyncSummary>).data
        assertEquals(listOf(RateSource.OFICIAL, RateSource.PARALELO), summary.updatedSources)
        assertEquals(1, summary.failures.size)
        assertTrue(summary.isPartial)
        assertEquals(2, localDataSource.currentRates.value.size)
    }

    @Test
    fun `si todo falla devuelve error y no toca los datos guardados`() = runTest {
        val repository = repository(
            FakeRateProvider(id = "DolarAPI", priority = 0, failing = true),
            FakeRateProvider(id = "Yadio", priority = 10, failing = true),
        )
        // Primero una sincronización correcta para dejar caché...
        val healthy = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )
        healthy.refreshRates()
        val before = localDataSource.currentRates.value

        val result = repository.refreshRates()

        assertTrue(result is Result.Error)
        assertEquals(before, localDataSource.currentRates.value)
        assertEquals(1, localDataSource.commitRateSyncCalls)
    }

    @Test
    fun `sin conectividad falla al instante y no molesta a los proveedores`() = runTest {
        connectivity.online = false
        val provider = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
        )

        val result = repository(provider).refreshRates()

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is DataError.Network)
        assertEquals(0, provider.fetchLatestCalls)
    }

    @Test
    fun `observeRates emite desde Room en orden de presentacion`() = runTest {
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(
                    rate(SourceKeys.PARALELO, 954.55),
                    rate(SourceKeys.OFICIAL, 859.06),
                ),
            ),
        )
        repository.refreshRates()

        val rates = repository.observeRates().first()

        assertEquals(listOf(RateSource.OFICIAL, RateSource.PARALELO), rates.map { it.source })
        assertEquals(859.06, rates.first().value, DELTA)
        assertEquals("DolarAPI", rates.first().providerId)
    }

    @Test
    fun `cada sincronizacion deja el cierre de hoy guardado`() = runTest {
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )

        repository.refreshRates()

        val epochDay = clock.today().toEpochDay()
        val close = localDataSource.dailyCloses.value.single()
        assertEquals(SourceKeys.OFICIAL, close.source)
        assertEquals(epochDay, close.epochDay)
        assertEquals(859.06, close.value, DELTA)
    }

    @Test
    fun `la flecha de tendencia usa el cierre anterior guardado`() = runTest {
        localDataSource.dailyCloses.value = listOf(
            dailyClose(SourceKeys.OFICIAL, epochDay = clock.today().toEpochDay() - 1, value = 850.0),
        )
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )

        repository.refreshRates()

        val current = localDataSource.currentRates.value.single()
        assertEquals(850.0, current.previousClose!!, DELTA)
        assertEquals(9.06, 859.06 - current.previousClose!!, DELTA)
    }

    @Test
    fun `si la base rechaza la escritura devuelve un error de almacenamiento`() = runTest {
        // La caché se reconstruyó y su aviso está encendido cuando el commit falla.
        rebuildTracker.markRebuilt()
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )
        localDataSource.failWrites = true

        val result = repository.refreshRates()

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is DataError.Storage)
        // Nada se escribió y el aviso sigue siendo cierto: la app no miente sobre su caché.
        assertTrue(localDataSource.currentRates.value.isEmpty())
        assertEquals(0, localDataSource.commitRateSyncCalls)
        assertTrue(rebuildTracker.rebuilt.value)
    }

    @Test
    fun `una sincronizacion que si escribe apaga el aviso de reconstruccion`() = runTest {
        rebuildTracker.markRebuilt()
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )

        val result = repository.refreshRates()

        assertTrue(result is Result.Success)
        assertFalse(rebuildTracker.rebuilt.value)
    }

    @Test
    fun `si el cierre anterior no se puede leer la sincronizacion sigue sin tendencia`() = runTest {
        // Una lectura de adorno (la flecha) no puede impedir guardar la tasa del día.
        localDataSource.failReads = true
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )

        val result = repository.refreshRates()

        assertTrue(result is Result.Success)
        assertEquals(1, localDataSource.currentRates.value.size)
        assertNull(localDataSource.currentRates.value.single().previousClose)
    }

    @Test
    fun `si la poda de cierres falla las tasas recien guardadas siguen intactas`() = runTest {
        localDataSource.failPrune = true
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )

        val result = repository.refreshRates()

        assertTrue(result is Result.Success)
        assertEquals(859.06, localDataSource.currentRates.value.single().value, DELTA)
    }

    @Test
    fun `un fallo no clasificado del almacenamiento sale como error y no como excepcion`() = runTest {
        val repository = repository(
            FakeRateProvider(
                id = "DolarAPI",
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
        )
        // Ni SQLiteException ni nada que el repositorio ya sepa traducir: es el fallo que
        // antes subía hasta el ViewModel y se llevaba la app por delante.
        localDataSource.unexpectedFailure = IllegalStateException("la base se cerró a mitad")

        val result = repository.refreshRates()

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is DataError.Unknown)
        // Y sin escribir nada: un fallo no deja datos a medias.
        assertTrue(localDataSource.currentRates.value.isEmpty())
    }

    @Test
    fun `una preferencia de proveedor ilegible no impide sincronizar`() = runTest {
        // Con el archivo legible, la preferencia mandaría y la tasa la traería el proveedor
        // elegido; si el archivo falla, la app vuelve al orden automático y sigue trabajando.
        preferences.state.value = UserPreferences(preferredProviderId = ProviderIds.EXCHANGERATEAPI)
        preferences.failReads = true

        val result = repository(
            FakeRateProvider(
                id = ProviderIds.DOLARAPI,
                priority = 0,
                rates = listOf(rate(SourceKeys.OFICIAL, 859.06)),
            ),
            FakeRateProvider(
                id = ProviderIds.EXCHANGERATEAPI,
                priority = 20,
                rates = listOf(rate(SourceKeys.OFICIAL, 900.0)),
            ),
        ).refreshRates()

        assertTrue(result is Result.Success)
        val current = localDataSource.currentRates.value.single()
        assertEquals(859.06, current.value, DELTA)
        assertEquals(ProviderIds.DOLARAPI, current.providerId)
    }

    private fun rate(sourceKey: String, value: Double) = RemoteRate(
        sourceKey = sourceKey,
        value = value,
        updatedAtMillis = null,
    )

    /** Cierre diario ya guardado en Room, que es lo que recibe el almacén local. */
    private fun dailyClose(sourceKey: String, epochDay: Long, value: Double) = RateHistoryEntity(
        source = sourceKey,
        epochDay = epochDay,
        value = value,
        providerId = "DolarAPI",
    )

    private companion object {
        const val DELTA = 0.000001
    }
}
