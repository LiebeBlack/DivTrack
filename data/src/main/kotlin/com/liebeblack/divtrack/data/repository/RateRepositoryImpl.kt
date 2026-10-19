package com.liebeblack.divtrack.data.repository

import android.database.sqlite.SQLiteException
import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.core.database.DatabaseRebuildTracker
import com.liebeblack.divtrack.core.database.datasource.RateLocalDataSource
import com.liebeblack.divtrack.core.datastore.UserPreferencesDataSource
import com.liebeblack.divtrack.core.network.monitor.ConnectivityObserver
import com.liebeblack.divtrack.data.di.IoDispatcher
import com.liebeblack.divtrack.data.mapper.toDailyCloseEntity
import com.liebeblack.divtrack.data.mapper.toDomain
import com.liebeblack.divtrack.data.mapper.toEntity
import com.liebeblack.divtrack.data.provider.ProviderRegistry
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.ProviderFailure
import com.liebeblack.divtrack.domain.model.ProviderStatus
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.SyncSummary
import com.liebeblack.divtrack.domain.repository.RateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Implementación Online-First.
 *
 * Reglas que se cumplen aquí (y que son el corazón del proyecto):
 *
 * 1. **Room es la única fuente de verdad para la UI.** Todas las lecturas son `Flow` de
 *    Room: la pantalla se actualiza sola cuando la red escribe y sigue funcionando offline.
 * 2. **Un fallo de red no toca los datos.** Si los proveedores fallan, no se escribe nada
 *    y se devuelve [Result.Error]; la UI muestra el aviso con lo que ya tenía.
 * 3. **Single-flight.** Un [Mutex] evita que el refresco manual, el arranque y WorkManager
 *    disparen tres sincronizaciones simultáneas.
 * 4. **El cierre de cada día se persiste.** Cada sync escribe la fila de hoy en el cierre
 *    diario, de modo que mañana existe un "cierre anterior" real para la flecha de tendencia
 *    incluso si la app estuvo cerrada.
 * 5. **La última lectura vive en memoria.** Si Room falla en caliente (archivo dañado, disco
 *    lleno, base en reconstrucción), el flujo no mata al colector: reintenta un par de veces
 *    y, si no hay arreglo, sirve la última lista leída en esta sesión. Es el respaldo que
 *    mantiene a la calculadora calculando aunque la caché desaparezca (ADR 31).
 * 6. **Un fallo de escritura no tumba la pantalla.** Si Room rechaza el commit, el error sube
 *    clasificado como [DataError.Storage] y la UI lo cuenta; el usuario se queda con las
 *    tasas viejas en pantalla en vez de perder la app por una excepción sin dueño.
 * 7. **Ninguna excepción cruza esta frontera.** Lo que no esté clasificado (una base cerrada a
 *    mitad, un `IllegalStateException` de Room, un archivo de preferencias ilegible) también
 *    sale como [Result.Error] tipado: arriba solo hay ViewModels, y una excepción sin dueño
 *    ahí sí se convierte en una caída de la app.
 */
@Singleton
class RateRepositoryImpl @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val localDataSource: RateLocalDataSource,
    private val databaseRebuildTracker: DatabaseRebuildTracker,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val timeProvider: TimeProvider,
    private val logger: Logger,
    private val connectivityObserver: ConnectivityObserver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RateRepository {

    private val syncMutex = Mutex()

    /**
     * Última lista de tasas servida a la UI en este proceso. Se actualiza con cada emisión
     * de Room que traiga contenido, **no** se borra si la base se reconstruye vacía (la
     * memoria es el único sitio donde quedan esas tasas) y se sirve cuando la lectura falla
     * del todo.
     */
    private val lastKnownRates = MutableStateFlow<List<ExchangeRate>>(emptyList())

    override fun observeRates(): Flow<List<ExchangeRate>> =
        localDataSource.observeCurrentRates()
            .map { entities ->
                entities
                    .map { entity -> entity.toDomain() }
                    .sortedBy { rate -> rate.source.displayOrder }
            }
            .onEach { rates ->
                // Una emisión vacía no borra la memoria: si Room se reconstruyó, la última
                // lectura real es lo único que queda para que el cálculo siga funcionando.
                if (rates.isNotEmpty()) lastKnownRates.value = rates
            }
            .retryWhen { cause, attempt ->
                val retriable = cause is SQLiteException && attempt < RATE_FLOW_RETRY_ATTEMPTS
                if (retriable) delay(RATE_FLOW_RETRY_DELAY_MILLIS)
                retriable
            }
            // Si la lectura se cae del todo, se sirve la última lectura real de la sesión y se
            // deja rastro: este respaldo salva la pantalla, y precisamente por eso escondía el
            // diagnóstico —sin este registro, una base que falla en bucle no aparecía en
            // ningún log.
            .catch { throwable ->
                logger.warn(
                    TAG,
                    "La lectura de tasas falló: se sirve la última lectura en memoria",
                    throwable,
                )
                emit(lastKnownRates.value)
            }
            .map { rates -> rates.ifEmpty { lastKnownRates.value } }
            .flowOn(ioDispatcher)

    override fun observeCacheRebuild(): Flow<Boolean> = databaseRebuildTracker.rebuilt

    override suspend fun refreshRates(): Result<SyncSummary> = withContext(ioDispatcher) {
        try {
            syncMutex.withLock { performRatesRefresh() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // La cancelación estructurada se propaga; cualquier otro fallo se clasifica aquí
            // porque el contrato de la capa de datos es devolver `Result`, nunca lanzar.
            logger.error(TAG, "La sincronización falló de forma no clasificada", exception)
            Result.Error(DataError.Unknown(cause = exception))
        }
    }

    /**
     * Diagnóstico de fuentes para Ajustes: lectura pura de red, sin tocar Room ni los
     * datos guardados. Respeta el proveedor preferido del usuario.
     */
    override suspend fun testProviders(): ProviderDiagnostics = withContext(ioDispatcher) {
        try {
            providerRegistry.testAll(preferredProviderId())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // El diagnóstico alimenta una pantalla, así que no puede lanzar: se devuelve el
            // estado más honesto posible (ninguna fuente comprobada, todas marcadas como
            // caídas con la causa real) en lugar de dejar la corrutina de Ajustes muerta.
            logger.error(TAG, "El diagnóstico de fuentes falló de forma no clasificada", exception)
            ProviderDiagnostics(
                statuses = ProviderIds.ordered.map { providerId ->
                    ProviderStatus(
                        providerId = providerId,
                        isOk = false,
                        sources = emptyList(),
                        error = ProviderFailure(
                            providerId = providerId,
                            error = DataError.Unknown(cause = exception),
                        ),
                    )
                },
            )
        }
    }

    /**
     * Obtiene la tasa actual de una fuente específica (para el widget).
     * Lectura síncrona de Room: ultra-rápida, sin bloqueos de UI.
     */
    override suspend fun getCurrentRate(source: RateSource): ExchangeRate? = withContext(ioDispatcher) {
        try {
            localDataSource.currentRate(source.key)?.toDomain()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.warn(TAG, "No se pudo leer la tasa actual de ${source.key}", exception)
            null
        }
    }

    /**
     * Observa la tasa actual de una fuente específica (para la notificación persistente).
     * Flow ligero que solo emite cuando cambia esa tasa específica.
     */
    override fun observeCurrentRate(source: RateSource): Flow<ExchangeRate?> =
        localDataSource.observeCurrentRate(source.key)
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    /**
     * Preferencia persistida, normalizada: `null` = orden automático.
     *
     * Una preferencia ilegible degrada al orden automático en lugar de tumbar la pasada: es
     * una comodidad del usuario, no un requisito para traer el dólar del día. Sin esto, un
     * archivo de preferencias dañado dejaba al panel sin sincronizar y al comprobador de
     * fuentes sin poder ni responder.
     */
    private suspend fun preferredProviderId(): String? = try {
        preferencesDataSource.preferences.first().preferredProviderId
            .takeIf { it.isNotBlank() }
            ?.takeIf { it in ProviderIds.ordered }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        logger.warn(TAG, "Preferencia de proveedor ilegible: se usa el orden automático", exception)
        null
    }

    private suspend fun performRatesRefresh(): Result<SyncSummary> {
        // Fallo rápido: sin conectividad no se espera al timeout para decir "sin conexión".
        // El usuario ve el aviso al instante y se ahorra el trabajo de red. Es una pista,
        // no un requisito: `ConnectivityObserver` solo responde "false" cuando de verdad no
        // hay ninguna red activa, así que un falso negativo no puede dejar la app muda.
        if (!connectivityObserver.isOnline()) {
            logger.warn(TAG, "Sincronización omitida: el sistema no reporta ninguna red activa")
            return Result.Error(DataError.Network(message = "Sin conexión a internet"))
        }

        val outcome = providerRegistry.fetchLatest(preferredProviderId())

        if (outcome.rates.isEmpty()) {
            val error = mostInformativeFailure(outcome.failures)
            logger.warn(TAG, "Sincronización sin datos: ${error.message ?: error::class.simpleName}")
            return Result.Error(error)
        }

        val epochDay = timeProvider.today().toEpochDay()

        val entities = outcome.rates.mapNotNull { resolved ->
            val source = RateSource.fromKey(resolved.rate.sourceKey) ?: return@mapNotNull null
            resolved.rate.toEntity(
                providerId = resolved.providerId,
                previousClose = previousCloseOrNull(
                    source = source,
                    epochDay = epochDay,
                    currentValue = resolved.rate.value,
                ),
                fetchedAtMillis = outcome.fetchedAtMillis,
            )
        }

        if (entities.isEmpty()) {
            return Result.Error(DataError.Parse(message = "Los proveedores no devolvieron tasas utilizables"))
        }

        // Commit atómico: tasas vigentes + cierres diarios en UNA transacción Room.
        // Antes eran dos upserts separados: un kill del proceso en el medio dejaba tasas
        // nuevas con cierres viejos y la tendencia corrupta hasta el día siguiente.
        //
        // La escritura va protegida a propósito: si Room la rechaza (archivo dañado, disco
        // lleno, base cerrada a mitad), el error vuelve clasificado y la UI lo cuenta en vez
        // de subir como excepción y matar al ViewModel que pidió el refresco.
        try {
            localDataSource.commitRateSync(
                rates = entities,
                closes = entities.map { entity -> entity.toDailyCloseEntity(epochDay) },
            )
        } catch (exception: SQLiteException) {
            logger.error(TAG, "La base local rechazó la escritura de la sincronización", exception)
            return Result.Error(DataError.Storage(cause = exception))
        }

        // La caché volvió a tener datos: el aviso de reconstrucción deja de ser cierto.
        databaseRebuildTracker.markRecovered()

        // Retención del cierre diario: la tabla crece dos filas al día. La poda viaja con la
        // sincronización (y no en un trabajo aparte) para que no exista ninguna ruta que
        // escriba cierres sin aplicarle nunca el límite de antigüedad.
        pruneOldDailyCloses()

        if (outcome.failures.isNotEmpty()) {
            logger.warn(TAG, "Sincronización parcial: ${entities.size} tasas, ${outcome.failures.size} fallos")
        }

        return Result.Success(
            SyncSummary(
                updatedSources = entities.mapNotNull { entity -> RateSource.fromKey(entity.source) },
                providerIds = outcome.providerIds,
                failures = outcome.failures,
                fetchedAtMillis = outcome.fetchedAtMillis,
            ),
        )
    }

    /**
     * Cierre anterior en este orden:
     * 1. Última fila del cierre diario con día menor a hoy (el caso normal).
     * 2. El valor vigente previo, para que la primera sincronización ya muestre tendencia.
     */
    private suspend fun resolvePreviousClose(
        source: RateSource,
        epochDay: Long,
        currentValue: Double,
    ): Double? {
        localDataSource.previousClose(source.key, epochDay)?.let { return it.value }
        return localDataSource.currentRate(source.key)?.value?.takeIf { it != currentValue }
    }

    /**
     * [resolvePreviousClose] a prueba de fallos.
     *
     * Si el cierre anterior no se puede leer (fila ilegible, base en reconstrucción), la
     * sincronización continúa y la flecha de tendencia se queda sin dato: que un adorno de la
     * UI impida guardar el valor del día sería peor que el adorno. El commit, en cambio, sí
     * aborta la operación: sin escritura no hay nada nuevo que contar.
     */
    private suspend fun previousCloseOrNull(
        source: RateSource,
        epochDay: Long,
        currentValue: Double,
    ): Double? =
        try {
            resolvePreviousClose(source, epochDay, currentValue)
        } catch (exception: SQLiteException) {
            logger.warn(
                TAG,
                "No se pudo leer el cierre anterior de ${source.key}: ${exception.message}",
            )
            null
        }

    private suspend fun pruneOldDailyCloses() {
        val keepFromEpochDay = timeProvider.today()
            .minusYears(AppConstants.DAILY_CLOSE_KEEP_YEARS)
            .toEpochDay()
        try {
            localDataSource.pruneDailyCloses(keepFromEpochDay)
        } catch (exception: SQLiteException) {
            // La poda es mantenimiento, no parte del commit: si falla, las tasas recién
            // guardadas siguen intactas y la próxima sincronización vuelve a intentarla.
            logger.warn(TAG, "No se pudo podar el cierre diario antiguo: ${exception.message}")
        }
    }

    /**
     * Elige el error que más explica al usuario.
     *
     * Con varios proveedores es normal acabar con un `Network` genérico (el DNS no resolvió)
     * y un `Http` concreto (el proveedor devolvió 404). Enseñar el genérico escondería la
     * causa real, y el usuario leería "sin conexión" cuando sí tenía conexión.
     */
    private fun mostInformativeFailure(failures: List<ProviderFailure>): DataError =
        failures.firstOrNull { failure -> failure.error !is DataError.Network }?.error
            ?: failures.firstOrNull()?.error
            ?: DataError.EmptyCache(message = "Los proveedores de tasas no respondieron")

    private companion object {
        const val TAG = "RateRepository"

        /** Reintentos de la lectura de Room antes de tirar de la última lectura en memoria. */
        const val RATE_FLOW_RETRY_ATTEMPTS = 2L

        /** Un respiro entre reintentos: deja que Room termine de reconstruir la base. */
        const val RATE_FLOW_RETRY_DELAY_MILLIS = 250L
    }
}
