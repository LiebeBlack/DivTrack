package com.liebeblack.divtrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import com.liebeblack.divtrack.presentation.navigation.DivTrackApp
import com.liebeblack.divtrack.service.RateNotificationService
import com.liebeblack.divtrack.util.appVersionName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Única Activity de la app.
 *
 * No hay Fragments ni navegación por XML: Compose + Navigation 3 son dueños de la pantalla.
 * `enableEdgeToEdge()` dibuja bajo las barras del sistema y deja que cada pantalla aplique
 * su propio inset, que es la forma correcta de hacerlo en Android 15+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            RateNotificationService.startService(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            settingsRepository.observeSettings()
                .map { it.enablePersistentNotification }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            RateNotificationService.startService(applicationContext)
                        }
                    } else {
                        RateNotificationService.stopService(applicationContext)
                    }
                }
        }

        // Se resuelve una sola vez, no en cada recomposición.
        val appVersion = applicationContext.appVersionName()

        setContent {
            DivTrackApp(appVersion = appVersion)
        }
    }
}
