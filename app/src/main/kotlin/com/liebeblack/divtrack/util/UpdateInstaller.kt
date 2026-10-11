package com.liebeblack.divtrack.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.liebeblack.divtrack.R
import com.liebeblack.divtrack.core.common.utils.AppConstants
import java.io.File

/**
 * Lanza el instalador del sistema con el APK que el gestor de descargas dejó en la carpeta
 * privada de la app.
 *
 * **Por qué esto vive en `:app`.** El archivo está en `Android/data/<paquete>/files/Download/`
 * y para que otro proceso (el instalador) pueda leerlo hace falta un `content://` con permiso
 * temporal: eso es `FileProvider`, y su autoridad y sus rutas se declaran en el manifiesto,
 * que solo tiene `:app`. `:presentation` pide la instalación con un efecto y este módulo la
 * ejecuta.
 *
 * **Qué no hace.** No instala nada por su cuenta: lanza `ACTION_VIEW` con el MIME de APK y
 * Android muestra su propia pantalla de confirmación. Sustituirse a sí misma en silencio
 * requeriría ser *device owner* o tener root, y no es algo que una app de tasas deba hacer ni
 * pueda prometer.
 *
 * **Las dos autorizaciones que sí hacen falta** (y que antes faltaban, dejando un botón que no
 * podía funcionar):
 *
 * 1. `REQUEST_INSTALL_PACKAGES` declarado en el manifiesto. Sin él, desde Android 8 una app
 *    que apunta a API > 25 no puede pedir la instalación.
 * 2. El ajuste «instalar apps desconocidas» de **esta** app. Es una decisión del usuario y
 *    puede estar apagada, así que se consulta con `canRequestPackageInstalls()`: si está
 *    apagada no se lanza un intent que el sistema va a rechazar, se lleva al usuario al
 *    ajuste exacto y se le dice qué hacer. Al volver, el botón vuelve a funcionar.
 *
 * **Defensa.** El nombre del archivo viene de una URL remota, así que se rechaza cualquier
 * nombre con separadores de ruta: la instalación nunca puede apuntar fuera de la carpeta de
 * descargas de la app.
 */
fun Context.installDownloadedUpdate(fileName: String) {
    val file = downloadedApkFile(fileName)
    if (file == null) {
        showInstallMessage(R.string.settings_update_apk_missing)
        return
    }

    // Android 8+: instalar exige que el usuario haya autorizado a ESTA app como fuente. Sin
    // esa autorización el instalador del sistema rechaza el intento, así que se comprueba
    // antes y se lleva al usuario al ajuste exacto en lugar de dejar un botón mudo.
    if (!canRequestPackageInstalls()) {
        showInstallMessage(R.string.settings_update_allow_install)
        openUnknownSourcesSettings()
        return
    }

    val uri = try {
        // Misma autoridad que declara el manifiesto: `${applicationId}.fileprovider`.
        FileProvider.getUriForFile(this, "$packageName$PROVIDER_AUTHORITY_SUFFIX", file)
    } catch (_: IllegalArgumentException) {
        // El archivo existe pero cae fuera de los caminos declarados en `file_paths.xml`:
        // sin URI no hay instalación posible, y decirlo es mejor que un botón mudo.
        showInstallMessage(R.string.settings_update_apk_missing)
        return
    }

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, AppConstants.APK_MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    if (runCatching { startActivity(intent) }.isFailure) {
        showInstallMessage(R.string.settings_update_no_installer)
    }
}

/**
 * ¿El usuario autorizó a esta app a instalar paquetes? (`Android 8+`)
 *
 * Si la consulta falla —no debería: el permiso está declarado en el manifiesto— se responde
 * `true` y se intenta la instalación igual. Bloquear por un fallo de diagnóstico sería peor
 * que dejar que sea el instalador del sistema quien decida.
 *
 * (Es la única app que se instala a sí misma: no se comprueba nada de otros paquetes.)
 */
private fun Context.canRequestPackageInstalls(): Boolean =
    runCatching { packageManager.canRequestPackageInstalls() }.getOrDefault(true)

/** Lleva al ajuste «instalar apps desconocidas» de esta app, no a los ajustes generales. */
private fun Context.openUnknownSourcesSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        .setData(Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.downloadedApkFile(fileName: String): File? {
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null

    val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
    return File(directory, fileName).takeIf { candidate -> candidate.isFile }
}

private fun Context.showInstallMessage(messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
}

private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
