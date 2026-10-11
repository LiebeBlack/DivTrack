package com.liebeblack.divtrack.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.presentation.calculator.CalculatorRoute
import com.liebeblack.divtrack.presentation.components.WelcomeScreen
import com.liebeblack.divtrack.presentation.dashboard.DashboardRoute
import com.liebeblack.divtrack.presentation.settings.SettingsRoute
import com.liebeblack.divtrack.presentation.theme.DivTrackTheme

/**
 * Punto de entrada de la UI.
 *
 * El tema se aplica aquí, por encima de la navegación, y se alimenta de DataStore: cambiar
 * el modo en Ajustes repinta la app al instante sin reiniciar nada.
 *
 * @param appVersion versión instalada, inyectada desde `:app` (único módulo que conoce
 *   `BuildConfig`), solo para mostrarla en la pantalla de ajustes.
 * @param onInstallUpdate lanza el instalador del sistema con el APK ya descargado. Vive en
 *   `:app` porque necesita su `FileProvider`; `:presentation` solo pide que ocurra.
 */
@Composable
fun DivTrackApp(
    appVersion: String = "",
    onInstallUpdate: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val themeMode = settings?.themeMode ?: ThemeMode.SYSTEM
    val welcomeCompleted = settings?.welcomeCompleted
    val isCompletingWelcome by viewModel.isCompletingWelcome.collectAsStateWithLifecycle()
    val welcomeSaveFailed by viewModel.welcomeSaveFailed.collectAsStateWithLifecycle()

    DivTrackTheme(themeMode = themeMode) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (welcomeCompleted) {
                true -> DivTrackNavigation(
                    appVersion = appVersion,
                    onInstallUpdate = onInstallUpdate,
                )
                false -> WelcomeScreen(
                    isSaving = isCompletingWelcome,
                    saveFailed = welcomeSaveFailed,
                    onContinue = viewModel::completeWelcome,
                )
                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DivTrackNavigation(appVersion: String, onInstallUpdate: (String) -> Unit) {
    // Back stack persistente: sobrevive a la rotación y a la muerte del proceso.
    val backStack = rememberNavBackStack(TopLevelDestination.start.key)
    val currentKey = backStack.lastOrNull() ?: TopLevelDestination.start.key

    // El lambda del instalador viaja envuelto en `rememberUpdatedState`: así la entrada de
    // Ajustes siempre llama al más reciente sin que su identidad cambie, que si no forzaría
    // a reconstruir el grafo de navegación en cada recomposición (ver el comentario de abajo).
    val currentInstallUpdate = rememberUpdatedState(onInstallUpdate)

    // El proveedor de entradas se construye UNA vez. Si se creara en cada recomposición,
    // `NavDisplay` recibiría un grafo distinto en cada pasada y volvería a resolver la
    // entrada activa: la pestaña se reconstruiría (y perdería el scroll) cada vez que algo
    // de arriba cambiara. Es una de las causas clásicas de "la app va lenta".
    val entryProvider = remember(appVersion) {
        entryProvider<NavKey> {
            entry<DashboardKey> { DashboardRoute() }
            entry<CalculatorKey> { CalculatorRoute() }
            entry<SettingsKey> {
                SettingsRoute(
                    appVersion = appVersion,
                    onInstallUpdate = { fileName -> currentInstallUpdate.value(fileName) },
                )
            }
        }
    }

    Scaffold(
        // Cada pantalla trae su propio Scaffold con TopAppBar, que ya aplica el inset de la
        // barra de estado. Aquí se anula para no reservar ese espacio dos veces.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                TopLevelDestination.ordered().forEach { destination ->
                    NavigationBarItem(
                        selected = destination.key == currentKey,
                        onClick = { navigateToTopLevel(backStack, destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            entryDecorators = listOf(
                // Estado de scroll y de campos guardados por entrada...
                rememberSaveableStateHolderNavEntryDecorator(),
                // ...y un ViewModelStoreOwner propio por entrada: la pestaña que sale del back
                // stack se limpia (nada de vistas vivas de pantallas que ya no están). El panel
                // es la raíz de la pila y conserva su ViewModel mientras la app viva, así que él
                // mismo decide cuándo refrescar al volver a la pantalla (ver ADR 29).
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
        )
    }
}
