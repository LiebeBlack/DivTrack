package com.liebeblack.divtrack.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.liebeblack.divtrack.R
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.repository.RateRepository
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano que muestra una notificación persistente con las tasas de cambio.
 *
 * La notificación muestra siempre la tasa oficial y, si el usuario lo habilita, también la
 * tasa paralelo. El servicio se inicia/detiene según la preferencia del usuario.
 */
@AndroidEntryPoint
class RateNotificationService : Service() {

    @Inject
    lateinit var rateRepository: RateRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeRates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Cargando...", "Cargando..."))
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun observeRates() {
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                rateRepository.observeCurrentRate(RateSource.OFICIAL),
                rateRepository.observeCurrentRate(RateSource.PARALELO),
                settingsRepository.observeSettings(),
            ) { official, parallel, settings ->
                Triple(official, parallel, settings)
            }.onEach { (official, parallel, settings) ->
                val officialText = official?.value?.toString() ?: "---"
                val parallelText = parallel?.value?.toString() ?: "---"

                // Actualizar notificación solo si el servicio está activo
                if (settings.enablePersistentNotification) {
                    updateNotification(officialText, parallelText, settings.hideUnofficialRate)
                } else {
                    // Detener servicio si el usuario desactivó la preferencia
                    stopSelf()
                }
            }.launchIn(serviceScope)
        }
    }

    private fun buildNotification(official: String, parallel: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Oficial: $official Bs/USD")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildNotificationText(official, parallel, false))
            )
            .build()
    }

    private fun updateNotification(official: String, parallel: String, hideUnofficial: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Oficial: $official Bs/USD")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildNotificationText(official, parallel, hideUnofficial))
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotificationText(
        official: String,
        parallel: String,
        hideUnofficial: Boolean,
    ): String {
        return buildString {
            append("Oficial: $official Bs/USD")
            if (!hideUnofficial) {
                append("\nParalelo: $parallel Bs/USD")
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "rate_notification_channel"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, RateNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RateNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
