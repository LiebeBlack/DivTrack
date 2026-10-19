package com.liebeblack.divtrack.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de datos del widget con SharedPreferences para acceso instantáneo.
 *
 * Optimizaciones:
 * - Lectura síncrona sin bloqueos (SharedPreferences es thread-safe)
 * - Datos persistidos para renderizado inmediato sin peticiones de red
 * - Memoria mínima: solo 3 valores (rate, timestamp, source)
 * - Singleton thread-safe para uso desde AppWidgetProvider (sin Hilt)
 */
class WidgetDataManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Obtiene la tasa cacheada instantáneamente (sin bloqueos).
     * Libera recursos inmediatamente después de leer.
     */
    fun getCachedRate(): WidgetRateData {
        val rate = prefs.getFloat(KEY_RATE, 0f).toDouble()
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        val source = prefs.getString(KEY_SOURCE, null) ?: "unknown"

        return WidgetRateData(
            rate = rate,
            timestamp = timestamp,
            source = source,
        )
    }

    /**
     * Guarda la tasa en cache para acceso instantáneo futuro.
     * Operación asíncrona recomendada, pero síncrona es aceptable para 3 valores.
     */
    fun saveRate(rate: Double, timestamp: Long, source: String) {
        prefs.edit()
            .putFloat(KEY_RATE, rate.toFloat())
            .putLong(KEY_TIMESTAMP, timestamp)
            .putString(KEY_SOURCE, source)
            .apply()
    }

    /**
     * Limpia la cache (útil para errores o datos obsoletos).
     */
    fun clearCache() {
        prefs.edit()
            .remove(KEY_RATE)
            .remove(KEY_TIMESTAMP)
            .remove(KEY_SOURCE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "rate_widget_prefs"
        private const val KEY_RATE = "rate"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_SOURCE = "source"

        @Volatile
        private var instance: WidgetDataManager? = null

        /**
         * Obtiene la instancia singleton thread-safe.
         * Usado por AppWidgetProvider que no puede usar inyección de dependencias.
         */
        fun getInstance(context: Context): WidgetDataManager {
            return instance ?: synchronized(this) {
                instance ?: WidgetDataManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Modelo de datos ultra-ligero para el widget.
 * Solo 3 propiedades primitivas para minimizar memoria.
 */
data class WidgetRateData(
    val rate: Double,
    val timestamp: Long,
    val source: String,
)
