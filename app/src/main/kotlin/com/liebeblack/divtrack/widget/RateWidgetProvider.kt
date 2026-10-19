package com.liebeblack.divtrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.liebeblack.divtrack.MainActivity
import com.liebeblack.divtrack.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppWidgetProvider ultra-optimizado para mostrar la tasa del dólar oficial.
 *
 * Principios de optimización:
 * - Cero consumo en segundo plano: solo se activa por WorkManager o acción del usuario
 * - Datos persistidos en SharedPreferences para renderizado instantáneo sin bloqueos
 * - Layout simple sin jerarquías anidadas complejas
 * - Libera memoria inmediatamente después de renderizar
 *
 * NOTA: AppWidgetProviders no pueden usar @AndroidEntryPoint porque Android
 * crea instancias directamente. Se usa una instancia estática de WidgetDataManager.
 */
class RateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Actualizar cada widget individualmente con datos cacheados
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                // Actualización manual: disparar WorkManager
                RateWidgetWorker.enqueueUpdate(context, manual = true)
            }
            ACTION_UPDATE_COMPLETE -> {
                // WorkManager completó la actualización: actualizar UI
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Widget añadido: programar actualización periódica
        RateWidgetWorker.schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Widget eliminado: cancelar trabajo periódico
        RateWidgetWorker.cancelPeriodicUpdates(context)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
    ) {
        // Obtener datos cacheados (instantáneo, sin bloqueos)
        val data = WidgetDataManager.getInstance(context).getCachedRate()

        val views = RemoteViews(context.packageName, R.layout.rate_widget)

        // Título (fijo)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))

        // Monto
        views.setTextViewText(
            R.id.widget_rate,
            if (data.rate > 0) {
                "${DecimalFormat("#,##0.00").format(data.rate)} Bs/USD"
            } else {
                context.getString(R.string.widget_no_data)
            },
        )

        // Hora de actualización
        views.setTextViewText(
            R.id.widget_updated,
            if (data.timestamp > 0) {
                context.getString(R.string.widget_updated, formatTime(data.timestamp))
            } else {
                context.getString(R.string.widget_loading)
            },
        )

        // Tocar el widget abre la app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

        // Botón de actualización manual
        val refreshIntent = Intent(context, RateWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        // Actualizar widget
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    companion object {
        private const val ACTION_REFRESH = "com.liebeblack.divtrack.widget.REFRESH"
        private const val ACTION_UPDATE_COMPLETE = "com.liebeblack.divtrack.widget.UPDATE_COMPLETE"
        private const val EXTRA_WIDGET_ID = "widget_id"

        /**
         * Formatea un timestamp en hora local (HH:mm).
         */
        private fun formatTime(timestampMillis: Long): String {
            if (timestampMillis == 0L) return "--:--"
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestampMillis))
        }

        /**
         * Notifica que un widget específico debe actualizarse (llamado por WorkManager).
         */
        fun notifyUpdateComplete(context: Context, widgetId: Int) {
            val intent = Intent(context, RateWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_COMPLETE
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            context.sendBroadcast(intent)
        }

        /**
         * Actualiza todos los widgets activos (llamado cuando cambia la tasa global).
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, RateWidgetProvider::class.java),
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, RateWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
