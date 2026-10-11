package com.liebeblack.divtrack.presentation.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Copia texto al portapapeles del sistema (API de Android, sin deprecaciones de Compose). */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Abre la pantalla de ajustes de red del sistema.
 *
 * Es la salida honesta cuando no hay conectividad: un aviso de "sin conexión" que no ofrece
 * nada que hacer deja al usuario atascado. Aquí se le lleva justo al sitio donde puede
 * arreglarlo (datos móviles, wifi, modo avión).
 *
 * No necesita ningún permiso: se lanza una Activity del sistema. Además se prueban dos
 * acciones en orden, porque hay ROMs que no exponen `ACTION_WIRELESS_SETTINGS`; si la
 * primera no resuelve, se cae a los ajustes generales.
 */
fun Context.openNetworkSettings() {
    val actions = listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
    actions.forEach { action ->
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

/**
 * Abre una dirección **http/https** en el navegador del sistema.
 *
 * El filtro de esquema no es decorativo: la URL viene del release publicado por la API de
 * GitHub, y una dirección remota no es una fuente de confianza para decidir qué `Intent`
 * lanza la app. Con `http`/`https` como únicos esquemas aceptados, lo peor que puede pasar
 * es que el usuario acabe en una página web; sin ese filtro, un `intent://` o un
 * `file://` abrirían componentes arbitrarios del dispositivo.
 *
 * Nunca lanza: si no hay navegador o la dirección no es válida, no se hace nada. El usuario
 * sigue teniendo el botón de descarga, que es el camino que importa.
 */
fun Context.openUrl(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in ALLOWED_URL_SCHEMES) return

    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private val ALLOWED_URL_SCHEMES = setOf("http", "https")
