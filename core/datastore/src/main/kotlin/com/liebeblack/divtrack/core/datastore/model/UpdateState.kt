package com.liebeblack.divtrack.core.datastore.model

/**
 * Estado persistido del módulo de actualizaciones.
 *
 * Igual que [UserPreferences], es primitivo a propósito: `:core:datastore` no conoce el
 * dominio, y el mapeo a los tipos del dominio vive en `:data`.
 *
 * **Qué se guarda y qué no.** Solo lo que se necesita para no volver a preguntar y para no
 * perder la descarga en curso si el proceso muere:
 *
 * - El resultado de la **última comprobación que respondió** ([latestVersion],
 *   [checkedAtMillis] y los datos del release). Una comprobación fallida no escribe nada:
 *   así una caída de red no congela el «no hay actualizaciones» durante seis horas.
 * - La identidad de la **descarga** en el gestor del sistema ([downloadId],
 *   [downloadFileName]) y si ya terminó ([downloadReady]). El archivo lo descarga el
 *   sistema, así que sobrevive a que la app se cierre o la mate el fabricante; guardar el
 *   id es lo que permite volver a Ajustes y encontrar la descarga donde estaba.
 */
data class UpdateState(
    /** Versión publicada más reciente, normalizada ("4.7"). Vacío = nunca se comprobó. */
    val latestVersion: String = "",

    /** Título del release, tal como lo publica CI ("DivTrack 4.7 (latest)"). */
    val latestTitle: String = "",

    /**
     * Página del release (GitHub). Es lo que la pantalla abre para que el usuario lea las
     * novedades: el cuerpo del release viene en Markdown y guardarlo para no pintarlo sería
     * datos muertos, así que se enlaza en lugar de almacenarse.
     */
    val latestPageUrl: String = "",

    /** URL de descarga directa del APK principal (nunca el de DivTrack Lite). */
    val latestDownloadUrl: String = "",

    /** Tamaño del APK en bytes; 0 si GitHub no lo informó. */
    val latestSizeBytes: Long = 0L,

    /**
     * Instante (ms) de publicación del release; 0 si no se pudo leer.
     *
     * Se guarda porque la pregunta «¿esta versión es de hoy o de hace un mes?» es
     * exactamente lo que el usuario se hace antes de descargar un APK.
     */
    val latestPublishedAtMillis: Long = 0L,

    /**
     * Instante (ms) de la última comprobación **exitosa**. `0` = ninguna.
     *
     * Es la marca de frescura de la ventana de comprobación y lo que la pantalla enseña
     * como «última comprobación»; por eso solo la escriben las respuestas de verdad.
     */
    val checkedAtMillis: Long = 0L,

    /** Id de la descarga en `DownloadManager`. `0` = no hay ninguna descarga asociada. */
    val downloadId: Long = 0L,

    /** Nombre del archivo descargado dentro de la carpeta privada de la app. */
    val downloadFileName: String = "",

    /** `true` cuando la descarga terminó y el APK está listo para lanzar el instalador. */
    val downloadReady: Boolean = false,
)
