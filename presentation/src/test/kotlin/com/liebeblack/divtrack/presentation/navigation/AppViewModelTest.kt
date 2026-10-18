package com.liebeblack.divtrack.presentation.navigation

import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.fake.FakeSettingsRepository
import com.liebeblack.divtrack.presentation.fake.FakeSyncScheduler
import com.liebeblack.divtrack.presentation.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Arranque de la app: el trabajo periódico de tasas se alinea con las preferencias.
 *
 * No hace falta colectar el estado: el flujo de preferencias del shell es `Eagerly` y el
 * `init` corre en `viewModelScope` (el dispatcher de test), así que `advanceUntilIdle` deja
 * pasar la programación antes de afirmar nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `al arrancar se programa el trabajo de tasas`() = runTest {
        val syncScheduler = FakeSyncScheduler()

        createViewModel(syncScheduler = syncScheduler)
        advanceUntilIdle()

        assertNotNull(syncScheduler.scheduledIntervalMinutes)
    }

    @Test
    fun `con la sincronizacion apagada el trabajo de tasas se cancela`() = runTest {
        val syncScheduler = FakeSyncScheduler()

        createViewModel(
            settings = UserSettings(autoSyncEnabled = false),
            syncScheduler = syncScheduler,
        )
        advanceUntilIdle()

        assertEquals(1, syncScheduler.cancelCalls)
    }

    private fun createViewModel(
        settings: UserSettings = UserSettings(),
        syncScheduler: FakeSyncScheduler = FakeSyncScheduler(),
    ): AppViewModel {
        val settingsRepository = FakeSettingsRepository(initial = settings)
        return AppViewModel(
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            updateSettings = UpdateSettingsUseCase(settingsRepository),
            ensureSyncScheduled = EnsureSyncScheduledUseCase(settingsRepository, syncScheduler),
        )
    }
}
