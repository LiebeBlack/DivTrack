package com.liebeblack.divtrack.presentation.fake

import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.SyncSummary
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.repository.RateRepository
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import com.liebeblack.divtrack.domain.scheduler.SyncScheduler
import com.liebeblack.divtrack.domain.scheduler.UpdateCheckScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Repositorio de tasas simulado. Permite guionizar la respuesta de la red sin tocar el
 * ViewModel: cada test decide si la sincronización responde, falla o responde a medias.
 */
class FakeRateRepository : RateRepository {

    val rates = MutableStateFlow<List<ExchangeRate>>(emptyList())

    var refreshResult: Result<SyncSummary> = Result.Success(
        SyncSummary(
            updatedSources = emptyList(),
            providerIds = emptyList(),
            failures = emptyList(),
            fetchedAtMillis = 0L,
        ),
    )
    var refreshCalls: Int = 0
        private set
    var testProvidersCalls: Int = 0
        private set
    var diagnostics: ProviderDiagnostics = ProviderDiagnostics(statuses = emptyList())

    /** La caché de este fake no se daña nunca; los tests que la «reconstruyan» lo simulan. */
    val cacheRebuild = MutableStateFlow(false)

    override fun observeRates(): Flow<List<ExchangeRate>> = rates

    override fun observeCacheRebuild(): Flow<Boolean> = cacheRebuild

    override suspend fun refreshRates(): Result<SyncSummary> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun testProviders(): ProviderDiagnostics {
        testProvidersCalls++
        return diagnostics
    }
}

/** Preferencias simuladas en memoria. */
class FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {

    val settings = MutableStateFlow(initial)

    override fun observeSettings(): Flow<UserSettings> = settings

    override suspend fun setThemeMode(mode: ThemeMode) {
        settings.value = settings.value.copy(themeMode = mode)
    }

    override suspend fun setDefaultSource(source: RateSource) {
        settings.value = settings.value.copy(defaultSource = source)
    }

    override suspend fun setIgtfEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(igtfEnabled = enabled)
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(autoSyncEnabled = enabled)
    }

    override suspend fun setSyncInterval(minutes: Int) {
        settings.value = settings.value.copy(syncIntervalMinutes = minutes)
    }

    override suspend fun setDefaultProvider(providerId: String?) {
        settings.value = settings.value.copy(defaultProviderId = providerId)
    }

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        settings.value = settings.value.copy(syncOnWifiOnly = enabled)
    }

    override suspend fun setWelcomeCompleted(completed: Boolean) {
        settings.value = settings.value.copy(welcomeCompleted = completed)
    }

    override suspend fun setShowParallelRate(enabled: Boolean) {
        settings.value = settings.value.copy(showParallelRate = enabled)
    }
}

/**
 * Repositorio de actualizaciones simulado.
 *
 * Guioniza la comprobación igual que `FakeRateRepository`: cada test decide si el release
 * responde, si es más nuevo o si la comprobación falla, y anota con qué se llamó (para poder
 * afirmar que el botón manual **fuerza** la consulta y la automática no).
 */
class FakeUpdateRepository(
    initial: UpdateStatus = UpdateStatus(installedVersion = DEFAULT_INSTALLED_VERSION),
) : UpdateRepository {

    val status = MutableStateFlow(initial)

    var checkResult: Result<UpdateStatus> = Result.Success(initial)
    var downloadResult: Result<Long> = Result.Success(DOWNLOAD_ID)

    /** Lo que devuelve el sondeo del gestor de descargas. */
    var download: UpdateDownload = UpdateDownload()

    var checkCalls: Int = 0
        private set

    /** Valores de `force` con los que se llamó a la comprobación, en orden. */
    val forcedFlags: MutableList<Boolean> = mutableListOf()

    var downloadCalls: Int = 0
        private set

    override fun observeStatus(): Flow<UpdateStatus> = status

    override suspend fun checkForUpdates(force: Boolean): Result<UpdateStatus> {
        checkCalls++
        forcedFlags += force

        val result = checkResult
        if (result is Result.Success) status.value = result.data
        return result
    }

    override suspend fun downloadUpdate(): Result<Long> {
        downloadCalls++
        return downloadResult
    }

    override suspend fun pollDownload(): UpdateDownload = download

    companion object {
        const val DEFAULT_INSTALLED_VERSION: String = "4.7"
        const val DOWNLOAD_ID: Long = 42L
    }
}

/**
 * Programador simulado de la comprobación de versión.
 *
 * No hay contador de cancelaciones porque el contrato no ofrece ninguna: comprobar la versión
 * una vez al día es gratis y el trabajo solo se reprograma, nunca se apaga.
 */
class FakeUpdateCheckScheduler : UpdateCheckScheduler {

    var scheduleCalls: Int = 0
        private set

    override suspend fun ensureScheduled() {
        scheduleCalls++
    }
}

/** Programador simulado: registra lo que WorkManager habría hecho. */
class FakeSyncScheduler : SyncScheduler {

    var scheduledIntervalMinutes: Int? = null
        private set
    var scheduledWifiOnly: Boolean? = null
        private set
    var cancelCalls: Int = 0
        private set

    override suspend fun schedulePeriodic(intervalMinutes: Int, wifiOnly: Boolean) {
        scheduledIntervalMinutes = intervalMinutes
        scheduledWifiOnly = wifiOnly
    }

    override suspend fun cancelPeriodic() {
        cancelCalls++
    }
}
