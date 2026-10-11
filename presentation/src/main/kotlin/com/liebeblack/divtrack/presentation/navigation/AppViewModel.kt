package com.liebeblack.divtrack.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureUpdateCheckScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel del shell de la app.
 *
 * Observa preferencias por encima de la navegación para aplicar el tema y presentar la
 * bienvenida de primer uso hasta que su confirmación quede persistida en DataStore.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val ensureSyncScheduled: EnsureSyncScheduledUseCase,
    private val ensureUpdateCheckScheduled: EnsureUpdateCheckScheduledUseCase,
) : ViewModel() {

    val settings: StateFlow<UserSettings?> = observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val _isCompletingWelcome = MutableStateFlow(false)
    val isCompletingWelcome = _isCompletingWelcome.asStateFlow()

    private val _welcomeSaveFailed = MutableStateFlow(false)
    val welcomeSaveFailed = _welcomeSaveFailed.asStateFlow()

    private var welcomeSaveCommitted = false

    init {
        // El trabajo periódico se alinea con lo que digan las preferencias al arrancar.
        viewModelScope.launch { ensureSyncScheduled() }

        // La comprobación de versión es independiente de la sincronización de tasas: un
        // trabajo diario propio, sin notificaciones, que solo escribe el resultado en
        // DataStore para que Ajustes lo cuente cuando el usuario entre.
        viewModelScope.launch { ensureUpdateCheckScheduled() }
    }

    fun completeWelcome() {
        if (
            settings.value?.welcomeCompleted == true ||
            welcomeSaveCommitted ||
            _isCompletingWelcome.value
        ) {
            return
        }

        _isCompletingWelcome.value = true
        _welcomeSaveFailed.value = false
        viewModelScope.launch {
            try {
                updateSettings.setWelcomeCompleted(true)
                welcomeSaveCommitted = true
            } catch (_: IOException) {
                _welcomeSaveFailed.value = true
            } finally {
                _isCompletingWelcome.value = false
            }
        }
    }
}
