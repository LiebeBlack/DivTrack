package com.liebeblack.divtrack.domain.repository

import com.liebeblack.divtrack.domain.model.UpdateDownload

/**
 * Puerto de descarga en segundo plano.
 *
 * El dominio declara la intención («baja este APK a la carpeta privada de la app y dime
 * cómo va»); `:data` la implementa con el gestor de descargas del sistema. La elección del
 * gestor no es un detalle: es el único mecanismo que **sobrevive al cierre de la app** sin
 * permisos nuevos ni servicio en primer plano, que es exactamente lo que se pidió
 * («descarga en segundo plano»).
 */
interface AppDownloader {

    /**
     * Encola [url] en la carpeta privada de la app. Devuelve el id con el que el sistema
     * identifica la descarga; lanza si el sistema rechaza la petición (URL inválida, sin
     * espacio, gestor no disponible) para que el repositorio lo clasifique.
     */
    suspend fun enqueue(url: String, fileName: String, title: String): Long

    /** Estado actual de la descarga [downloadId]. */
    suspend fun stateOf(downloadId: Long): UpdateDownload

    /**
     * Elimina la descarga **y su archivo**. Se usa para no dejar en el dispositivo el APK de
     * una versión que ya no es la publicada.
     */
    suspend fun remove(downloadId: Long)
}

/**
 * Versión del APK en ejecución.
 *
 * Es un puerto (y no una lectura directa de `BuildConfig`) porque el dato tiene que ser el
 * del paquete realmente instalado, no el de la compilación que alguien leyó del catálogo:
 * la comparación decide si se ofrece o no una actualización, y un `versionName` equivocado
 * produce justo el fallo silencioso que este módulo viene a evitar.
 */
interface InstalledAppVersionProvider {

    /** `versionName` del paquete instalado; cadena vacía si el sistema no lo puede leer. */
    fun versionName(): String
}
