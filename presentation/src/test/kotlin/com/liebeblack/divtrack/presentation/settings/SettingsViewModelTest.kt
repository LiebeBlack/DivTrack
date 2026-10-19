package com.liebeblack.divtrack.presentation.settings

import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.usecase.DiagnoseProvidersUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.fake.FakeRateRepository
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.fake.FakeSyncScheduler
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val scheduler = FakeSyncScheduler()

    @Test
    fun `el estado refleja las preferencias guardadas`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        val state = viewModel.state.value
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(RateSource.OFICIAL, state.defaultSource)
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
    fun `cambiar notificacion persistente persiste el valor`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetEnablePersistentNotification(true))
        advanceUntilIdle()
        assertEquals(true, settingsRepository.settings.value.enablePersistentNotification)
        assertEquals(true, viewModel.state.value.enablePersistentNotification)
    }

    @Test
    fun `cambiar ocultar tasa no oficial persiste el valor`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetHideUnofficialRate(true))
        advanceUntilIdle()
        assertEquals(true, settingsRepository.settings.value.hideUnofficialRate)
        assertEquals(true, viewModel.state.value.hideUnofficialRate)
    }

    @Test
    fun `cambiar tasas personalizadas persiste los valores`() = runTest {
        val viewModel = createViewModel()
        collectState(viewModel)

        viewModel.onIntent(SettingsIntent.SetCustomOfficialRate(50.0))
        viewModel.onIntent(SettingsIntent.SetCustomParallelRate(60.0))
        advanceUntilIdle()

        assertEquals(50.0, settingsRepository.settings.value.customOfficialRate, 0.0)
        assertEquals(60.0, settingsRepository.settings.value.customParallelRate, 0.0)
        assertEquals(50.0, viewModel.state.value.customOfficialRate, 0.0)
        assertEquals(60.0, viewModel.state.value.customParallelRate, 0.0)
    }

    private fun TestScope.createViewModel() = SettingsViewModel(
        observeSettings = ObserveSettingsUseCase(settingsRepository),
        updateSettings = UpdateSettingsUseCase(settingsRepository),
        ensureSyncScheduled = EnsureSyncScheduledUseCase(settingsRepository, scheduler),
        diagnoseProviders = DiagnoseProvidersUseCase(rateRepository),
    )

    /** En `backgroundScope`: la recolección no termina nunca y `runTest` no debe esperarla. */
    private fun TestScope.collectState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { }
        }
        advanceUntilIdle()
    }
}
