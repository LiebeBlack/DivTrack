package com.liebeblack.divtrack.data.worker

import com.liebeblack.divtrack.core.common.error.DataError

/**
 * ¿Vale la pena reintentar este fallo?
 *
 * Es la misma frontera que aplica `RetryInterceptor` en la capa de red (ADR 22), y por eso
 * vive en un solo sitio: **solo se reintenta lo que puede cambiar por esperar**. Un proveedor
 * caído o una conexión que falla sí; un 404 (endpoint cambiado), una respuesta ilegible, una
 * base que rechaza la escritura o un proveedor que devuelve un envoltorio de error dan el
 * mismo resultado treinta segundos después, así que reintentarlos solo gasta radio y batería.
 *
 * Antes esta regla estaba escrita dentro de `RateSyncWorker`; el worker de comprobación de
 * versión necesita exactamente la misma, y tenerla duplicada era la forma segura de que las
 * dos versiones se separaran con el tiempo.
 */
internal fun DataError.isWorthRetrying(): Boolean = when (this) {
    is DataError.Network, is DataError.Timeout -> true

    is DataError.Http -> code == HTTP_REQUEST_TIMEOUT ||
        code == HTTP_TOO_MANY_REQUESTS ||
        code in HTTP_SERVER_ERROR_RANGE

    is DataError.Parse,
    is DataError.EmptyCache,
    is DataError.Storage,
    is DataError.Unknown,
    -> false
}

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
