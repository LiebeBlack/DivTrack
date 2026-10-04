package com.liebeblack.divtrack.presentation.calculator

import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.usecase.CalculateConversionUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveRatesUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.SyncRatesUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.fake.FakeRateRepository
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * La calculadora es el corazón de la app: se prueba que **no existe el botón "Calcular"**,
 * que el IGTF se aplica con la aritmética correcta y que pegar un importe en formato
 * venezolano no rompe nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rateRepository = FakeRateRepository()
    private val settingsRepository = FakeSettingsRepository()

    @Before
    fun setUp() {
        rateRepository.rates.value = listOf(
            rate(RateSource.OFICIAL, 36.5, previousClose = 36.4),
            rate(RateSource.PARALELO, 954.55, previousClose = 950.0),
        )
    }

    @Test
    fun `al abrir la calculadora se sincroniza contra los proveedores`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        // El bug corregido: la calculadora solo observaba Room, así que entrar directo aquí
        // calculaba con la tasa guardada aunque llevara horas vieja.
        assertEquals(1, rateRepository.refreshCalls)
    }

    @Test
    fun `el intent de refresco fuerza una nueva pasada`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onIntent(CalculatorIntent.Refresh)
        advanceUntilIdle()

        assertEquals(2, rateRepository.refreshCalls)
    }

    @Test
    fun `la fila de tasa muestra la edad del dato publicado`() = runTest {
        val updatedAtMillis = 1_800_000_000_000L
        rateRepository.rates.value = listOf(
            rate(RateSource.OFICIAL, 36.5, previousClose = null, updatedAtMillis = updatedAtMillis),
        )
        val viewModel = createViewModel()
        collectState(viewModel)

        val state = viewModel.state.value

        // El ViewModel solo transporta la marca de tiempo a través del formateador es-VE de
        // `:core:common`: comparar contra el formateador (y no contra un literal tipo "15 ene
        // · 04:00") mantiene el test inmune a los datos CLDR del JVM que lo ejecute.
        val expected = CurrencyFormatters.timestamp(Instant.ofEpochMilli(updatedAtMillis))
        assertEquals(expected, state.selectedRateAgeText)
        assertTrue(expected.isNotEmpty())
    }

    @Test
    fun `sin marca de tiempo del proveedor no se inventa edad`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        assertEquals(null, viewModel.state.value.selectedRateAgeText)
    }

    @Test
    fun `el resultado se calcula al teclear sin pulsar ningun boton`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("10"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("10", state.inputText)
        // Sin IGTF, el total que pinta la tarjeta coincide con el neto.
        assertEquals("10,00", state.totalUsdText)
        assertEquals("365,00", state.totalBsText)
        assertTrue(state.canCopy)
    }

    @Test
    fun `activar el igtf recalcula el total sobre el neto`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("10"))
        advanceUntilIdle()
        viewModel.onIntent(CalculatorIntent.ToggleIgtf(true))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("10,95", state.igtfBsText)
        assertEquals("375,95", state.totalBsText)
        assertTrue(settingsRepository.settings.value.igtfEnabled)
    }

    @Test
    fun `la direccion inversa convierte bolivares a dolares`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.SwapDirection)
        advanceUntilIdle()
        viewModel.onIntent(CalculatorIntent.AmountChanged("365"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isUsdToBs)
        assertEquals("10,00", state.totalUsdText)
        assertEquals("365,00", state.totalBsText)
    }

    @Test
    fun `el importe pegado en formato venezolano se sanea`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("1.234,56"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("1234,56", state.inputText)
        assertEquals("45.061,44", state.totalBsText)
    }

    @Test
    fun `cambiar de tasa actualiza el calculo`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("10"))
        viewModel.onIntent(CalculatorIntent.SelectSource(RateSource.PARALELO))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(RateSource.PARALELO, state.selectedSource)
        assertEquals("954,55", state.selectedRateText)
        assertEquals("9.545,50", state.totalBsText)
    }

    @Test
    fun `la cuota del credito reparte el total en la moneda elegida`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("10"))
        viewModel.onIntent(CalculatorIntent.SelectInstallments(12))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(12, state.installments)
        // Total sin IGTF: 365,00 Bs y 10,00 $ -> 12 cuotas = 30,42 Bs y 0,83 $.
        assertEquals("30,42", state.installmentBsText)
        assertEquals("0,83", state.installmentUsdText)
    }

    @Test
    fun `el numero de cuotas nunca baja de una`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.SelectInstallments(0))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.installments)
    }

    @Test
    fun `sin tasas disponibles la calculadora lo avisa y no calcula`() = runTest {
        rateRepository.rates.value = emptyList()
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(CalculatorIntent.AmountChanged("10"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasRate)
        assertFalse(state.canCopy)
        assertEquals("—", state.totalBsText)
    }

    @Test
    fun `solo se ofrecen las tasas que existen`() = runTest {
        rateRepository.rates.value = listOf(rate(RateSource.PARALELO, 954.55, previousClose = null))
        val viewModel = createViewModel()
        collectState(viewModel)

        val state = viewModel.state.value

        // El selector no puede ofrecer el oficial si el proveedor no lo ha publicado: dejar
        // elegirlo llevaría a "sin tasas disponibles" tras un toque del usuario.
        assertEquals(listOf(RateSource.PARALELO), state.rateOptions.map { option -> option.source })
        assertEquals(RateSource.PARALELO, state.selectedSource)
        assertTrue(state.hasRate)
    }

    private fun TestScope.createViewModel() = CalculatorViewModel(
        observeRates = ObserveRatesUseCase(rateRepository),
        observeSettings = ObserveSettingsUseCase(settingsRepository),
        updateSettings = UpdateSettingsUseCase(settingsRepository),
        calculateConversion = CalculateConversionUseCase(),
        syncRates = SyncRatesUseCase(rateRepository),
    )

    /**
     * Mantiene viva la suscripción: el estado es `stateIn(WhileSubscribed)`, así que sin un
     * colector activo la calculadora ni siquiera se molesta en calcular (que es justo lo que
     * queremos en producción, y lo que hay que respetar en los tests).
     *
     * Va en `backgroundScope`: un `collect` infinito lanzado en el scope del test nunca
     * completa, y `runTest` esperaría por él hasta agotar su tiempo límite.
     */
    private fun TestScope.collectState(viewModel: CalculatorViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { }
        }
        advanceUntilIdle()
    }

    private fun rate(
        source: RateSource,
        value: Double,
        previousClose: Double?,
        updatedAtMillis: Long? = null,
    ) = ExchangeRate(
        source = source,
        value = value,
        previousClose = previousClose,
        providerId = "DolarAPI",
        updatedAtMillis = updatedAtMillis,
        fetchedAtMillis = 0L,
    )
}
