package com.liebeblack.divtrack.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.liebeblack.divtrack.core.database.entity.CurrentRateEntity
import com.liebeblack.divtrack.core.database.entity.RateHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a datos de tasas. Las lecturas son `Flow`: Room re-emite al escribir, y esa es
 * la pieza que hace que la UI se actualice sola (online y offline) sin polling.
 */
@Dao
interface RateDao {

    @Query("SELECT * FROM ${CurrentRateEntity.TABLE_NAME}")
    fun observeCurrentRates(): Flow<List<CurrentRateEntity>>

    /** Observa una sola tasa por fuente (para notificación persistente). */
    @Query("SELECT * FROM ${CurrentRateEntity.TABLE_NAME} WHERE source = :source LIMIT 1")
    fun observeCurrentRate(source: String): Flow<CurrentRateEntity?>

    /** Valor vigente puntual: se usa como "cierre anterior" en la primera sincronización. */
    @Query("SELECT * FROM ${CurrentRateEntity.TABLE_NAME} WHERE source = :source LIMIT 1")
    suspend fun currentRate(source: String): CurrentRateEntity?

    @Upsert
    suspend fun upsertCurrentRates(rates: List<CurrentRateEntity>)

    /** Último cierre anterior a hoy para una fuente: alimenta la flecha de tendencia. */
    @Query(
        "SELECT * FROM ${RateHistoryEntity.TABLE_NAME} " +
            "WHERE source = :source AND epoch_day < :epochDay " +
            "ORDER BY epoch_day DESC LIMIT 1",
    )
    suspend fun previousClose(source: String, epochDay: Long): RateHistoryEntity?

    /**
     * Escribe (o corrige) el cierre del día en curso. Idempotente por `(source, epoch_day)`.
     *
     * No hay consulta de lectura de la serie: la pantalla que la usaba ya no existe, así que
     * este DAO no expone ningún `Flow` de cierres.
     */
    @Upsert
    suspend fun upsertDailyCloses(closes: List<RateHistoryEntity>)

    @Query("DELETE FROM ${RateHistoryEntity.TABLE_NAME} WHERE epoch_day < :beforeEpochDay")
    suspend fun pruneDailyCloses(beforeEpochDay: Long): Int

    /**
     * Commit atómico de una sincronización: tasa vigente + cierre diario en UNA transacción.
     *
     * Por qué existe: escribir en dos operaciones separadas deja una ventana donde un kill
     * del proceso produce tasas actuales nuevas con cierres diarios viejos — y la flecha de
     * tendencia (que compara contra el cierre) sale corrupta hasta el día siguiente.
     * Dentro de `@Transaction` o se aplica todo o no se aplica nada.
     */
    @Transaction
    suspend fun commitRateSync(
        rates: List<CurrentRateEntity>,
        closes: List<RateHistoryEntity>,
    ) {
        upsertCurrentRates(rates)
        upsertDailyCloses(closes)
    }
}
