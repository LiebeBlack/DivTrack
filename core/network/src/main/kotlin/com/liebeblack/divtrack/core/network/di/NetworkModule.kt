package com.liebeblack.divtrack.core.network.di

import android.content.Context
import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.network.BuildConfig
import com.liebeblack.divtrack.core.network.interceptor.CacheFallbackInterceptor
import com.liebeblack.divtrack.core.network.interceptor.HeadersInterceptor
import com.liebeblack.divtrack.core.network.interceptor.LoggingInterceptorFactory
import com.liebeblack.divtrack.core.network.interceptor.RetryInterceptor
import com.liebeblack.divtrack.core.network.logging.LogcatLogger
import com.liebeblack.divtrack.core.network.monitor.AndroidConnectivityObserver
import com.liebeblack.divtrack.core.network.monitor.ConnectivityObserver
import com.liebeblack.divtrack.core.network.provider.DolarApiProvider
import com.liebeblack.divtrack.core.network.provider.DolarVzlaProvider
import com.liebeblack.divtrack.core.network.provider.ExchangeRateApiProvider
import com.liebeblack.divtrack.core.network.provider.RateProvider
import com.liebeblack.divtrack.core.network.provider.YadioProvider
import com.liebeblack.divtrack.core.network.service.DolarApiService
import com.liebeblack.divtrack.core.network.service.DolarVzlaService
import com.liebeblack.divtrack.core.network.service.ExchangeRateApiService
import com.liebeblack.divtrack.core.network.service.GithubReleasesService
import com.liebeblack.divtrack.core.network.service.YadioService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Grafo de red. Un único `OkHttpClient` (pool de conexiones y caché compartidos) y cinco
 * instancias de Retrofit que solo cambian de `baseUrl`: DolarAPI, Yadio, ExchangeRate-API,
 * DolarVZLA y la API de GitHub del módulo de actualizaciones.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val JSON_MEDIA_TYPE = "application/json"
    private const val HTTP_CACHE_DIRECTORY = "http_rates"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Los proveedores agregan campos con frecuencia: nunca romper por un campo nuevo.
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideHttpCache(@ApplicationContext context: Context): Cache = Cache(
        directory = File(context.cacheDir, HTTP_CACHE_DIRECTORY),
        maxSize = AppConstants.HTTP_CACHE_BYTES,
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(cache: Cache): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(cache)
            // `callTimeout` es el techo de la petición COMPLETA (conexión + reintentos +
            // lectura). Es la garantía que evita el clásico "la app se quedó pensando":
            // sin él, tres intentos con timeouts de 8/12 s podían tener la pantalla girando
            // más de medio minuto antes de decir que no había conexión.
            .callTimeout(AppConstants.HTTP_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(AppConstants.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConstants.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConstants.HTTP_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HeadersInterceptor())
            // El orden importa: CacheFallback envuelve a Retry, de modo que si todos los
            // reintentos fallan, todavía se puede responder desde la caché en disco.
            .addInterceptor(CacheFallbackInterceptor())
            .addInterceptor(RetryInterceptor())

        // Logging solo en debug (null en release: ni coste ni filtrado de datos).
        LoggingInterceptorFactory.create(BuildConfig.DEBUG)?.let(builder::addInterceptor)

        return builder.build()
    }

    @Provides
    @Singleton
    @DolarApiRetrofit
    fun provideDolarApiRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.DOLARAPI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    @YadioRetrofit
    fun provideYadioRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.YADIO_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    @ExchangeRateApiRetrofit
    fun provideExchangeRateApiRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.EXCHANGERATEAPI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    @DolarVzlaRetrofit
    fun provideDolarVzlaRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.DOLARVZLA_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    @GithubRetrofit
    fun provideGithubRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.GITHUB_API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGithubReleasesService(@GithubRetrofit retrofit: Retrofit): GithubReleasesService =
        retrofit.create(GithubReleasesService::class.java)

    @Provides
    @Singleton
    fun provideDolarApiService(@DolarApiRetrofit retrofit: Retrofit): DolarApiService =
        retrofit.create(DolarApiService::class.java)

    @Provides
    @Singleton
    fun provideYadioService(@YadioRetrofit retrofit: Retrofit): YadioService =
        retrofit.create(YadioService::class.java)

    @Provides
    @Singleton
    fun provideExchangeRateApiService(@ExchangeRateApiRetrofit retrofit: Retrofit): ExchangeRateApiService =
        retrofit.create(ExchangeRateApiService::class.java)

    @Provides
    @Singleton
    fun provideDolarVzlaService(@DolarVzlaRetrofit retrofit: Retrofit): DolarVzlaService =
        retrofit.create(DolarVzlaService::class.java)

    // --- Multi-proveedor: cada implementación entra en el SET de RateProvider ---

    @Provides
    @Singleton
    @IntoSet
    fun provideDolarApiRateProvider(
        service: DolarApiService,
        timeProvider: TimeProvider,
    ): RateProvider = DolarApiProvider(service = service, timeProvider = timeProvider)

    @Provides
    @Singleton
    @IntoSet
    fun provideYadioRateProvider(
        service: YadioService,
        timeProvider: TimeProvider,
    ): RateProvider = YadioProvider(service = service, timeProvider = timeProvider)

    @Provides
    @Singleton
    @IntoSet
    fun provideExchangeRateApiRateProvider(
        service: ExchangeRateApiService,
        timeProvider: TimeProvider,
    ): RateProvider = ExchangeRateApiProvider(service = service, timeProvider = timeProvider)

    @Provides
    @Singleton
    @IntoSet
    fun provideDolarVzlaRateProvider(
        service: DolarVzlaService,
        timeProvider: TimeProvider,
    ): RateProvider = DolarVzlaProvider(service = service, timeProvider = timeProvider)
}

/** Bindings de interfaces a implementaciones con constructor inyectable. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindLogger(impl: LogcatLogger): Logger
}
