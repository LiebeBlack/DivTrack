package com.liebeblack.divtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.liebeblack.divtrack.presentation.navigation.DivTrackApp
import com.liebeblack.divtrack.util.appVersionName
import com.liebeblack.divtrack.util.installDownloadedUpdate
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de la app.
 *
 * No hay Fragments ni navegación por XML: Compose + Navigation 3 son dueños de la pantalla.
 * `enableEdgeToEdge()` dibuja bajo las barras del sistema y deja que cada pantalla aplique
 * su propio inset, que es la forma correcta de hacerlo en Android 15+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Se resuelve una sola vez, no en cada recomposición.
        val appVersion = applicationContext.appVersionName()

        // El instalador del APK descargado: es lo único que no puede vivir en
        // `:presentation`, porque necesita el `FileProvider` de este módulo (el dueño del
        // manifiesto y de la carpeta privada de descargas).
        val onInstallUpdate: (String) -> Unit = { fileName ->
            applicationContext.installDownloadedUpdate(fileName)
        }

        setContent {
            DivTrackApp(appVersion = appVersion, onInstallUpdate = onInstallUpdate)
        }
    }
}
