package com.liebeblack.divtrack.core.database.datasource

import com.liebeblack.divtrack.core.database.DivTrackDatabase
import com.liebeblack.divtrack.core.database.entity.CurrentRateEntity
import com.liebeblack.divtrack.core.database.entity.RateHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de persistencia que consume `:data`.
 *
 * Existe como interfaz (y no se inyecta el DAO directamente) por dos razones:
 * 1. `RateRepositoryImpl` se puede testear en JVM pura con un fake en memoria.
 * 2. La capa de datos no depende de Room en su lógica, solo del contrato.
 */
interface RateLocalDataSource {

    fun observeCurrentRates(): Flow<List<CurrentRateEntity>>

    fun observeCurrentRate(source: String): Flow<CurrentRateEntity?>

    suspend fun currentRate(source: String): CurrentRateEntity?

    suspend fun previousClose(source: String, epochDay: Long): RateHistoryEntity?

    suspend fun pruneDailyCloses(beforeEpochDay: Long): Int

    /**
     * Escritura atómica de la sincronización: tasas vigentes + cierres diarios en una sola
     * transacción. Ver `RateDao.commitRateSync` para el porqué.
     *
     * Es la **única** escritura del contrato a propósito: al no existir métodos sueltos para
     * escribir tasas o cierres por separado, ninguna ruta futura puede reintroducir el estado
     * intermedio (tasas nuevas con cierres viejos) que esta transacción elimina.
     */
    suspend fun commitRateSync(rates: List<CurrentRateEntity>, closes: List<RateHistoryEntity>)
}

/** Implementación real sobre Room. */
@Singleton
class RoomRateLocalDataSource @Inject constructor(
    database: DivTrackDatabase,
) : RateLocalDataSource {

    private val dao = database.rateDao()

    override fun observeCurrentRates(): Flow<List<CurrentRateEntity>> = dao.observeCurrentRates()

    override fun observeCurrentRate(source: String): Flow<CurrentRateEntity?> = dao.observeCurrentRate(source)

    override suspend fun currentRate(source: String): CurrentRateEntity? = dao.currentRate(source)

    override suspend fun previousClose(source: String, epochDay: Long): RateHistoryEntity? =
        dao.previousClose(source, epochDay)

    override suspend fun pruneDailyCloses(beforeEpochDay: Long): Int =
        dao.pruneDailyCloses(beforeEpochDay)

    override suspend fun commitRateSync(rates: List<CurrentRateEntity>, closes: List<RateHistoryEntity>) {
        if (rates.isEmpty()) return
        dao.commitRateSync(rates, closes)
    }
}
