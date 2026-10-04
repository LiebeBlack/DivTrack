package com.liebeblack.divtrack.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * Fábrica del *open helper* de Room que convierte una base ilegible en una base nueva.
 *
 * **Por qué existe.** Si el archivo SQLite se daña —un apagado a mitad de escritura, un
 * almacenamiento lleno, una restauración mal hecha—, la primera lectura lanza
 * `SQLiteException` dentro del `Flow` que observan los ViewModel y la app cae al abrir. La
 * lectura no puede «seguir con lo que había»: el archivo entero es ilegible.
 *
 * ADR 16 prohíbe perder datos en silencio, así que aquí la pérdida es explícita y avisada:
 *
 * 1. **Verificación previa.** Si el archivo existe y `PRAGMA quick_check` no lo da por sano
 *    (o ni se puede abrir), se borra antes de que Room lo toque y se enciende
 *    [DatabaseRebuildTracker] para que la UI lo cuente.
 * 2. **Red de seguridad.** La configuración que se entrega al helper lleva
 *    `allowDataLossOnRecovery = true`: si la comprobación previa pasó pero la apertura falla
 *    (corrupción que solo se ve al abrir, permisos, etc.), AndroidX borra el archivo y
 *    reintenta con una base nueva. Esa segunda ruta no emite aviso —no hay gancho público
 *    para saber que ocurrió— pero evita que la app se caiga.
 *
 * Un fallo que **no** sea corrupción (disco lleno, permisos) no borra nada: primero se agota
 * el reintento de la capa 2 y, si también falla, se propaga el error para que la UI lo cuente
 * con sus propios mensajes.
 *
 * **Nota de rendimiento.** La verificación previa corre una sola vez por proceso, cuando Hilt
 * construye la base (al crear el primer ViewModel). En un archivo sano son unos milisegundos
 * de apertura y `quick_check`; en uno dañado, el recorrido termina en el primer error. Es el
 * precio de poder avisar con certeza: una comprobación posterior llegaría cuando Room ya
 * intentó abrir el archivo.
 */
class RebuildingOpenHelperFactory(
    private val context: Context,
    private val tracker: DatabaseRebuildTracker,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
        val name = configuration.name
        if (name != null) {
            val file = databaseFile(name, configuration.useNoBackupDirectory)
            if (isCorrupt(file)) {
                SQLiteDatabase.deleteDatabase(file)
                tracker.markRebuilt()
            }
        }

        val recoverable = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(configuration.callback)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(true)
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(recoverable)
    }

    /**
     * `quick_check` es la verificación ligera de SQLite: recorre la estructura del archivo y
     * devuelve `ok` cuando está sana. Se abre en modo lectura/escritura a propósito: un
     * archivo con WAL pendiente necesita recuperación y abrirlo en solo-lectura daría un
     * falso positivo de corrupción.
     */
    private fun isCorrupt(file: File): Boolean {
        if (!file.exists()) return false

        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { database ->
                    database.rawQuery(QUICK_CHECK, null).use { cursor ->
                        cursor.moveToFirst() && cursor.getString(0) != OK
                    }
                }
        } catch (_: SQLiteDatabaseCorruptException) {
            true
        } catch (_: SQLiteException) {
            // No es corrupción (disco, permisos): no se toca el archivo.
            false
        }
    }

    private fun databaseFile(name: String, useNoBackupDirectory: Boolean): File =
        if (useNoBackupDirectory) {
            File(context.noBackupFilesDir, name)
        } else {
            context.getDatabasePath(name)
        }

    private companion object {
        const val QUICK_CHECK = "PRAGMA quick_check(1)"
        const val OK = "ok"
    }
}
