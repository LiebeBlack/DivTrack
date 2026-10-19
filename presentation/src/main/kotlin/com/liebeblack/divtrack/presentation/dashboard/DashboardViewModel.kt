package com.liebeblack.divtrack.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.Spread
import com.liebeblack.divtrack.domain.usecase.CalculateSpreadUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveCacheRebuildUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveRatesUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.SyncRatesUseCase
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText
import com.liebeblack.divtrack.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del dashboard. Agnóstico del framework de UI: no importa nada de Compose
 * (solo `lifecycle-viewmodel`), así que se testea en JVM pura.
 *
 * Al abrir, sincroniza primero y mantiene el estado de carga visible para que las tasas
 * guardadas no aparezcan un instante antes de ser reemplazadas. Al terminar, Room entrega
 * el dato actualizado o conserva el último valor si la red falló.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeRates: ObserveRatesUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val observeCacheRebuild: ObserveCacheRebuildUseCase,
    private val syncRates: SyncRatesUseCase,
    private val calculateSpread: CalculateSpreadUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<DashboardEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<DashboardEffect> = _effects.asSharedFlow()

    private var refreshJob: Job? = null
    private var refreshRequestedByUser = false

    init {
        observeRatesFromCache()
        observeParallelRatePreference()
        observeCacheRebuildState()
        refresh(isUserInitiated = false)
    }

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Refresh -> refresh(isUserInitiated = true)
            DashboardIntent.Retry -> refresh(isUserInitiated = true)
            DashboardIntent.OnResumed -> refreshIfNeeded()
        }
    }

    /**
     * Refresco silencioso al volver a la pantalla.
     *
     * El ViewModel del panel vive mientras la pestaña siga en el back stack, así que su
     * `init` no se repite: sin esto, volver a la app después de horas mostraba la tasa de la
     * última pasada hasta que el usuario tirara de pull-to-refresh (o hasta que WorkManager
     * despertara). Solo se consulta si el dato en pantalla superó la ventana de frescura o si
     * la última pasada falló: es un refresco con criterio, no un latido de red en cada
     * regreso.
     */
    private fun refreshIfNeeded() {
        val current = _state.value
        val official = current.rates.firstOrNull { rate -> rate.source == RateSource.OFICIAL }
        val isOld = official == null ||
            timeProvider.nowMillis() - official.fetchedAtMillis >=
            AppConstants.RATE_RESUME_REFRESH_AFTER_MILLIS

        // Dos motivos para salir a la red: el dato en pantalla ya no es fresco, o la última
        // pasada falló. El segundo pesa tanto como el primero: sin él, el aviso de "sin
        // conexión" se quedaba encendido hasta quince minutos después de que el usuario
        // recuperara la red, con la tasa guardada leyéndose como si fuera de ahora.
        if (isOld || current.isOffline) refresh(isUserInitiated = false)
    }

    private fun observeRatesFromCache() {
        viewModelScope.launch {
            observeRates().collect { rates ->
                _state.update { current -> current.withRates(rates) }
            }
        }
    }

    private fun observeParallelRatePreference() {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                _state.update { it.copy(showParallelRate = settings.showParallelRate) }
            }
        }
    }

    /**
     * La base pudo recrearse tras detectar un archivo dañado. El aviso se queda en pantalla
     * hasta que una sincronización vuelve a escribir tasas: es la única señal que explica por
     * qué los datos guardados desaparecieron (ADR 31).
     */
    private fun observeCacheRebuildState() {
        viewModelScope.launch {
            observeCacheRebuild().collect { rebuilt ->
                _state.update { it.copy(cacheRebuilt = rebuilt) }
            }
        }
    }

    private fun refresh(isUserInitiated: Boolean): Job {
        val activeRefresh = refreshJob
        if (activeRefresh?.isActive == true) {
            if (isUserInitiated) {
                refreshRequestedByUser = true
                _state.update { it.copy(isRefreshing = true) }
            }
            return activeRefresh
        }

        refreshRequestedByUser = isUserInitiated
        return viewModelScope.launch {
            if (isUserInitiated) {
                _state.update { it.copy(isRefreshing = true) }
            }

            try {
                when (val result = syncRates()) {
                    is Result.Success -> {
                        val summary = result.data
                        _state.update {
                            it.copy(
                                isRefreshing = false,
                                isOffline = false,
                                isConnectivityProblem = false,
                                errorText = null,
                            )
                        }

                        when {
                            summary.isPartial -> _effects.emit(
                                DashboardEffect.ShowMessage(UiText.Res(R.string.msg_partial_update)),
                            )

                            refreshRequestedByUser -> _effects.emit(
                                DashboardEffect.ShowMessage(UiText.Res(R.string.msg_rates_updated)),
                            )

                            else -> Unit
                        }
                    }

                    is Result.Error -> {
                        val error = result.error
                        val hasCachedData = _state.value.hasData
                        val isConnectivity = error is DataError.Network
                        // Se lee ANTES de actualizar el estado: distingue el primer fallo de
                        // un reintento silencioso que vuelve a fallar igual.
                        val wasOffline = _state.value.isOffline

                        _state.update {
                            it.copy(
                                isRefreshing = false,
                                isOffline = true,
                                isConnectivityProblem = isConnectivity,
                                errorText = error.toUiText(),
                            )
                        }

                        // No todos los fallos son "sin conexión": si el proveedor responde 503 o
                        // la petición caduca, el usuario tiene que leer eso y no un diagnóstico
                        // equivocado de su propia red. Cuando sí es conectividad, el mensaje
                        // recuerda que los datos en pantalla siguen siendo válidos.
                        //
                        // Un reintento silencioso que vuelve a fallar igual no repite el
                        // aviso: el panel ya tiene su aviso permanente con la causa, y volver
                        // a la pestaña mientras no hay red no puede convertirse en una ristra
                        // de snackbars. Solo se avisa cuando hay algo nuevo que contar (el
                        // primer fallo, o una pasada que pidió el usuario).
                        if (isUserInitiated || !wasOffline) {
                            _effects.emit(
                                DashboardEffect.ShowMessage(
                                    when {
                                        isConnectivity && hasCachedData ->
                                            UiText.Res(R.string.msg_offline_showing_cache)

                                        isConnectivity -> UiText.Res(R.string.msg_offline_no_data)

                                        else -> error.toUiText()
                                    },
                                )
                            )
                        }
                    }

                    Result.Loading -> Unit
                }
            } finally {
                refreshRequestedByUser = false
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }.also { refreshJob = it }
    }

    /** Aplica las tasas de Room y deriva el spread una sola vez por emisión. */
    private fun DashboardUiState.withRates(rates: List<ExchangeRate>): DashboardUiState {
        val spread: Spread = calculateSpread(rates)
        // Una sola lectura del reloj por emisión: antes se pedía una vez por tasa y todas
        // las tarjetas debían compartir exactamente el mismo instante de referencia.
        val nowMillis = timeProvider.nowMillis()
        return copy(
            rates = rates.map { rate -> rate.toRateUiModel(nowMillis) },
            spreadPercentText = spread.percent?.let { percent -> CurrencyFormatters.percent(percent) },
            spreadAbsoluteText = spread.absolute?.let { absolute -> CurrencyFormatters.bolivars(absolute) },
        )
    }
}
