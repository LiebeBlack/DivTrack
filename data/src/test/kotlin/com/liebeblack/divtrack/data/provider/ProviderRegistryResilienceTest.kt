package com.liebeblack.divtrack.data.provider

import com.liebeblack.divtrack.core.common.logging.NoOpLogger
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.model.RemoteRate
import com.liebeblack.divtrack.core.network.model.RemoteRateSet
import com.liebeblack.divtrack.core.network.provider.RateProvider
import com.liebeblack.divtrack.data.fake.FakeRateProvider
import com.liebeblack.divtrack.data.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resiliencia del registro frente a proveedores caídos y datos rancios:
 *
 *  1. Un proveedor que falla no se vuelve a molestar durante el cooldown (circuit breaker).
 *  2. Cumplido el cooldown se reintenta solo (auto-recuperación).
 *  3. Si TODOS están abiertos, la pasada reintenta el orden completo en vez de rendirse.
 *  4. Gana el dato MÁS FRESCO: el caso real del banco que deja de publicar y de la API
 *     que sigue sirviendo su cierre de ayer mientras otra fuente ya trajo el de hoy.
 *  5. El oficial tiene una sola voz (ADR 40): la referencia no se deja pisar ni por un dato
 *     más fresco, la pasada no la persigue por frescura y, si falla, la cola la respalda.
 *     Además, un dato fechado en el futuro no compite por frescura.
 */
class ProviderRegistryResilienceTest {

    private val clock = FakeTimeProvider()

    private fun registry(vararg providers: RateProvider) = ProviderRegistry(
        providers = providers.toSet(),
        errorMapper = NetworkErrorMapper(),
        logger = NoOpLogger,
        timeProvider = clock,
    )

    @Test
    fun `un proveedor fallido se salta durante el cooldown`() = runTest {
        val caido = FakeRateProvider(id = "DolarAPI", priority = 0, failing = true)
        val relevo = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(rate(SourceKeys.PARALELO, 954.55)),
        )
        val registry = registry(caido, relevo)

        registry.fetchLatest()
        assertEquals(1, caido.fetchLatestCalls)

        // Segunda pasada dentro del cooldown: el circuito abierto no recibe llamadas.
        registry.fetchLatest()
        assertEquals(1, caido.fetchLatestCalls)
        assertEquals(2, relevo.fetchLatestCalls)
    }

    @Test
    fun `cumplido el cooldown el proveedor se reintenta solo`() = runTest {
        val caido = FakeRateProvider(id = "DolarAPI", priority = 0, failing = true)
        val relevo = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(rate(SourceKeys.PARALELO, 954.55)),
        )
        val registry = registry(caido, relevo)

        registry.fetchLatest()
        assertEquals(1, caido.fetchLatestCalls)

        // Dentro del cooldown, el caído se salta (hay relevo disponible)...
        registry.fetchLatest()
        assertEquals(1, caido.fetchLatestCalls)

        // ...y cumplido el periodo vuelve a intentarse sin intervención.
        clock.nowMillis += COOLDOWN_MILLIS + 1
        registry.fetchLatest()
        assertEquals(2, caido.fetchLatestCalls)
    }

    @Test
    fun `un proveedor que vuelve a responder se recupera del circuito abierto`() = runTest {
        val intermitente = FlakyProvider(id = "DolarAPI", priority = 0, failuresBeforeSuccess = 2)
        val registry = registry(intermitente)

        registry.fetchLatest() // Fallo 1: abre el circuito.
        assertEquals(1, intermitente.attempts)

        clock.nowMillis += COOLDOWN_MILLIS + 1
        registry.fetchLatest() // Reintento que falla: falla 2 y reabre.
        assertEquals(2, intermitente.attempts)

        clock.nowMillis += COOLDOWN_MILLIS + 1
        registry.fetchLatest() // Cumplió los fallos: responde y cierra el circuito.
        assertEquals(3, intermitente.attempts)

        clock.nowMillis += COOLDOWN_MILLIS + 1
        registry.fetchLatest() // Circuito cerrado: se consulta sin esperar cooldown.
        assertEquals(4, intermitente.attempts)
    }

    @Test
    fun `si todos estan abiertos se reintenta el orden completo`() = runTest {
        val caidoA = FakeRateProvider(id = "DolarAPI", priority = 0, failing = true)
        val caidoB = FakeRateProvider(id = "Yadio", priority = 10, failing = true)
        val registry = registry(caidoA, caidoB)

        registry.fetchLatest()
        assertEquals(1, caidoA.fetchLatestCalls)
        assertEquals(1, caidoB.fetchLatestCalls)

        // Ambos en cooldown, pero la app no puede quedarse sin pasada: reintenta todo.
        registry.fetchLatest()
        assertEquals(2, caidoA.fetchLatestCalls)
        assertEquals(2, caidoB.fetchLatestCalls)
    }

    @Test
    fun `gana el dato mas fresco aunque llegue de un proveedor de cola`() = runTest {
        // DolarAPI responde "bien" pero con un cierre de hace días: el banco dejó de publicar.
        // Este fake NO declara autoridad (el real sí, ADR 40): aquí se fija la regla de
        // frescura para cualquier fuente normal; el contraste con la referencia está en el
        // test "el oficial de la referencia no lo pisa nadie".
        val conDatoRancio = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 860.18, updatedAtMillis = RANCIO_MILLIS),
            ),
        )
        // ExchangeRate-API, de cola, trae el tipo de cambio de HOY.
        val conDatoFresco = FakeRateProvider(
            id = "ExchangeRateAPI",
            priority = 20,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 861.02, updatedAtMillis = HOY_MILLIS),
            ),
        )

        val outcome = registry(conDatoRancio, conDatoFresco).fetchLatest()

        val oficial = outcome.rates.single { it.rate.sourceKey == SourceKeys.OFICIAL }
        // La frescura manda: el valor mostrado es el de hoy, no el cierre repetido de ayer.
        assertEquals(861.02, oficial.rate.value, DELTA)
        assertEquals("ExchangeRateAPI", oficial.providerId)
    }

    @Test
    fun `una lectura en vivo sin hora reemplaza al dato rancio pero no al fresco`() = runTest {
        // DolarAPI responde: su oficial es de hoy, pero su paralelo es de ayer (el banco
        // dejó de publicar). La pasada sigue adelante justo por ese paralelo rancio.
        val conParaleloRancio = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 860.18, updatedAtMillis = HOY_MILLIS),
                rate(SourceKeys.PARALELO, 940.0, updatedAtMillis = AYER_MILLIS),
            ),
        )
        // Yadio es el upstream del paralelo y no publica marca de tiempo: lo que entrega es
        // una lectura en vivo.
        val enVivo = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(
                rate(SourceKeys.PARALELO, 954.55),
                rate(SourceKeys.OFICIAL, 500.0),
            ),
        )

        val outcome = registry(conParaleloRancio, enVivo).fetchLatest()

        // El cierre rancio se descarta: sin esta regla, la pasada preguntaba a Yadio porque el
        // dato estaba viejo y después tiraba su respuesta, dejando el valor de ayer en pantalla.
        val paralelo = outcome.rates.single { it.rate.sourceKey == SourceKeys.PARALELO }
        assertEquals(954.55, paralelo.rate.value, DELTA)
        assertEquals("Yadio", paralelo.providerId)

        // Sin hora no se puede pisar un dato que sí está fresco.
        val oficial = outcome.rates.single { it.rate.sourceKey == SourceKeys.OFICIAL }
        assertEquals(860.18, oficial.rate.value, DELTA)
        assertEquals("DolarAPI", oficial.providerId)
    }

    @Test
    fun `una pasada no se corta si la tasa resuelta esta rancia`() = runTest {
        val conDatoRancio = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 860.18, updatedAtMillis = RANCIO_MILLIS),
                rate(SourceKeys.PARALELO, 955.71, updatedAtMillis = HOY_MILLIS),
            ),
        )
        val nuncaConsultado = FakeRateProvider(
            id = "ExchangeRateAPI",
            priority = 20,
            rates = listOf(rate(SourceKeys.OFICIAL, 861.02, updatedAtMillis = HOY_MILLIS)),
        )

        registry(conDatoRancio, nuncaConsultado).fetchLatest()

        // El oficial llegó rancio: la pasada NO se corta y pregunta al tercero.
        assertEquals(1, nuncaConsultado.fetchLatestCalls)
    }

    @Test
    fun `el diagnostico expone la edad del dato que trae cada fuente`() = runTest {
        val conDatoRancio = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(rate(SourceKeys.OFICIAL, 860.18, updatedAtMillis = AYER_MILLIS)),
        )
        val sinMarca = FakeRateProvider(
            id = "Yadio",
            priority = 10,
            rates = listOf(rate(SourceKeys.PARALELO, 954.55)),
        )

        val diagnostics = registry(conDatoRancio, sinMarca).testAll()

        val dolarApiStatus = diagnostics.statuses.single { it.providerId == "DolarAPI" }
        assertTrue(dolarApiStatus.isOk)
        assertEquals(AYER_MILLIS, dolarApiStatus.lastUpdatedAtMillis)

        val yadioStatus = diagnostics.statuses.single { it.providerId == "Yadio" }
        assertTrue(yadioStatus.isOk)
        assertEquals(null, yadioStatus.lastUpdatedAtMillis)
    }

    @Test
    fun `el oficial de la referencia no lo pisa nadie, ni con un dato mas fresco`() = runTest {
        // DolarAPI con su regla real: es la referencia del oficial (ADR 40) y sirve el
        // cierre del viernes. Un proveedor de cola trae un valor fechado hoy —más fresco—
        // pero no es la fuente autorizada y su cifra no debe encarecer el dólar mostrado.
        val referencia = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(rate(SourceKeys.OFICIAL, 875.6505, updatedAtMillis = AYER_MILLIS)),
            authoritativeSources = setOf(SourceKeys.OFICIAL),
        )
        val deCola = FakeRateProvider(
            id = "DolarVZLA",
            priority = 30,
            rates = listOf(rate(SourceKeys.OFICIAL, 876.7976, updatedAtMillis = HOY_MILLIS)),
        )

        val outcome = registry(referencia, deCola).fetchLatest()

        val oficial = outcome.rates.single { it.rate.sourceKey == SourceKeys.OFICIAL }
        assertEquals(875.6505, oficial.rate.value, DELTA)
        assertEquals("DolarAPI", oficial.providerId)
    }

    @Test
    fun `si la referencia del oficial falla, la cola lo respalda`() = runTest {
        val caida = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            failing = true,
            authoritativeSources = setOf(SourceKeys.OFICIAL),
        )
        val relevo = FakeRateProvider(
            id = "ExchangeRateAPI",
            priority = 20,
            rates = listOf(rate(SourceKeys.OFICIAL, 861.02, updatedAtMillis = HOY_MILLIS)),
        )

        val outcome = registry(caida, relevo).fetchLatest()

        // La autoridad se gana respondiendo: sin respuesta de la referencia, manda el fresco.
        val oficial = outcome.rates.single { it.rate.sourceKey == SourceKeys.OFICIAL }
        assertEquals(861.02, oficial.rate.value, DELTA)
        assertEquals("ExchangeRateAPI", oficial.providerId)
    }

    @Test
    fun `la pasada se corta con el oficial de la referencia y no gasta la cola`() = runTest {
        // Mismo escenario que "una pasada no se corta si la tasa resuelta esta rancia", pero
        // con la referencia declarada: el cierre rancio de DolarAPI ya no obliga a preguntar
        // a nadie (es el oficial aunque esté viejo) y la cola no se gasta.
        val referencia = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(
                rate(SourceKeys.OFICIAL, 875.6505, updatedAtMillis = RANCIO_MILLIS),
                rate(SourceKeys.PARALELO, 955.71, updatedAtMillis = HOY_MILLIS),
            ),
            authoritativeSources = setOf(SourceKeys.OFICIAL),
        )
        val nuncaConsultado = FakeRateProvider(
            id = "ExchangeRateAPI",
            priority = 20,
            rates = listOf(rate(SourceKeys.OFICIAL, 861.02, updatedAtMillis = HOY_MILLIS)),
        )

        registry(referencia, nuncaConsultado).fetchLatest()

        assertEquals(0, nuncaConsultado.fetchLatestCalls)
    }

    @Test
    fun `un dato fechado en el futuro no compite por frescura`() = runTest {
        // Paralelo de DolarAPI (de hoy) contra el de un proveedor de cola fechado para
        // mañana: la regla de frescura lo habría impuesto a pesar de la fecha imposible.
        val delDia = FakeRateProvider(
            id = "DolarAPI",
            priority = 0,
            rates = listOf(rate(SourceKeys.PARALELO, 940.0, updatedAtMillis = HOY_MILLIS)),
        )
        val delFuturo = FakeRateProvider(
            id = "DolarVZLA",
            priority = 30,
            rates = listOf(
                rate(SourceKeys.PARALELO, 990.0, updatedAtMillis = HOY_MILLIS + 25L * MILLIS_PER_HOUR),
            ),
        )

        val outcome = registry(delDia, delFuturo).fetchLatest()

        val paralelo = outcome.rates.single { it.rate.sourceKey == SourceKeys.PARALELO }
        assertEquals(940.0, paralelo.rate.value, DELTA)
        assertEquals("DolarAPI", paralelo.providerId)
    }

    private fun rate(
        sourceKey: String,
        value: Double,
        updatedAtMillis: Long? = null,
    ) = RemoteRate(
        sourceKey = sourceKey,
        value = value,
        updatedAtMillis = updatedAtMillis,
    )

    /**
     * Proveedor que falla las primeras N veces y luego responde: es la forma
     * determinista de probar la recuperación del breaker sin tocar el reloj.
     */
    private class FlakyProvider(
        override val id: String,
        override val priority: Int,
        private val failuresBeforeSuccess: Int,
    ) : RateProvider {

        var attempts: Int = 0
            private set

        override suspend fun fetchLatest(): RemoteRateSet {
            attempts++
            if (attempts <= failuresBeforeSuccess) throw java.io.IOException("proveedor caído")
            return RemoteRateSet(
                providerId = id,
                rates = listOf(
                    RemoteRate(
                        sourceKey = SourceKeys.OFICIAL,
                        value = 860.18,
                        updatedAtMillis = HOY_MILLIS,
                    ),
                ),
                fetchedAtMillis = 0L,
            )
        }
    }

    private companion object {
        const val DELTA = 0.000001
        const val COOLDOWN_MILLIS = 10L * 60L * 1000L
        const val MILLIS_PER_HOUR = 60L * 60L * 1000L

        // Instantes fijos coherentes con el reloj del fake (2026-06-14T02:40Z ≈ 1.8e12 ms).
        const val HOY_MILLIS = 1_800_000_000_000L
        const val AYER_MILLIS = HOY_MILLIS - 25L * MILLIS_PER_HOUR

        /**
         * Una marca que **sí** está rancio para el oficial, según el umbral real del proyecto.
         *
         * Se deriva de `AppConstants` a propósito: estos dos tests fijan el corte de pasada
         * ("se sigue preguntando mientras una tasa resuelta esté rancio"), y una fecha escrita
         * a mano los dejaba midiendo una regla que ya no existe en cuanto el umbral cambió.
         */
        const val RANCIO_MILLIS =
            HOY_MILLIS - (AppConstants.STALE_RATE_HOURS + 1) * MILLIS_PER_HOUR
    }
}
