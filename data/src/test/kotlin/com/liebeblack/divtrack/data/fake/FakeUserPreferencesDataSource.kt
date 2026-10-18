package com.liebeblack.divtrack.data.fake

import com.liebeblack.divtrack.core.datastore.UserPreferencesDataSource
import com.liebeblack.divtrack.core.datastore.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** DataStore simulado: un `MutableStateFlow` con los mismos setters del contrato. */
class FakeUserPreferencesDataSource(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesDataSource {

    val state = MutableStateFlow(initial)

    /**
     * Cuando es `true`, leer las preferencias lanza. Simula el fallo que no pasa por el
     * `catch` de `IOException` del datasource real: el repositorio tiene que degradar al
     * orden automático, no quedarse sin pasada.
     */
    var failReads: Boolean = false

    override val preferences: Flow<UserPreferences>
        get() = if (failReads) {
            flow { throw IllegalStateException("preferencias ilegibles") }
        } else {
            state
        }

    override suspend fun setThemeMode(themeMode: String) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun setDefaultSource(sourceKey: String) {
        state.value = state.value.copy(defaultSourceKey = sourceKey)
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        state.value = state.value.copy(autoSyncEnabled = enabled)
    }

    override suspend fun setSyncIntervalMinutes(minutes: Int) {
        state.value = state.value.copy(syncIntervalMinutes = minutes)
    }

    override suspend fun setPreferredProvider(providerId: String) {
        state.value = state.value.copy(preferredProviderId = providerId)
    }

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        state.value = state.value.copy(syncOnWifiOnly = enabled)
    }

    override suspend fun setWelcomeCompleted(completed: Boolean) {
        state.value = state.value.copy(welcomeCompleted = completed)
    }

    override suspend fun setShowParallelRate(enabled: Boolean) {
        state.value = state.value.copy(showParallelRate = enabled)
    }
}
