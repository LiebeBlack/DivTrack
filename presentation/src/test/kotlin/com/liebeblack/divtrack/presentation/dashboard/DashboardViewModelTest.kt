package com.liebeblack.divtrack.presentation.dashboard

import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.ProviderFailure
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.SyncSummary
import com.liebeblack.divtrack.domain.usecase.CalculateSpreadUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveCacheRebuildUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveRatesUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.SyncRatesUseCase
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText
import com.liebeblack.divtrack.presentation.fake.FakeRateRepository
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.fake.FakeTimeProvider
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * El dashboard es donde el pliego pone dos requisitos explícitos:
 *  1. Si la API responde, la UI refleja los datos (y la brecha se calcula sola).
 *  2. Si falla, se conservan los datos de Room y se emite un evento one-shot para el snackbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rateRepository = FakeRateRepository()
    private val settingsRepository = FakeSettingsRepository()

    @Test
    fun `las tarjetas y la brecha se derivan de las tasas guardadas`() = runTest {
        rateRepository.rates.value = listOf(
            rate(RateSource.OFICIAL, 859.06, previousClose = 858.0),
            rate(RateSource.PARALELO, 954.55, previousClose = 960.0),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.rates.size)
        assertEquals("859,06 Bs.", state.rates.first { it.source == RateSource.OFICIAL }.valueText)
        assertEquals("954,55 Bs.", state.rates.first { it.source == RateSource.PARALELO }.valueText)
        assertEquals("+11,12 %", state.spreadPercentText)
        assertEquals("95,49 Bs.", state.spreadAbsoluteText)
    }

    @Test
    fun `la tasa paralela queda oculta hasta activarla desde ajustes`() = runTest {
        rateRepository.rates.value = listOf(
            rate(RateSource.OFICIAL, 859.06, previousClose = null),
            rate(RateSource.PARALELO, 954.55, previousClose = null),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(RateSource.OFICIAL), viewModel.state.value.visibleRates.map { it.source })

        settingsRepository.settings.value = settingsRepository.settings.value.copy(showParallelRate = true)
        advanceUntilIdle()

        assertEquals(
            listOf(RateSource.OFICIAL, RateSource.PARALELO),
            viewModel.state.value.visibleRates.map { it.source },
        )
    }

    @Test
    fun `si la red falla conserva el cache y emite el aviso de sin conexion`() = runTest {
        rateRepository.rates.value = listOf(
            rate(RateSource.OFICIAL, 859.06, previousClose = null),
            rate(RateSource.PARALELO, 954.55, previousClose = null),
        )
        rateRepository.refreshResult = Result.Error(DataError.Network())

        val viewModel = createViewModel()
        val effects = mutableListOf<DashboardEffect>()
        collectEffects(viewModel, effects)

        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.rates.size)
        assertTrue(viewModel.state.value.isOffline)
        assertEquals(
            DashboardEffect.ShowMessage(UiText.Res(R.string.msg_offline_showing_cache)),
            effects.single(),
        )
    }

    @Test
    fun `una sincronizacion parcial avisa sin perder datos`() = runTest {
        rateRepository.rates.value = listOf(rate(RateSource.OFICIAL, 859.06, previousClose = null))
        rateRepository.refreshResult = Result.Success(
            SyncSummary(
                updatedSources = listOf(RateSource.OFICIAL),
                providerIds = listOf("DolarAPI"),
                failures = listOf(ProviderFailure("Yadio", DataError.Timeout())),
                fetchedAtMillis = 0L,
            ),
        )

        val viewModel = createViewModel()
        val effects = mutableListOf<DashboardEffect>()
        collectEffects(viewModel, effects)

        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.rates.size)
        assertEquals(
            DashboardEffect.ShowMessage(UiText.Res(R.string.msg_partial_update)),
            effects.single(),
        )
    }

    @Test
    fun `si la cache se reconstruyo el panel lo explica`() = runTest {
        rateRepository.cacheRebuild.value = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cacheRebuilt)
    }

    @Test
    fun `volver a la pantalla no gasta red si la tasa es reciente`() = runTest {
        val timeProvider = FakeTimeProvider(nowMillis = NOW_MILLIS)
        rateRepository.rates.value = listOf(
            rate(
                RateSource.OFICIAL,
                859.06,
                previousClose = null,
                fetchedAtMillis = NOW_MILLIS - 5 * MINUTE_MILLIS,
            ),
        )

        val viewModel = createViewModel(timeProvider)
        advanceUntilIdle()
        val callsAfterOpen = rateRepository.refreshCalls

        viewModel.onIntent(DashboardIntent.OnResumed)
        advanceUntilIdle()

        assertEquals(callsAfterOpen, rateRepository.refreshCalls)
    }

    @Test
    fun `volver a la pantalla refresca sola la tasa vieja sin avisar`() = runTest {
        val timeProvider = FakeTimeProvider(nowMillis = NOW_MILLIS)
        rateRepository.rates.value = listOf(
            rate(
                RateSource.OFICIAL,
                859.06,
                previousClose = null,
                fetchedAtMillis = NOW_MILLIS - 40 * MINUTE_MILLIS,
            ),
        )

        val viewModel = createViewModel(timeProvider)
        val effects = mutableListOf<DashboardEffect>()
        collectEffects(viewModel, effects)
        advanceUntilIdle()
        val callsAfterOpen = rateRepository.refreshCalls
        effects.clear()

        viewModel.onIntent(DashboardIntent.OnResumed)
        advanceUntilIdle()

        assertEquals(callsAfterOpen + 1, rateRepository.refreshCalls)
        assertTrue("un refresco automático no debe mostrar snackbar", effects.isEmpty())
    }

    @Test
    fun `volver a la pantalla reintenta si la ultima pasada fallo`() = runTest {
        val timeProvider = FakeTimeProvider(nowMillis = NOW_MILLIS)
        // El dato mostrado es de hace un minuto: por edad, la ventana de frescura lo cubre.
        rateRepository.rates.value = listOf(
            rate(
                RateSource.OFICIAL,
                859.06,
                previousClose = null,
                fetchedAtMillis = NOW_MILLIS - MINUTE_MILLIS,
            ),
        )
        rateRepository.refreshResult = Result.Error(DataError.Network())

        val viewModel = createViewModel(timeProvider)
        val effects = mutableListOf<DashboardEffect>()
        collectEffects(viewModel, effects)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOffline)
        val callsAfterOpen = rateRepository.refreshCalls
        effects.clear()

        viewModel.onIntent(DashboardIntent.OnResumed)
        advanceUntilIdle()

        // La red pudo volver mientras la app seguía mostrando el dato guardado: el regreso a
        // la pantalla es el momento de reintentar, sin esperar a que el dato cumpla 15 minutos.
        assertEquals(callsAfterOpen + 1, rateRepository.refreshCalls)
        // Y sin repetir el aviso: el panel ya tiene su aviso permanente con la causa.
        assertTrue("un reintento silencioso no repite el snackbar", effects.isEmpty())
    }

    private fun TestScope.createViewModel(
        timeProvider: FakeTimeProvider = FakeTimeProvider(),
    ) = DashboardViewModel(
        observeRates = ObserveRatesUseCase(rateRepository),
        observeSettings = ObserveSettingsUseCase(settingsRepository),
        observeCacheRebuild = ObserveCacheRebuildUseCase(rateRepository),
        syncRates = SyncRatesUseCase(rateRepository),
        calculateSpread = CalculateSpreadUseCase(),
        timeProvider = timeProvider,
    )

    /**
     * Recolecta los eventos one-shot en una lista propia, con un dispatcher no confinado
     * para que la suscripción exista **antes** de que el `init` del ViewModel emita. Es la
     * forma determinista de testear un `SharedFlow` sin depender de tiempos.
     *
     * Va en `backgroundScope` porque `toList` no completa nunca: si se lanzara en el scope
     * del test, `runTest` esperaría por él hasta agotar su tiempo límite.
     */
    private fun TestScope.collectEffects(
        viewModel: DashboardViewModel,
        destination: MutableList<DashboardEffect>,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.toList(destination)
        }
    }

    private fun rate(
        source: RateSource,
        value: Double,
        previousClose: Double?,
        fetchedAtMillis: Long = 0L,
    ) = ExchangeRate(
        source = source,
        value = value,
        previousClose = previousClose,
        providerId = "DolarAPI",
        updatedAtMillis = null,
        fetchedAtMillis = fetchedAtMillis,
    )

    private companion object {
        /** Reloj fijo de los tests; cualquier marca anterior simula un dato con edad. */
        const val NOW_MILLIS = 1_800_000_000_000L
        const val MINUTE_MILLIS = 60_000L
    }
}
