package com.liebeblack.divtrack.data.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.liebeblack.divtrack.domain.repository.InstalledAppVersionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `versionName` del paquete realmente instalado, leído del `PackageManager`.
 *
 * Es la misma fuente que usa la pantalla de Ajustes para mostrar la versión: leerla aquí
 * garantiza que la comparación de versiones se hace contra el APK que el usuario tiene
 * delante, y no contra lo que diga un catálogo de compilación.
 *
 * Nunca lanza: si el paquete no se puede consultar devuelve cadena vacía y el módulo de
 * actualizaciones responde `UNKNOWN` (no «estás al día», que sería una afirmación falsa).
 */
@Singleton
class PackageManagerInstalledAppVersion @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppVersionProvider {

    override fun versionName(): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}
