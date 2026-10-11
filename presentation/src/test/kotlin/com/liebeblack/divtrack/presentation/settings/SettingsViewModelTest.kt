package com.liebeblack.divtrack.presentation.settings

import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.AppRelease
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UpdateAvailability
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.usecase.CheckForUpdatesUseCase
import com.liebeblack.divtrack.domain.usecase.DiagnoseProvidersUseCase
import com.liebeblack.divtrack.domain.usecase.DownloadUpdateUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveUpdateStatusUseCase
import com.liebeblack.divtrack.domain.usecase.PollUpdateDownloadUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.fake.FakeRateRepository
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.fake.FakeSyncScheduler
import com.liebeblack.divtrack.presentation.fake.FakeUpdateRepository
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ajustes: cada cambio se persiste y, cuando toca, reprograma el trabajo periódico. Ese
 * último detalle es el que evita que la preferencia y lo que hace WorkManager se separen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = FakeSettingsRepository()
    private val rateRepository = FakeRateRepository()
    private val updateRepository = FakeUpdateRepository()
    private val scheduler = FakeSyncScheduler()

    @Test
    fun `el estado refleja las preferencias guardadas`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        val state = viewModel.state.value
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(RateSource.OFICIAL, state.defaultSource)
        assertEquals(false, state.igtfEnabled)
        assertEquals(true, state.autoSyncEnabled)
    }

    @Test
    fun `cambiar el tema persiste el modo elegido`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SelectTheme(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, settingsRepository.settings.value.themeMode)
        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun `cambiar la frecuencia guarda el intervalo y reprograma el trabajo`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetSyncInterval(60))
        advanceUntilIdle()

        assertEquals(60, settingsRepository.settings.value.syncIntervalMinutes)
        assertEquals(60, scheduler.scheduledIntervalMinutes)
    }

    @Test
    fun `apagar la sincronizacion automatica cancela el trabajo periodico`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetAutoSync(false))
        advanceUntilIdle()

        assertEquals(false, settingsRepository.settings.value.autoSyncEnabled)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(null, scheduler.scheduledIntervalMinutes)
    }

    @Test
    fun `activar solo wifi guarda la preferencia y reprograma con la restriccion`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetWifiOnly(true))
        advanceUntilIdle()

        assertEquals(true, settingsRepository.settings.value.syncOnWifiOnly)
        assertEquals(true, scheduler.scheduledWifiOnly)
    }

    @Test
    fun `elegir proveedor preferido persiste el id y automatico lo limpia`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SelectProvider("Yadio"))
        advanceUntilIdle()
        assertEquals("Yadio", settingsRepository.settings.value.defaultProviderId)

        viewModel.onIntent(SettingsIntent.SelectProvider(null))
        advanceUntilIdle()
        assertEquals(null, settingsRepository.settings.value.defaultProviderId)
    }

    @Test
    fun `comprobar actualizaciones fuerza la consulta y refleja la version nueva`() = runTest {
        updateRepository.checkResult = Result.Success(availableUpdate)

        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        // `force = true`: el botón manual no puede quedarse en la ventana de frescura.
        assertEquals(listOf(true), updateRepository.forcedFlags)
        assertEquals(true, viewModel.state.value.update.isUpdateAvailable)
        assertEquals("4.8", viewModel.state.value.update.latestVersion)
        // Con versión nueva y APK adjunto, hay algo que descargar.
        assertEquals(true, viewModel.state.value.update.canDownload)
    }

    @Test
    fun `si la comprobacion falla se avisa y no se inventa un estado`() = runTest {
        updateRepository.checkResult = Result.Error(DataError.Network())

        val viewModel = createViewModel()
        collectState(viewModel)

        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effect -> effects += effect }
        }
        advanceUntilIdle()

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        assertTrue(effects.any { effect -> effect is SettingsEffect.ShowMessage })
        // El fallo no escribe nada: sigue sin saberse qué versión hay publicada.
        assertEquals(null, viewModel.state.value.update.latestVersion)
        assertEquals(false, viewModel.state.value.update.canDownload)
    }

    @Test
    fun `una descarga en curso se reengancha al abrir la pantalla`() = runTest {
        updateRepository.status.value = availableUpdate.copy(
            download = UpdateDownload(phase = DownloadPhase.RUNNING),
        )
        updateRepository.download = UpdateDownload(
            phase = DownloadPhase.READY,
            fileName = "DivTrack-release.apk",
        )

        val viewModel = createViewModel()
        collectState(viewModel)

        assertEquals(DownloadPhase.READY, viewModel.state.value.update.downloadPhase)
        assertEquals(true, viewModel.state.value.update.canInstall)
    }

    private val availableUpdate = UpdateStatus(
        installedVersion = "4.7",
        latestVersion = "4.8",
        availability = UpdateAvailability.UPDATE_AVAILABLE,
        release = AppRelease(
            versionName = "4.8",
            pageUrl = "https://example.test/releases/latest",
            downloadUrl = "https://example.test/DivTrack-release.apk",
            downloadSizeBytes = 22_000_000L,
        ),
    )

    private fun TestScope.createViewModel() = SettingsViewModel(
        observeSettings = ObserveSettingsUseCase(settingsRepository),
        updateSettings = UpdateSettingsUseCase(settingsRepository),
        ensureSyncScheduled = EnsureSyncScheduledUseCase(settingsRepository, scheduler),
        diagnoseProviders = DiagnoseProvidersUseCase(rateRepository),
        observeUpdateStatus = ObserveUpdateStatusUseCase(updateRepository),
        checkForUpdates = CheckForUpdatesUseCase(updateRepository),
        downloadUpdate = DownloadUpdateUseCase(updateRepository),
        pollUpdateDownload = PollUpdateDownloadUseCase(updateRepository),
    )

    /** En `backgroundScope`: la recolección no termina nunca y `runTest` no debe esperarla. */
    private fun TestScope.collectState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { }
        }
        advanceUntilIdle()
    }
}
