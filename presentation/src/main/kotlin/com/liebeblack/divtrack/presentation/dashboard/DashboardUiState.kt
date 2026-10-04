package com.liebeblack.divtrack.presentation.dashboard

import androidx.compose.runtime.Immutable
import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.TrendDirection
import com.liebeblack.divtrack.presentation.common.UiText
import java.time.Instant

/**
 * Modelo de una tasa ya formateado para pintar.
 *
 * Todo son primitivas o enums: `@Immutable` + campos estables hacen que Compose pueda
 * saltarse la recomposición de la tarjeta cuando solo cambia la otra tasa.
 */
@Immutable
data class RateUiModel(
    val source: RateSource,
    val valueText: String,
    val deltaText: String?,
    val trend: TrendDirection,
    /** El proveedor publicó este dato hace más del umbral de su fuente (banco sin publicar). */
    val isStale: Boolean,
    val providerText: String,
    val updatedAtText: String?,

    /**
     * Cuándo la app trajo este valor de la red.
     *
     * Es distinto de [updatedAtText], que cuenta cuándo lo publicó el proveedor: aquí
     * interesa la edad de la comprobación, que es lo que decide el refresco al volver a la
     * pantalla (el proveedor puede no publicar nada nuevo y aun así haber que comprobarlo).
     */
    val fetchedAtMillis: Long,
)

/** Estado completo del dashboard. Es la única fuente que consume la pantalla. */
@Immutable
data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val rates: List<RateUiModel> = emptyList(),
    val showParallelRate: Boolean = false,
    val spreadPercentText: String? = null,
    val spreadAbsoluteText: String? = null,

    /** La última sincronización falló. */
    val isOffline: Boolean = false,

    /**
     * El fallo fue de conectividad (y no del proveedor). Decide si tiene sentido ofrecer el
     * atajo a los ajustes de red: mandar al usuario a sus ajustes cuando el que falla es el
     * proveedor solo consigue que toque cosas que no arreglan nada.
     */
    val isConnectivityProblem: Boolean = false,

    /**
     * La base local se recreó vacía porque su archivo no era legible. Mientras sea `true`, el
     * panel explica que las tasas guardadas se perdieron y que volverán al sincronizar
     * (ADR 31). Se apaga solo cuando la sincronización vuelve a escribir en la caché.
     */
    val cacheRebuilt: Boolean = false,

    /** Causa real del último fallo, ya traducida a un recurso de texto. */
    val errorText: UiText? = null,
) {
    val hasData: Boolean get() = rates.isNotEmpty()

    val visibleRates: List<RateUiModel>
        get() = if (showParallelRate) rates else rates.filter { it.source == RateSource.OFICIAL }

    /** Alguna tasa visible viene del "banco que dejó de publicar": merece aviso permanente. */
    val hasStaleData: Boolean
        get() = rates.any { rate ->
            (showParallelRate || rate.source == RateSource.OFICIAL) && rate.isStale
        }
}

/** Dominio -> UI. El formateo es-VE se hace una sola vez, aquí, no en cada recomposición. */
internal fun ExchangeRate.toRateUiModel(nowMillis: Long): RateUiModel = RateUiModel(
    source = source,
    valueText = CurrencyFormatters.bolivars(value),
    deltaText = changePercent?.takeIf { it.isFinite() }?.let { CurrencyFormatters.percent(it) },
    trend = trend,
    isStale = isStale(nowMillis),
    providerText = providerId,
    updatedAtText = updatedAtMillis?.let { millis ->
        runCatching { CurrencyFormatters.timestamp(Instant.ofEpochMilli(millis)) }.getOrNull()
    },
    fetchedAtMillis = fetchedAtMillis,
)
