package com.liebeblack.divtrack.data.fake

import android.database.sqlite.SQLiteException
import com.liebeblack.divtrack.core.database.datasource.RateLocalDataSource
import com.liebeblack.divtrack.core.database.entity.CurrentRateEntity
import com.liebeblack.divtrack.core.database.entity.RateHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Room simulado en memoria.
 *
 * Reproduce lo que de verdad importa para el repositorio: la escritura de una sincronización
 * entra por la transacción [commitRateSync], las escrituras son idempotentes por clave (como
 * el `@Upsert` de Room) y la lectura es un `Flow` que re-emite al cambiar.
 *
 * Los interruptores `failReads` / `failWrites` / `failPrune` imitan un almacenamiento que
 * rechaza operaciones, para fijar el comportamiento del repositorio cuando Room dice que no.
 * Lanzan `SQLiteException` real: es el mismo tipo que Room deja escapar en Android, y su
 * constructor sobrevive intacto en el jar mockeable de los tests JVM (la llamada a `super`
 * se conserva; solo se descarta el cuerpo).
 */
class FakeRateLocalDataSource : RateLocalDataSource {

    val currentRates = MutableStateFlow<List<CurrentRateEntity>>(emptyList())
    val dailyCloses = MutableStateFlow<List<RateHistoryEntity>>(emptyList())

    var commitRateSyncCalls: Int = 0
        private set

    /** Cuando es `true`, leer una fila lanza como una base ilegible. */
    var failReads: Boolean = false

    /** Cuando es `true`, el commit lanza como una base que rechaza la escritura. */
    var failWrites: Boolean = false

    /**
     * Fallo **no clasificado** del almacenamiento: cualquier cosa que no sea la
     * `SQLiteException` que el repositorio ya sabe traducir (base cerrada a mitad, error del
     * motor). Sirve para fijar que la frontera del repositorio no deja escapar excepciones.
     */
    var unexpectedFailure: Throwable? = null

    /** Cuando es `true`, la poda lanza como un borrado que no se pudo aplicar. */
    var failPrune: Boolean = false

    override fun observeCurrentRates(): Flow<List<CurrentRateEntity>> = currentRates

    override fun observeCurrentRate(source: String): Flow<CurrentRateEntity?> =
        currentRates.map { list -> list.firstOrNull { it.source == source } }

    override suspend fun currentRate(source: String): CurrentRateEntity? {
        failIfAsked(failReads)
        return currentRates.value.firstOrNull { it.source == source }
    }

    override suspend fun previousClose(source: String, epochDay: Long): RateHistoryEntity? {
        failIfAsked(failReads)
        return dailyCloses.value
            .filter { it.source == source && it.epochDay < epochDay }
            .maxByOrNull { it.epochDay }
    }

    override suspend fun pruneDailyCloses(beforeEpochDay: Long): Int {
        failIfAsked(failPrune)
        val before = dailyCloses.value.size
        dailyCloses.value = dailyCloses.value.filter { it.epochDay >= beforeEpochDay }
        return before - dailyCloses.value.size
    }

    /** Espeja la semántica transaccional de `RateDao.commitRateSync`. */
    override suspend fun commitRateSync(rates: List<CurrentRateEntity>, closes: List<RateHistoryEntity>) {
        if (rates.isEmpty()) return
        unexpectedFailure?.let { failure -> throw failure }
        failIfAsked(failWrites)
        commitRateSyncCalls++

        val bySource = currentRates.value.associateBy { it.source }.toMutableMap()
        rates.forEach { rate -> bySource[rate.source] = rate }
        currentRates.value = bySource.values.toList()

        if (closes.isNotEmpty()) {
            val byKey = dailyCloses.value.associateBy { it.source to it.epochDay }.toMutableMap()
            closes.forEach { close -> byKey[close.source to close.epochDay] = close }
            dailyCloses.value = byKey.values.toList()
        }
    }

    private fun failIfAsked(shouldFail: Boolean) {
        if (shouldFail) throw SQLiteException("fallo simulado del almacenamiento local")
    }
}
