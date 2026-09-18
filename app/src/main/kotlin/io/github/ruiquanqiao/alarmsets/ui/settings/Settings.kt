package io.github.ruiquanqiao.alarmsets.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ruiquanqiao.alarmsets.AppContainer
import io.github.ruiquanqiao.alarmsets.BuildConfig
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.domain.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val exactAlarmsBlocked: Boolean = false,
    val alarmStreamMuted: Boolean = false,
    val checkingUpdate: Boolean = false,
    val updateStatus: UpdateStatus? = null,
    val updateError: Boolean = false,
    val downloadProgress: Int? = null,
    val needsInstallPermission: Boolean = false,
    val installIntent: Intent? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val internal = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = internal.asStateFlow()

    fun refresh() {
        internal.value = internal.value.copy(
            exactAlarmsBlocked = !container.scheduler.canScheduleExactAlarms(),
            alarmStreamMuted = container.tonePlayer.isAlarmStreamMuted(),
        )
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            internal.value = internal.value.copy(checkingUpdate = true, updateError = false)
            container.updateChecker.check()
                .onSuccess {
                    internal.value = internal.value.copy(checkingUpdate = false, updateStatus = it)
                }
                .onFailure {
                    internal.value =
                        internal.value.copy(checkingUpdate = false, updateError = true)
                }
        }
    }

    fun download(update: UpdateStatus.Available) {
        if (!container.apkDownloader.canRequestInstall()) {
            internal.value = internal.value.copy(needsInstallPermission = true)
            return
        }
        viewModelScope.launch {
            internal.value = internal.value.copy(downloadProgress = 0)
            container.apkDownloader
                .download(update) { read, total ->
                    val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                    internal.value = internal.value.copy(downloadProgress = percent)
                }
                .onSuccess { file ->
                    internal.value = internal.value.copy(
                        downloadProgress = null,
                        installIntent = container.apkDownloader.installIntent(file),
                    )
                }
                .onFailure {
                    internal.value =
                        internal.value.copy(downloadProgress = null, updateError = true)
                }
        }
    }

    fun consumeInstallIntent() {
        internal.value = internal.value.copy(installIntent = null)
    }

    fun unknownSourcesIntent(): Intent = container.apkDownloader.manageUnknownSourcesIntent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownload: (UpdateStatus.Available) -> Unit,
    onFixExactAlarms: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.exactAlarmsBlocked) {
                item {
                    ActionCard(
                        title = stringResource(R.string.settings_exact_alarms_blocked),
                        body = stringResource(R.string.settings_exact_alarms_blocked_desc),
                        error = true,
                        onClick = onFixExactAlarms,
                    )
                }
            }
            if (state.alarmStreamMuted) {
                item {
                    ActionCard(
                        title = stringResource(R.string.settings_alarm_volume_muted),
                        body = stringResource(R.string.settings_alarm_volume_muted_desc),
                        error = true,
                        onClick = {},
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        when {
                            state.checkingUpdate -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.width(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.settings_check_updates))
                            }

                            state.downloadProgress != null -> Text(
                                stringResource(
                                    R.string.settings_update_downloading,
                                    state.downloadProgress,
                                ),
                            )

                            state.updateError -> Text(
                                stringResource(R.string.settings_update_failed),
                                color = MaterialTheme.colorScheme.error,
                            )

                            state.updateStatus is UpdateStatus.Available -> {
                                val update = state.updateStatus
                                Text(
                                    stringResource(
                                        R.string.settings_update_available,
                                        update.versionName,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (update.releaseNotes.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        update.releaseNotes.lineSequence().take(6).joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onDownload(update) }) {
                                    Text(stringResource(R.string.settings_update_download))
                                }
                            }

                            state.updateStatus is UpdateStatus.UpToDate -> Text(
                                stringResource(R.string.settings_up_to_date),
                            )

                            else -> TextButton(onClick = onCheckUpdates) {
                                Text(stringResource(R.string.settings_check_updates))
                            }
                        }

                        if (state.needsInstallPermission) {
                            TextButton(onClick = onAllowInstalls) {
                                Text(stringResource(R.string.settings_update_allow_install))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    error: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (error) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Opens the system page where the user can grant exact alarm scheduling. */
fun exactAlarmSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
    }
