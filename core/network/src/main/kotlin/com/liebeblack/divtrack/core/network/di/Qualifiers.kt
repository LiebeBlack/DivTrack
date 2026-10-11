package com.liebeblack.divtrack.core.network.di

import javax.inject.Qualifier

/** Retrofit apuntando a DolarAPI Venezuela (oficial + paralelo). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DolarApiRetrofit

/** Retrofit apuntando a Yadio (fallback del paralelo). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YadioRetrofit

/** Retrofit apuntando a ExchangeRate-API (fallback independiente del oficial). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExchangeRateApiRetrofit

/**
 * Retrofit apuntando al CDN estático de DolarVZLA (tasa oficial del BCV).
 *
 * Es el único apuntador cuyo host no ejecuta una API: sirve archivos JSON ya generados.
 * Para OkHttp es indiferente, y el separador de configuración (otra `baseUrl`) evita que
 * un cambio de dominio por ahí toque a los demás proveedores.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DolarVzlaRetrofit

/**
 * Retrofit apuntando a la API pública de GitHub, donde vive el APK publicado.
 *
 * Es un host distinto de los cuatro proveedores de tasas, y eso es deliberado: una caída de
 * los proveedores venezolanos no debe impedir saber si hay una versión nueva de la app.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubRetrofit
