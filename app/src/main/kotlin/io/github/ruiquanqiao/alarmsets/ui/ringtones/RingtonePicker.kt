package io.github.ruiquanqiao.alarmsets.ui.ringtones

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ruiquanqiao.alarmsets.AppContainer
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.designsystem.AlarmSetsShapeTokens
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneCategory
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneOption
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RingtonePickerState(
    val options: List<RingtoneOption> = emptyList(),
    val selected: RingtoneRef? = null,
    val importError: Boolean = false,
)

class RingtonePickerViewModel(
    private val container: AppContainer,
    private val alarmId: Long,
) : ViewModel() {

    private val internal = MutableStateFlow(RingtonePickerState())
    val state: StateFlow<RingtonePickerState> = internal.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val alarm = container.repository.getAlarm(alarmId)
            internal.value = internal.value.copy(
                options = container.ringtoneCatalog.options(),
                selected = alarm?.ringtone,
            )
        }
    }

    fun select(ref: RingtoneRef) {
        viewModelScope.launch {
            internal.value = internal.value.copy(selected = ref)
            container.ringtonePreviewPlayer.play(ref, volumePercent = 70)
            container.repository.getAlarm(alarmId)?.let { alarm ->
                container.saveAlarm(alarm.copy(ringtone = ref))
            }
        }
    }

    fun import(uri: String, displayName: String) {
        viewModelScope.launch {
            container.ringtoneCatalog.importAudio(uri, displayName)
                .onSuccess { ref ->
                    internal.value = internal.value.copy(importError = false)
                    refresh()
                    select(ref)
                }
                .onFailure {
                    internal.value = internal.value.copy(importError = true)
                }
        }
    }

    fun stopPreview() = container.ringtonePreviewPlayer.stop()

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtonePickerScreen(
    state: RingtonePickerState,
    onSelect: (RingtoneRef) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ringtones_title)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Default.FileUpload, null) },
                text = { Text(stringResource(R.string.ringtone_import)) },
            )
        },
    ) { padding ->
        val grouped = state.options.groupBy { it.category }
        val order = listOf(
            RingtoneCategory.SCHOOL to R.string.ringtone_category_school,
            RingtoneCategory.GENERAL to R.string.ringtone_category_general,
            RingtoneCategory.IMPORTED to R.string.ringtone_category_imported,
            RingtoneCategory.SYSTEM to R.string.ringtone_category_system,
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.importError) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.ringtone_import_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            order.forEach { (category, titleRes) ->
                val entries = grouped[category].orEmpty()
                if (entries.isEmpty()) return@forEach

                item(key = "header-$category") {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(entries, key = { it.ref.toString() }) { option ->
                    RingtoneRow(
                        option = option,
                        selected = option.ref == state.selected,
                        onClick = { onSelect(option.ref) },
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.ringtone_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RingtoneRow(
    option: RingtoneOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = AlarmSetsShapeTokens.alarmRow,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.bodyLarge)
                if (option.description.isNotBlank()) {
                    Text(
                        option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Check, null)
            }
        }
    }
}
