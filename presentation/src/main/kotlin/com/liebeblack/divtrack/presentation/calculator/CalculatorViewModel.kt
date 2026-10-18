package com.liebeblack.divtrack.presentation.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.core.common.utils.NumberParsing
import com.liebeblack.divtrack.domain.model.Conversion
import com.liebeblack.divtrack.domain.model.ConversionDirection
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.usecase.CalculateConversionUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveRatesUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.SyncRatesUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Calculadora bidireccional.
 *
 * El cálculo es **automático en cada tecla**: `inputText` es un `MutableStateFlow` y el
 * estado de UI se deriva con `combine` + `stateIn(WhileSubscribed)`. No hay botón "Calcular"
 * y no se recalcula nada mientras nadie observa la pantalla (sin trabajo en background).
 *
 * El saneado del texto (`NumberParsing.sanitizeAmountInput`) hace que pegar "1.234,56" o
 * escribir "1234,5" funcione sin que el usuario pelee con el formato.
 *
 * **La calculadora también sincroniza.** La tasa que usa sale de Room, y Room solo cambia
 * si alguien escribe: si esta pantalla no disparara la sincronización, entrar directo aquí
 * (o volver horas después) calcularía con la tasa guardada aunque llevara horas vieja, sin
 * aviso ni forma de refrescar. Por eso, al abrir y al pulsar "Reintentar/Actualizar" se
 * fuerza la pasada contra los proveedores: si falla, se usa la guardada y se avisa sin
 * tocar el cálculo que ya está en pantalla. Y al volver a la pestaña ([onResumed]) se repite
 * con el mismo criterio que el panel: la tasa que se ve puede ser de hace horas aunque nadie
 * haya tocado el importe.
 *
 * **También divide en cuotas.** Con el monto ya escrito, el usuario elige 3, 6, 12 o 24
 * cuotas y ve la cuota mensual en ambas monedas, calculada sobre el mismo total. No depende
 * de la red: sigue funcionando con la última tasa guardada aunque la base local haya tenido
 * que reconstruirse.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val observeRates: ObserveRatesUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val calculateConversion: CalculateConversionUseCase,
    private val syncRates: SyncRatesUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val inputText = MutableStateFlow("")
    private val direction = MutableStateFlow(ConversionDirection.USD_TO_BS)
    private val selectedSource = MutableStateFlow<RateSource?>(null)
    private val installments = MutableStateFlow(DEFAULT_INSTALLMENTS)
    private val availableRates = MutableStateFlow(CalculatorRates())
    private val isSyncing = MutableStateFlow(false)
    private var syncJob: Job? = null
    private var syncRequestedByUser = false

    /** Cuándo salió la última pasada a la red, en ms del [timeProvider]. */
    private var lastAttemptMillis: Long = 0L

    /** Si esa última pasada falló: el próximo regreso a la pantalla reintenta sin esperar. */
    private var lastAttemptFailed: Boolean = false

    private val _effects = MutableSharedFlow<CalculatorEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<CalculatorEffect> = _effects.asSharedFlow()

    // `combine` tipado solo admite 5 flujos: los datos del cálculo viajan agrupados en un
    // snapshot y el estado de sincronización va fuera.
    private val calculationInputs = combine(
        inputText,
        direction,
        selectedSource,
        availableRates,
    ) { input, currentDirection, source, rates ->
        CalculatorInputs(
            input = input,
            direction = currentDirection,
            source = source,
            rates = rates,
        )
    }

    val state: StateFlow<CalculatorUiState> = combine(
        calculationInputs,
        isSyncing,
        installments,
    ) { inputs, syncing, installmentCount ->
        buildState(
            input = inputs.input,
            direction = inputs.direction,
            requestedSource = inputs.source,
            rates = inputs.rates,
            isSyncing = syncing,
            installments = installmentCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CalculatorUiState(igtfRateText = AppConstants.IGTF_LABEL),
    )

    init {
        viewModelScope.launch {
            observeRates()
                .distinctUntilChanged()
                .collect { rates -> availableRates.value = rates.toCalculatorRates() }
        }

        viewModelScope.launch {
            // Las preferencias inicializan la calculadora; si el usuario ya eligió algo en
            // esta sesión, su elección manda (el valor no nulo gana).
            observeSettings().collect { settings ->
                if (selectedSource.value == null) selectedSource.value = settings.defaultSource
            }
        }

        // Asegura que el cálculo parta de la tasa del día: sin esto, entrar directo a la
        // calculadora usaría la última fila de Room aunque llevara horas vieja.
        refresh()
    }

    fun onIntent(intent: CalculatorIntent) {
        when (intent) {
            is CalculatorIntent.AmountChanged -> {
                inputText.value = NumberParsing.sanitizeAmountInput(intent.value)
            }

            is CalculatorIntent.SelectSource -> selectedSource.value = intent.source

            // Nunca menos de una cuota: el selector de la UI no lo permite, pero el estado no
            // depende de que la UI se porte bien.
            is CalculatorIntent.SelectInstallments ->
                installments.value = intent.count.coerceAtLeast(1)

            CalculatorIntent.Refresh -> refresh(isUserInitiated = true)

            CalculatorIntent.OnResumed -> onResumed()

            CalculatorIntent.SwapDirection -> {
                direction.value = when (direction.value) {
                    ConversionDirection.USD_TO_BS -> ConversionDirection.BS_TO_USD
                    ConversionDirection.BS_TO_USD -> ConversionDirection.USD_TO_BS
                }
            }

            CalculatorIntent.ClearAmount -> inputText.value = ""

            CalculatorIntent.CopyBreakdown -> copyBreakdown()
        }
    }

    /**
     * La pantalla volvió al primer plano (o a su pestaña).
     *
     * El ViewModel vive mientras la pestaña siga en el back stack, así que su `init` no se
     * repite: sin esto, volver a la calculadora después de horas mostraba y usaba la tasa
     * guardada como si fuera de ahora. Se consulta la red solo si el último intento falló o si
     * ya venció la ventana de frescura —el mismo criterio que el panel—, así que volver a los
     * dos minutos no gasta radio.
     */
    private fun onResumed() {
        val needsCheck = lastAttemptFailed ||
            timeProvider.nowMillis() - lastAttemptMillis >= AppConstants.RATE_RESUME_REFRESH_AFTER_MILLIS
        if (needsCheck) refresh()
    }

    /**
     * Comparte la sincronización en curso de esta pantalla. El mutex del repositorio
     * serializa además los refrescos que lleguen desde otras pantallas o WorkManager.
     */
    private fun refresh(isUserInitiated: Boolean = false) {
        if (syncJob?.isActive == true) {
            if (isUserInitiated) syncRequestedByUser = true
            return
        }

        syncRequestedByUser = isUserInitiated
        lastAttemptMillis = timeProvider.nowMillis()
        isSyncing.value = true
        syncJob = viewModelScope.launch {
            try {
                val result = syncRates()

                // El resultado se recuerda para el próximo regreso a la pantalla: un fallo
                // reciente obliga a reintentar aunque la ventana de frescura no haya vencido.
                lastAttemptFailed = result is Result.Error

                if (result is Result.Error && syncRequestedByUser) {
                    _effects.emit(
                        CalculatorEffect.ShowMessage(UiText.Res(R.string.calculator_sync_failed)),
                    )
                }
                // El éxito no necesita efecto: Room emite y el `combine` recalcula solo.
            } finally {
                syncRequestedByUser = false
                isSyncing.value = false
            }
        }
    }

    private fun copyBreakdown() {
        val current = state.value
        if (!current.canCopy) return

        viewModelScope.launch {
            _effects.emit(
                CalculatorEffect.CopyToClipboard(
                    summary = BreakdownSummary(
                        direction = current.direction,
                        source = current.selectedSource,
                        amountText = current.inputText,
                        rateText = current.selectedRateText,
                        igtfText = current.igtfBsText,
                        totalBsText = current.totalBsText,
                        totalUsdText = current.totalUsdText,
                    ),
                ),
            )
        }
    }

    private fun buildState(
        input: String,
        direction: ConversionDirection,
        requestedSource: RateSource?,
        rates: CalculatorRates,
        isSyncing: Boolean,
        installments: Int,
    ): CalculatorUiState {
        val options = rates.options

        // La preferencia del usuario manda **solo si esa tasa existe hoy**: si el proveedor
        // no la ha publicado, caemos en la que sí está en lugar de dejar la calculadora
        // mostrando "sin tasas" con la otra disponible a un toque de distancia.
        val source = requestedSource
            ?.takeIf { requested -> options.any { option -> option.source == requested } }
            ?: options.firstOrNull()?.source
            ?: RateSource.OFICIAL
        // `options` sale de `rates` en el mismo orden, así que basta una búsqueda.
        val selectedRate = options.firstOrNull { it.source == source }?.value

        val conversion: Conversion? = calculateConversion(
            direction = direction,
            source = source,
            rate = selectedRate,
            rawAmount = input,
            igtfEnabled = false,
        )

        // La cuota sale del mismo total ya calculado (IGTF incluido, si está activo): dividir
        // es exacto porque el impuesto es proporcional a cada pago. Sin monto o sin tasa, el
        // guion de "sin dato" se mantiene.
        val installmentBsText =
            conversion?.let { CurrencyFormatters.amount(it.totalBs / installments) } ?: "—"
        val installmentUsdText =
            conversion?.let { CurrencyFormatters.amount(it.totalUsd / installments) } ?: "—"

        // La fila "Tasa: X" también cuenta la edad del dato: una tasa de ayer es el motivo
        // nº1 de que las cuentas "no salgan", y no hay que dejar que se lea como actual.
        return CalculatorUiState(
            inputText = input,
            direction = direction,
            selectedSource = source,
            rateOptions = options,
            selectedRateText = selectedRate?.let { rates.formattedValues[source] } ?: "—",
            selectedRateAgeText = rates.formattedUpdatedAt[source],
            isSyncing = isSyncing,
            igtfRateText = AppConstants.IGTF_LABEL,
            igtfBsText = conversion?.let { CurrencyFormatters.amount(it.igtfBs) } ?: "—",
            totalBsText = conversion?.let { CurrencyFormatters.amount(it.totalBs) } ?: "—",
            totalUsdText = conversion?.let { CurrencyFormatters.amount(it.totalUsd) } ?: "—",
            installments = installments,
            installmentBsText = installmentBsText,
            installmentUsdText = installmentUsdText,
            canCopy = conversion?.hasAmount == true,
            hasRate = selectedRate != null,
        )
    }

    /** Marca de tiempo del proveedor, en el formato es-VE de la app; vacío si falla. */
    private fun formatUpdatedAt(millis: Long): String =
        runCatching { CurrencyFormatters.timestamp(Instant.ofEpochMilli(millis)) }.getOrDefault("")

    private fun List<ExchangeRate>.toCalculatorRates(): CalculatorRates = CalculatorRates(
        options = map { rate ->
            RateOptionUi(
                source = rate.source,
                value = rate.value,
            )
        },
        formattedValues = associate { rate ->
            rate.source to CurrencyFormatters.amount(rate.value)
        },
        formattedUpdatedAt = associate { rate ->
            rate.source to rate.updatedAtMillis?.let(::formatUpdatedAt)
        },
    )

    /** Los datos que alimentan el cálculo, agrupados para `combine`. */
    private data class CalculatorInputs(
        val input: String,
        val direction: ConversionDirection,
        val source: RateSource?,
        val rates: CalculatorRates,
    )

    private data class CalculatorRates(
        val options: List<RateOptionUi> = emptyList(),
        val formattedValues: Map<RateSource, String> = emptyMap(),
        val formattedUpdatedAt: Map<RateSource, String?> = emptyMap(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
