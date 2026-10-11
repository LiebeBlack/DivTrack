package com.liebeblack.divtrack.core.network.service

import com.liebeblack.divtrack.core.network.dto.ExchangeRateApiDto
import retrofit2.http.GET

/**
 * ExchangeRate-API (open endpoint). Base en `BuildConfig.EXCHANGERATEAPI_BASE_URL`.
 *
 * Es el tercer estilo de lectura del proyecto y el primero que es **independiente de los
 * proveedores venezolanos**: publica el tipo de cambio oficial USD->VES desde fuentes
 * oficiales, de modo que sigue vivo aunque toda la pila DolarAPI/Yadio caiga a la vez
 * (comparten upstream y los cortes de ruta hacia Venezuela los han tumbado a ambos).
 */
interface ExchangeRateApiService {

    @GET("v6/latest/USD")
    suspend fun getLatestUsd(): ExchangeRateApiDto
}
