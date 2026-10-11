package com.liebeblack.divtrack.presentation.navigation

import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureUpdateCheckScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.fake.FakeSyncScheduler
import com.liebeblack.divtrack.presentation.fake.FakeUpdateCheckScheduler
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Arranque de la app: los dos trabajos de fondo quedan programados y son **independientes**
 * —apagar la sincronización de tasas no apaga la comprobación de versión—, que es lo que
 * justifica que sean dos programaciones y no una.
 *
 * No hace falta colectar el estado: el flujo de preferencias del shell es `Eagerly` y el
 * `init` corre en `viewModelScope` (el dispatcher de test), así que `advanceUntilIdle` deja
 * pasar las dos programaciones antes de afirmar nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `al arrancar se programan las tasas y la comprobacion de version`() = runTest {
        val syncScheduler = FakeSyncScheduler()
        val updateScheduler = FakeUpdateCheckScheduler()

        createViewModel(syncScheduler = syncScheduler, updateScheduler = updateScheduler)
        advanceUntilIdle()

        assertNotNull(syncScheduler.scheduledIntervalMinutes)
        assertEquals(1, updateScheduler.scheduleCalls)
    }

    @Test
    fun `con la sincronizacion apagada el trabajo de tasas se cancela y el de version no`() =
        runTest {
            val syncScheduler = FakeSyncScheduler()
            val updateScheduler = FakeUpdateCheckScheduler()

            createViewModel(
                settings = UserSettings(autoSyncEnabled = false),
                syncScheduler = syncScheduler,
                updateScheduler = updateScheduler,
            )
            advanceUntilIdle()

            assertEquals(1, syncScheduler.cancelCalls)
            assertEquals(1, updateScheduler.scheduleCalls)
        }

    private fun createViewModel(
        settings: UserSettings = UserSettings(),
        syncScheduler: FakeSyncScheduler = FakeSyncScheduler(),
        updateScheduler: FakeUpdateCheckScheduler = FakeUpdateCheckScheduler(),
    ): AppViewModel {
        val settingsRepository = FakeSettingsRepository(initial = settings)
        return AppViewModel(
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            updateSettings = UpdateSettingsUseCase(settingsRepository),
            ensureSyncScheduled = EnsureSyncScheduledUseCase(settingsRepository, syncScheduler),
            ensureUpdateCheckScheduled = EnsureUpdateCheckScheduledUseCase(updateScheduler),
        )
    }
}
