package com.liebeblack.divtrack.core.common.error

/**
 * Taxonomía de fallos de datos. Vive en el núcleo puro para que dominio, datos y UI
 * hablen el mismo idioma sin acoplarse a Retrofit/OkHttp/Room.
 *
 * El mapeo desde excepciones reales ocurre en `:core:network` (`NetworkErrorMapper`).
 */
sealed interface DataError {

    val message: String?
    val cause: Throwable?

    /** Sin conectividad / host inalcanzable (`UnknownHostException`, `ConnectException`). */
    data class Network(
        override val message: String? = "Sin conexión a internet",
        override val cause: Throwable? = null,
    ) : DataError

    /** La red tardó más de lo permitido (`SocketTimeoutException`). */
    data class Timeout(
        override val message: String? = "La conexión tardó demasiado",
        override val cause: Throwable? = null,
    ) : DataError

    /** El proveedor respondió con un código de error. */
    data class Http(
        val code: Int,
        override val message: String? = "Respuesta HTTP $code del proveedor",
        override val cause: Throwable? = null,
    ) : DataError

    /** La respuesta no se pudo deserializar o venía incompleta. */
    data class Parse(
        override val message: String? = "Respuesta inválida del proveedor",
        override val cause: Throwable? = null,
    ) : DataError

    /** No hay nada cacheado todavía y la red falló: la UI no tiene nada que mostrar. */
    data class EmptyCache(
        override val message: String? = "Sin datos guardados todavía",
        override val cause: Throwable? = null,
    ) : DataError

    /**
     * La base local rechazó una lectura o una escritura (disco lleno, archivo ilegible,
     * conexión cerrada). No viene de la red: la app sigue en pie y la UI lo cuenta en vez de
     * caerse; los datos anteriores se quedan en pantalla.
     */
    data class Storage(
        override val message: String? = "La base de datos local no pudo guardar los datos",
        override val cause: Throwable? = null,
    ) : DataError

    /** Error no clasificado. */
    data class Unknown(
        override val message: String? = "Ocurrió un error inesperado",
        override val cause: Throwable? = null,
    ) : DataError
}
