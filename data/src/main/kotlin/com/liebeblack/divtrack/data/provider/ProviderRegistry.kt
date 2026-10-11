package com.liebeblack.divtrack.data.provider

import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.provider.RateProvider
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.ProviderFailure
import com.liebeblack.divtrack.domain.model.ProviderStatus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Una tasa ya resuelta junto al proveedor que la aportó.
 *
 * La procedencia viaja con la tasa porque al aplanar varios proveedores en una sola lista se
 * pierde quién publicó cada valor, y eso es justo lo que la UI muestra ("Fuente: DolarAPI").
 */
data class ResolvedRate(
    val rate: RemoteRate,
    val providerId: String,
)

/** Resultado agregado de una pasada de tasas vigentes. */
data class ProviderFetchOutcome(
    val rates: List<ResolvedRate>,
    val providerIds: List<String>,
    val failures: List<ProviderFailure>,
    val fetchedAtMillis: Long,
)

/**
 * Orquesta el multi-proveedor con tres reglas:
 *
 * 1. **La resolución es por tasa, no por proveedor**. Entre proveedores normales gana el
 *    dato MÁS FRESCO: si uno de cola publica una marca de tiempo posterior a la que ya se
 *    resolvió, la reemplaza; y si el titular ya está rancio, también lo reemplaza una
 *    fuente que respondió **en vivo sin publicar hora** (Yadio, upstream del paralelo):
 *    ver [acceptsUnstamped]. Dos excepciones, ambas de producto:
 *    - Una fuente **autoritativa** (ADR 40): el proveedor declara que es la referencia de
 *      una tasa —DolarAPI lo es del oficial, vía su endpoint dedicado— y entonces su valor
 *      no se deja pisar por nadie ni por ser más viejo; el respaldo solo se busca cuando
 *      ese proveedor **falla**. Ver [isAuthoritativeSource].
 *    - Un dato fechado en el **futuro** no compite por frescura: se degrada a lectura sin
 *      hora. Ver [withTrustedTimestamp].
 * 2. **Circuit breaker con auto-recuperación.** Un proveedor que falla queda "abierto"
 *    durante [AppConstants.PROVIDER_COOLDOWN_MILLIS]: se salta en las pasadas siguientes y
 *    nadie espera su timeout. Cumplido el periodo se reintenta solo; si responde, vuelve al
 *    servicio. Si TODOS estuvieran abiertos, se reintenta el orden completo: la app nunca se
 *    queda sin pasada por culpa del breaker.
 * 3. **El orden lo manda el usuario** (preferido primero) o la prioridad del proyecto;
 *    la pasada se corta cuando las dos tasas están resueltas **y frescas**.
 *
 * @param preferredProviderId preferencia persistida; `null` o id desconocido = automático.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards RateProvider>,
    private val errorMapper: NetworkErrorMapper,
    private val logger: Logger,
    private val timeProvider: TimeProvider,
) {

    /** Todos los proveedores registrados, ordenados por su prioridad base. */
    private val orderedProviders: List<RateProvider> = providers.sortedBy { it.priority }

    /**
     * Circuito abierto por proveedor: instante (ms) hasta el cual NO se le consulta.
     *
     * `ConcurrentHashMap` y no `HashMap`: esta tabla la escribe la pasada de sincronización
     * (dentro del mutex del repositorio) **y** el diagnóstico de Ajustes (`testAll`, fuera
     * de él, desde otro dispatcher) — el diagnóstico puede cerrar un circuito mientras una
     * pasada lo abre. Con HashMap eso es una condición de carrera real.
     */
    private val openUntilMillis = ConcurrentHashMap<String, Long>()

    suspend fun fetchLatest(preferredProviderId: String? = null): ProviderFetchOutcome =
        fetchWithOrder(availableOrder(preferredProviderId))

    /**
     * Diagnóstico: consulta TODAS las fuentes —también las de circuito abierto, porque el
     * usuario pidió expresamente saber cómo están— y devuelve el estado de cada una con la
     * marca de tiempo del dato más reciente que trajo.
     *
     * Una conexión "sana" que devuelve JSON sin pares utilizables cuenta como fallida.
     */
    suspend fun testAll(preferredProviderId: String? = null): ProviderDiagnostics {
        val order = orderFor(preferredProviderId)
        val statuses = mutableListOf<ProviderStatus>()

        order.forEach { provider ->
            val status = probe(provider)
            // El diagnóstico no decide el breaker: medir no es usar. Pero si el sondeo
            // responde con datos, el circuito se cierra de inmediato (recuperación).
            if (status.isOk) markSuccess(provider.id)
            statuses += status
        }

        return ProviderDiagnostics(statuses = statuses)
    }

    /** Orden efectivo de consulta: preferido primero, resto por prioridad base. */
    private fun orderFor(preferredProviderId: String?): List<RateProvider> {
        val preferred = preferredProviderId
            ?.let { id -> orderedProviders.firstOrNull { it.id == id } }
        return if (preferred == null) {
            orderedProviders
        } else {
            listOf(preferred) + orderedProviders.filter { it !== preferred }
        }
    }

    /**
     * Orden con el breaker aplicado: los de circuito abierto se saltan, salvo que no
     * quede ninguno disponible — entonces se reintenta el orden completo antes que
     * devolver una pasada vacía.
     */
    private fun availableOrder(preferredProviderId: String?): List<RateProvider> {
        val order = orderFor(preferredProviderId)
        val now = timeProvider.nowMillis()
        val available = order.filter { provider ->
            val openUntil = openUntilMillis[provider.id] ?: return@filter true
            openUntil <= now
        }
        return available.ifEmpty { order }
    }

    private suspend fun fetchWithOrder(order: List<RateProvider>): ProviderFetchOutcome {
        val resolved = LinkedHashMap<String, ResolvedRate>()
        val providerIds = mutableListOf<String>()
        val failures = mutableListOf<ProviderFailure>()
        val fetchedAtMillis = mutableListOf<Long>()
        // Una sola lectura del reloj por pasada: todas las comparaciones de frescura de
        // esta pasada usan el mismo instante.
        val nowMillis = timeProvider.nowMillis()

        for (provider in order) {
            // Corte de pasada: solo se detiene cuando están las dos tasas resueltas Y
            // frescas. Si una resuelta está rancia (banco sin publicar), se sigue
            // consultando proveedores por si alguien trae algo más reciente.
            if (!needsMoreData(resolved, nowMillis)) break

            fetchSafely { provider.fetchLatest() }
                .onSuccess { remoteSet ->
                    fetchedAtMillis += remoteSet.fetchedAtMillis
                    if (remoteSet.rates.isNotEmpty()) markSuccess(provider.id)

                    var contributed = false
                    remoteSet.rates.forEach { rate ->
                        if (rate.sourceKey !in SourceKeys.all) return@forEach

                        // Una fecha en el futuro no es "más reciente": es un dato roto (alguien
                        // sirvió una tasa fechada para un día que aún no llegó — en vivo, el
                        // 2026-10-11, el CDN de DolarVZLA servía el cierre del día 13). Se
                        // degrada a lectura sin hora y compite sin ventaja de frescura.
                        val candidate = rate.withTrustedTimestamp(nowMillis)
                        val current = resolved[candidate.sourceKey]
                        // Gana el dato más fresco (igualando a quien publica primero y degradando
                        // al que responde con el cierre de un día pasado), salvo que la tasa sea
                        // autoritativa: entonces su dueño manda mientras responda (ADR 40).
                        val wins = when {
                            current == null -> true
                            isAuthoritativeSource(provider.id, candidate.sourceKey) &&
                                !isAuthoritativeSource(current.providerId, candidate.sourceKey) -> true
                            isAuthoritativeSource(current.providerId, candidate.sourceKey) -> false
                            else -> isNewer(candidate.updatedAtMillis, current.rate.updatedAtMillis) ||
                                acceptsUnstamped(candidate, current, nowMillis)
                        }
                        if (wins) {
                            resolved[candidate.sourceKey] = ResolvedRate(rate = candidate, providerId = provider.id)
                            contributed = true
                        }
                    }
                    if (contributed) providerIds += provider.id
                }
                .onFailure { throwable ->
                    markFailure(provider.id)
                    failures += ProviderFailure(providerId = provider.id, error = errorMapper.map(throwable))
                    logger.warn(TAG, "Proveedor ${provider.id} falló: ${throwable.message}", throwable)
                }
        }

        return ProviderFetchOutcome(
            rates = resolved.values.toList(),
            providerIds = providerIds.toList(),
            failures = failures.toList(),
            fetchedAtMillis = fetchedAtMillis.maxOrNull() ?: 0L,
        )
    }

    /** Sondeo de un proveedor para el diagnóstico: cómo quedó, qué trajo y de cuándo. */
    private suspend fun probe(provider: RateProvider): ProviderStatus {
        return try {
            val remoteSet = provider.fetchLatest()
            val sources = remoteSet.rates
                .filter { it.sourceKey in SourceKeys.all }
                .map { it.sourceKey }
                .distinct()
            val lastUpdatedAt = remoteSet.rates.mapNotNull { it.updatedAtMillis }.maxOrNull()
            ProviderStatus(
                providerId = provider.id,
                isOk = remoteSet.rates.isNotEmpty(),
                sources = sources,
                lastUpdatedAtMillis = lastUpdatedAt,
                error = null,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.warn(TAG, "Diagnóstico: ${provider.id} no respondió: ${exception.message}", exception)
            ProviderStatus(
                providerId = provider.id,
                isOk = false,
                sources = emptyList(),
                lastUpdatedAtMillis = null,
                error = ProviderFailure(providerId = provider.id, error = errorMapper.map(exception)),
            )
        }
    }

    /**
     * `true` si todavía tiene sentido gastar otro proveedor: falta una tasa, o una de las
     * resueltas está más vieja que el umbral de frescura de su fuente. Con la marca de
     * tiempo desconocida (Yadio no publica) no se supone rancio: no se fuerza consulta.
     */
    private fun needsMoreData(resolved: Map<String, ResolvedRate>, nowMillis: Long): Boolean {
        val missing = SourceKeys.all.any { it !in resolved }
        if (missing) return true
        // Un titular autoritativo no se persigue por frescura (ADR 40): si su proveedor
        // respondió, su valor es el oficial aunque tenga días — el BCV no publica en todos
        // los días bancarios, y seguir buscando "algo más nuevo" es acabar mostrando un
        // dato ajeno con la etiqueta del oficial.
        return resolved.values.any { entry ->
            !isAuthoritativeSource(entry.providerId, entry.rate.sourceKey) && isStale(entry.rate, nowMillis)
        }
    }

    /** `true` si [providerId] es la referencia declarada de [sourceKey] (ADR 40). */
    private fun isAuthoritativeSource(providerId: String, sourceKey: String): Boolean =
        orderedProviders.firstOrNull { it.id == providerId }
            ?.authoritativeSources
            ?.contains(sourceKey) == true

    /**
     * `true` cuando el recién llegado sirve aunque no traiga hora.
     *
     * [isNewer] exige que el candidato tenga hora, así que una fuente que responda en vivo sin
     * publicar marca de tiempo (Yadio si algún día dejara de mandar su `timestamp`, o cualquier
     * proveedor que solo publique el valor) quedaba fuera: la pasada la consultaba **justo
     * porque** el dato resuelto estaba rancio y acto seguido descartaba su respuesta, dejando
     * el cierre viejo en pantalla con una fuente sana respondiendo en vivo. Solo aplica sobre
     * un titular rancio: una lectura sin hora no puede pisar a un dato que sí está fresco.
     */
    private fun acceptsUnstamped(
        candidate: RemoteRate,
        current: ResolvedRate,
        nowMillis: Long,
    ): Boolean = candidate.updatedAtMillis == null && isStale(current.rate, nowMillis)

    /** Umbral por fuente: el oficial publica ~1 vez/día; el paralelo, por horas. */
    private fun isStale(rate: RemoteRate, nowMillis: Long): Boolean {
        val updatedAt = rate.updatedAtMillis ?: return false
        val thresholdHours = when (rate.sourceKey) {
            SourceKeys.OFICIAL -> AppConstants.STALE_RATE_HOURS
            else -> AppConstants.STALE_PARALLEL_HOURS
        }
        return nowMillis - updatedAt > thresholdHours * MILLIS_PER_HOUR
    }

    /** `true` si el candidato es estrictamente más reciente que el ya resuelto. */
    private fun isNewer(candidateMillis: Long?, currentMillis: Long?): Boolean =
        candidateMillis != null && (currentMillis == null || candidateMillis > currentMillis)

    /**
     * Baja a `null` (edad desconocida) una marca de tiempo fechada en el futuro.
     *
     * "Más adelante que el reloj" no es más reciente: es un dato roto, y la regla de
     * frescura lo habría impuesto como el más nuevo de todos (así intentó entrar el cierre
     * del 13 de octubre publicado por un CDN un domingo 11). El valor se conserva —es lo
     * mejor que hay— pero sin ventaja de su fecha y sin que la UI pinte un día inexistente.
     * La tolerancia cubre un desfase de reloj de minutos, que sí es normal.
     */
    private fun RemoteRate.withTrustedTimestamp(nowMillis: Long): RemoteRate {
        val updatedAt = updatedAtMillis ?: return this
        if (updatedAt <= nowMillis + FUTURE_TIMESTAMP_TOLERANCE_MILLIS) return this
        return copy(updatedAtMillis = null)
    }

    private fun markFailure(providerId: String) {
        openUntilMillis[providerId] = timeProvider.nowMillis() + AppConstants.PROVIDER_COOLDOWN_MILLIS
    }

    private fun markSuccess(providerId: String) {
        openUntilMillis.remove(providerId)
    }

    /**
     * Aísla el fallo de un proveedor sin romper la cancelación estructurada
     * (`CancellationException` siempre se re-lanza).
     */
    private suspend fun <T> fetchSafely(block: suspend () -> T): kotlin.Result<T> = try {
        kotlin.Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        kotlin.Result.failure(exception)
    }

    private companion object {
        const val TAG = "ProviderRegistry"
        const val MILLIS_PER_HOUR = 3_600_000L

        /** Un reloj desfasado minutos es normal; una tasa fechada días adelante, no. */
        const val FUTURE_TIMESTAMP_TOLERANCE_MILLIS = 2L * MILLIS_PER_HOUR
    }
}
