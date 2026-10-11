package com.liebeblack.divtrack.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.ProviderStatus
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.CollectEffects
import com.liebeblack.divtrack.presentation.common.TopSnackbarHost
import com.liebeblack.divtrack.presentation.common.asString
import com.liebeblack.divtrack.presentation.common.labelRes
import com.liebeblack.divtrack.presentation.common.openUrl
import com.liebeblack.divtrack.presentation.components.SegmentedSelector
import com.liebeblack.divtrack.presentation.components.SettingsSwitchRow
import com.liebeblack.divtrack.presentation.theme.Spacing
import java.time.Instant

@Composable
fun SettingsRoute(
    appVersion: String,
    onInstallUpdate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val onCheckProviders = remember(viewModel) { viewModel::onCheckProviders }
    val onCheckForUpdates = remember(viewModel) { viewModel::onCheckForUpdates }
    val onDownloadUpdate = remember(viewModel) { viewModel::onDownloadUpdate }
    val onInstallDownloaded = remember(viewModel) { viewModel::onInstallUpdate }
    val onOpenReleasePage = remember(viewModel) { viewModel::onOpenReleasePage }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage ->
                snackbarHostState.showSnackbar(message = effect.text.asString(context))

            // El instalador es lo único que no puede vivir en esta capa: necesita el
            // `FileProvider` del módulo `:app`, que es el dueño del manifiesto y de la
            // carpeta privada de descargas. Por eso el efecto sale hacia arriba.
            is SettingsEffect.LaunchInstaller -> onInstallUpdate(effect.fileName)

            is SettingsEffect.OpenReleasePage -> context.openUrl(effect.url)
        }
    }

    SettingsScreen(
        state = state,
        appVersion = appVersion,
        snackbarHostState = snackbarHostState,
        onIntent = onIntent,
        onCheckProviders = onCheckProviders,
        onCheckForUpdates = onCheckForUpdates,
        onDownloadUpdate = onDownloadUpdate,
        onInstallUpdate = onInstallDownloaded,
        onOpenReleasePage = onOpenReleasePage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String = "",
    onCheckProviders: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onOpenReleasePage: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(text = stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = padding.calculateTopPadding() + Spacing.sm,
                        bottom = padding.calculateBottomPadding() + Spacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SettingsCard(
                    title = stringResource(R.string.settings_theme),
                    helper = stringResource(R.string.settings_theme_helper),
                ) {
                    SegmentedSelector(
                        options = ThemeMode.entries,
                        selected = state.themeMode,
                        onSelect = { mode -> onIntent(SettingsIntent.SelectTheme(mode)) },
                        label = { mode -> stringResource(mode.labelRes()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsCard(
                    title = stringResource(R.string.settings_default_source),
                    helper = stringResource(R.string.settings_default_source_helper),
                ) {
                    SegmentedSelector(
                        options = RateSource.ordered(),
                        selected = state.defaultSource,
                        onSelect = { source -> onIntent(SettingsIntent.SelectDefaultSource(source)) },
                        label = { source -> stringResource(source.labelRes()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_parallel_rate),
                    helper = stringResource(R.string.settings_show_parallel_rate_helper),
                    checked = state.showParallelRate,
                    onCheckedChange = { enabled ->
                        onIntent(SettingsIntent.SetShowParallelRate(enabled))
                    },
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_igtf_default),
                    helper = stringResource(R.string.settings_igtf_default_helper),
                    checked = state.igtfEnabled,
                    onCheckedChange = { enabled -> onIntent(SettingsIntent.SetIgtfDefault(enabled)) },
                )

                SettingsCard(
                    title = stringResource(R.string.settings_provider),
                    helper = stringResource(R.string.settings_provider_helper),
                ) {
                    SegmentedSelector(
                        options = ProviderOptions,
                        selected = state.defaultProviderId ?: AUTOMATIC_PROVIDER_ID,
                        onSelect = { providerId ->
                            onIntent(
                                SettingsIntent.SelectProvider(
                                    providerId.takeUnless { it == AUTOMATIC_PROVIDER_ID },
                                ),
                            )
                        },
                        label = { providerId -> stringResource(providerLabelRes(providerId)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_sync),
                    helper = stringResource(R.string.settings_auto_sync_helper),
                    checked = state.autoSyncEnabled,
                    onCheckedChange = { enabled -> onIntent(SettingsIntent.SetAutoSync(enabled)) },
                )

                // La frecuencia y el ajuste de red solo se ofrecen si la sincronización está
                // encendida: un control activo que no hace nada es peor que no mostrarlo.
                if (state.autoSyncEnabled) {
                    SettingsCard(title = stringResource(R.string.settings_interval)) {
                        SegmentedSelector(
                            options = SyncIntervalOptions,
                            selected = state.syncIntervalMinutes,
                            onSelect = { minutes -> onIntent(SettingsIntent.SetSyncInterval(minutes)) },
                            label = { minutes ->
                                if (minutes % 60 == 0) {
                                    stringResource(R.string.settings_interval_hours, minutes / 60)
                                } else {
                                    stringResource(R.string.settings_interval_minutes, minutes)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_wifi_only),
                        helper = stringResource(R.string.settings_wifi_only_helper),
                        checked = state.syncOnWifiOnly,
                        onCheckedChange = { enabled -> onIntent(SettingsIntent.SetWifiOnly(enabled)) },
                    )
                }

                ProvidersStatusCard(
                    state = state,
                    onCheckProviders = onCheckProviders,
                )

                UpdatesCard(
                    state = state.update,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onOpenReleasePage = onOpenReleasePage,
                )

                AboutCard(appVersion = appVersion)
            }
        }
        TopSnackbarHost(
            hostState = snackbarHostState,
            topPadding = padding.calculateTopPadding(),
        )
    }
}

/**
 * Diagnóstico de fuentes: un botón que consulta las tres APIs de verdad y pinta el
 * resultado por proveedor. Se muestra qué rate trajo cada una, para que el usuario pueda
 * distinguir "está caído" de "respondió pero sin tasas útiles".
 */
@Composable
private fun ProvidersStatusCard(
    state: SettingsUiState,
    onCheckProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.settings_providers_status_title),
        helper = stringResource(R.string.settings_providers_status_helper),
        modifier = modifier,
    ) {
        Button(
            onClick = onCheckProviders,
            enabled = !state.isCheckingProviders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isCheckingProviders) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = stringResource(
                    if (state.isCheckingProviders) {
                        R.string.settings_providers_checking
                    } else {
                        R.string.settings_providers_check_now
                    },
                ),
            )
        }

        val diagnostics = state.providerDiagnostics
        if (diagnostics != null) {
            diagnostics.statuses.forEach { status ->
                ProviderStatusRow(status = status)
            }
        }
    }
}

@Composable
private fun ProviderStatusRow(status: ProviderStatus, modifier: Modifier = Modifier) {
    val dotColor = if (status.isOk) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val sourcesText = when {
        status.sources.isEmpty() -> stringResource(R.string.provider_sources_none)
        // Las etiquetas se resuelven con `map` (lambda inline, conserva el contexto
        // composable): `stringResource` dentro del lambda de `joinToString` no compila
        // porque su `transform` no es inline.
        else -> status.sources
            .map { sourceKey ->
                RateSource.fromKey(sourceKey)?.let { stringResource(it.labelRes()) } ?: sourceKey
            }
            .joinToString(separator = " · ")
    }
    // La edad del dato es parte del diagnóstico: "OK" con un dato de ayer es justamente
    // el caso del banco que dejó de publicar, y así se lee.
    val lastUpdatedText = status.lastUpdatedAtMillis?.let { millis ->
        runCatching { CurrencyFormatters.timestamp(Instant.ofEpochMilli(millis)) }.getOrNull()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = dotColor,
                ) {}
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = status.providerId,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sourcesText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (status.isOk) {
                Text(
                    text = lastUpdatedText
                        ?.let { fresh -> stringResource(R.string.provider_last_data, fresh) }
                        ?: stringResource(R.string.provider_no_timestamp),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Sección «Actualizaciones»: versión instalada, versión publicada, descarga en segundo plano
 * y el paso a la pantalla de instalación de Android.
 *
 * Aquí no se decide nada: el ViewModel entrega el estado ya interpretado (qué texto toca,
 * si hay algo que descargar, si el APK está listo) y esta función solo elige **qué** pinta.
 * La descarga la hace el gestor del sistema, así que la app puede cerrarse a mitad y al
 * volver el sondeo del ViewModel reengancha el progreso donde estaba.
 */
@Composable
private fun UpdatesCard(
    state: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.settings_update_title),
        helper = stringResource(R.string.settings_update_helper),
        modifier = modifier,
    ) {
        SectionBody(
            text = stringResource(
                R.string.settings_update_installed,
                state.installedVersion.ifBlank { NOT_AVAILABLE },
            ),
        )

        state.latestVersion?.let { latest ->
            SectionBody(text = stringResource(R.string.settings_update_latest, latest))
        }

        SectionBody(
            text = when {
                state.isVersionUnknown ->
                    stringResource(R.string.settings_update_unreadable_version)

                state.isUpdateAvailable -> stringResource(R.string.settings_update_available)

                state.latestVersion != null -> stringResource(R.string.settings_update_current)

                else -> stringResource(R.string.settings_update_unknown)
            },
        )

        state.checkedAtText?.let { checkedAt ->
            SectionBody(text = stringResource(R.string.settings_update_checked_at, checkedAt))
        }

        state.publishedAtText?.let { publishedAt ->
            SectionBody(text = stringResource(R.string.settings_update_published_at, publishedAt))
        }

        state.downloadSizeText?.let { size ->
            SectionBody(text = stringResource(R.string.settings_update_size, size))
        }

        DownloadStateBlock(state = state)

        Button(
            onClick = onCheckForUpdates,
            enabled = !state.isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = stringResource(
                    if (state.isChecking) {
                        R.string.settings_update_checking
                    } else {
                        R.string.settings_update_check_now
                    },
                ),
            )
        }

        // El botón de descarga desaparece mientras hay una descarga viva o el APK ya está
        // listo, y vuelve a aparecer si la descarga falló: un callejón sin salida no vale.
        val downloadIdle = state.downloadPhase == DownloadPhase.NONE ||
            state.downloadPhase == DownloadPhase.FAILED
        if (state.canDownload && downloadIdle && !state.canInstall) {
            Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.settings_update_download))
            }
        }

        if (state.canInstall) {
            Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.settings_update_install))
            }
        }

        if (state.releasePageUrl != null) {
            OutlinedButton(onClick = onOpenReleasePage, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.settings_update_view_release))
            }
        }
    }
}

/** Estado de la descarga: progreso mientras baja, aviso cuando termina o si falló. */
@Composable
private fun DownloadStateBlock(state: UpdateUiState, modifier: Modifier = Modifier) {
    when (state.downloadPhase) {
        DownloadPhase.PENDING,
        DownloadPhase.RUNNING,
        -> {
            val progress = state.downloadProgress
            SectionBody(
                text = if (progress == null) {
                    stringResource(R.string.settings_update_downloading)
                } else {
                    stringResource(
                        R.string.settings_update_downloading_percent,
                        (progress * PERCENT).toInt(),
                    )
                },
            )

            // Barra determinada cuando el gestor ya conoce el tamaño total, e indeterminada
            // cuando no: un 0 % cuando en realidad no se sabe es una cifra inventada.
            if (progress == null) {
                LinearProgressIndicator(modifier = modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = modifier.fillMaxWidth(),
                )
            }
        }

        DownloadPhase.READY ->
            SectionBody(text = stringResource(R.string.settings_update_ready))

        DownloadPhase.FAILED ->
            SectionBody(text = stringResource(R.string.settings_update_download_failed_state))

        DownloadPhase.NONE -> Unit
    }
}

/** Tarjeta de sección con título, explicación opcional y contenido. */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionTitle(text = title)
            if (helper != null) {
                SectionBody(text = helper)
            }
            content()
        }
    }
}

@Composable
private fun AboutCard(appVersion: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionTitle(text = stringResource(R.string.settings_about))

            Text(
                text = stringResource(
                    R.string.settings_version,
                    appVersion.ifBlank { NOT_AVAILABLE },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SectionTitle(text = stringResource(R.string.settings_providers))
            SectionBody(text = stringResource(R.string.settings_providers_value))

            SectionTitle(text = stringResource(R.string.settings_disclaimer_title))
            SectionBody(text = stringResource(R.string.settings_disclaimer_value))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SectionBody(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val NOT_AVAILABLE = "—"

/** La fracción de progreso (0..1) se pinta en tanto por ciento. */
private const val PERCENT = 100
