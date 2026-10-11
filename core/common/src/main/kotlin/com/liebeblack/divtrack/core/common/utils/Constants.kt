package com.liebeblack.divtrack.core.common.utils

/** Constantes compartidas por todas las capas. Sin contexto de Android. */
object AppConstants {

    /** Impuesto a las Grandes Transacciones Financieras (alícuota vigente). */
    const val IGTF_RATE: Double = 0.03

    /** Etiqueta del IGTF lista para UI/copiar al portapapeles. */
    const val IGTF_LABEL: String = "3 %"

    /** WorkManager no permite periodicidad menor a 15 minutos. */
    const val SYNC_MIN_INTERVAL_MINUTES: Int = 15
    const val SYNC_DEFAULT_INTERVAL_MINUTES: Int = 240

    /**
     * Retención del cierre diario en Room (años).
     *
     * Solo se guardan dos filas por día (una por fuente) y se necesitan para calcular la
     * tendencia respecto al cierre anterior; dos años sobran y acotan la tabla.
     */
    const val DAILY_CLOSE_KEEP_YEARS: Long = 2

    const val DATABASE_NAME: String = "divtrack.db"

    /** Caché HTTP en disco: 10 MB alcanzan de sobra para dos endpoints pequeños. */
    const val HTTP_CACHE_BYTES: Long = 10L * 1024L * 1024L

    /** Hora legal de Venezuela: todas las "fechas de cierre" se calculan aquí. */
    const val TIME_ZONE_ID: String = "America/Caracas"

    /** Locale de formateo (coma decimal, punto de miles). */
    const val LOCALE_TAG: String = "es-VE"

    /** Reintentos máximos del interceptor de red. */
    const val HTTP_MAX_RETRIES: Int = 2

    /**
     * Tiempo máximo **total** de una petición (conexión + reintentos incluidos).
     *
     * Sin este techo, un proveedor que acepta la conexión y luego no responde encadenaba
     * tres intentos de 10 s de conexión + 15 s de lectura y dejaba la pantalla girando casi
     * un minuto. Con él, el peor caso está acotado y el error llega a tiempo para ser útil.
     */
    const val HTTP_CALL_TIMEOUT_SECONDS: Long = 20

    const val HTTP_CONNECT_TIMEOUT_SECONDS: Long = 8
    const val HTTP_READ_TIMEOUT_SECONDS: Long = 12
    const val HTTP_WRITE_TIMEOUT_SECONDS: Long = 8

    /**
     * Frescura esperada del dato de una fuente.
     *
     * El caso a cubrir: DolarAPI responde 200 con JSON perfecto **pero el BCV dejó de
     * publicar** y la API sirve el cierre de ayer. Ese dato no es "actual": hay que
     * mostrarlo (es lo mejor disponible), pero señalando su edad y buscando quién sí
     * publicó algo más reciente.
     *
     * - [STALE_RATE_HOURS]: el oficial (BCV) publica ~1 vez/día; más de un día entero sin
     *   actualizar huele a banco caído. El paralelo se actualiza por horas; el umbral
     *   compartido es deliberadamente conservador para no marcar ruido.
     * - [STALE_PARALLEL_HOURS]: umbral más estricto solo para el paralelo, que publica
     *   varias veces al día.
     */
    const val STALE_RATE_HOURS: Long = 24
    const val STALE_PARALLEL_HOURS: Long = 12

    /**
     * Tiempo desde el último intento a partir del cual volver a una pantalla dispara una
     * pasada sola.
     *
     * Quince minutos es el intervalo mínimo que admite WorkManager y, a la vez, la frecuencia
     * con la que la gente vuelve a la app: dentro de esa ventana, lo que se ve ya es una
     * comprobación de hace un momento. Vive aquí, y no en cada ViewModel, para que el panel y
     * la calculadora no puedan divergir aplicando la misma regla con números distintos.
     */
    const val RATE_RESUME_REFRESH_AFTER_MILLIS: Long = 15L * 60L * 1000L

    // --- Módulo de actualizaciones (Sistema de Actualización Inteligente) ---

    /**
     * Ventana mínima entre comprobaciones de versión **automáticas**.
     *
     * La comprobación consulta la API pública de GitHub, que limita a 60 peticiones por
     * hora y por IP sin autenticar. Con esta ventana, entrar y salir de Ajustes (o el
     * arranque repetido de la app) no gasta cuota; el botón «Buscar actualización» del
     * usuario sí la ignora a propósito, porque ahí la respuesta es lo que se pidió.
     */
    const val UPDATE_CHECK_MIN_INTERVAL_MILLIS: Long = 6L * 60L * 60L * 1000L

    /**
     * Periodicidad del trabajo de comprobación en segundo plano.
     *
     * Un día es el ritmo real de publicación de este proyecto, y es también lo que hace
     * que la comprobación de fondo no sea un latido de red.
     */
    const val UPDATE_CHECK_INTERVAL_HOURS: Long = 24L

    /** Tipo MIME de un APK; lo usan el descargador (`:data`) y el instalador (`:app`). */
    const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"

    /**
     * Circuit breaker de proveedores.
     *
     * Si una fuente falla, no se vuelve a molestar durante este periodo: su siguiente turno
     * en la cola se salta y la pasada no espera su timeout. Cumplido el cooldown, la fuente
     * se reintenta automáticamente (medio circuito) y, si responde, vuelve al servicio
     * normal. Sin estado persistente: un reinicio de proceso reabre todos los circuitos,
     * que es el comportamiento deseado (el fallo más común es de red, no del proveedor).
     */
    const val PROVIDER_COOLDOWN_MILLIS: Long = 10L * 60L * 1000L
}

/**
 * Claves canónicas de cada tasa. Se usan como identificador en Room, en los proveedores
 * remotos y en los mapeos, de modo que un proveedor nuevo no obliga a tocar el esquema.
 */
object SourceKeys {
    const val OFICIAL: String = "oficial"
    const val PARALELO: String = "paralelo"

    val all: List<String> = listOf(OFICIAL, PARALELO)
}

/**
 * Proveedores remotos, sus ids estables y su orden de relevancia.
 *
 * El `id` viaja por Room y por las preferencias: cambiar el texto obligaría a migrar datos.
 * El orden de prioridad de la pasada vive aquí para que sea única fuente de verdad
 * (debe coincidir con el [providerPriority] de cada implementación de red).
 */
object ProviderIds {
    const val DOLARAPI: String = "DolarAPI"
    const val YADIO: String = "Yadio"
    const val EXCHANGERATEAPI: String = "ExchangeRateAPI"

    /** Prioridad (menor = se consulta primero). Debe reflejar las constantes de red. */
    val providerPriority: Map<String, Int> = mapOf(
        DOLARAPI to 0,
        YADIO to 10,
        EXCHANGERATEAPI to 20,
    )

    /** Orden de consulta: DolarAPI → Yadio → ExchangeRateAPI. */
    val ordered: List<String> = providerPriority.entries.sortedBy { it.value }.map { it.key }
}
