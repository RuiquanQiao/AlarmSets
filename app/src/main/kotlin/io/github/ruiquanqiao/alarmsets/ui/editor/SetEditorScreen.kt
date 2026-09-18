package io.github.ruiquanqiao.alarmsets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.designsystem.AlarmSetsShapeTokens
import io.github.ruiquanqiao.alarmsets.core.designsystem.accentColorsFor
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.SetAccent
import io.github.ruiquanqiao.alarmsets.ui.formatted
import io.github.ruiquanqiao.alarmsets.ui.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditorScreen(
    set: AlarmSet?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onAccent: (SetAccent) -> Unit,
    onShift: (Int) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onAlarmEnabled: (Long, Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(set?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate_set)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { menuOpen = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_set)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_set)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAlarm,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_alarm)) },
            )
        },
    ) { padding ->
        if (set == null) {
            Box(Modifier.fillMaxSize()) {}
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NameField(set.name, onRename) }
            item { AccentPicker(set.accent, onAccent) }
            item { ShiftControls(onShift) }

            item {
                Text(
                    "Timeline",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }

            items(set.sortedByTime(), key = { it.id }) { alarm ->
                TimelineRow(
                    alarm = alarm,
                    accentName = set.accent.name,
                    onClick = { onEditAlarm(alarm) },
                    onEnabledChange = { onAlarmEnabled(alarm.id, it) },
                )
            }

            if (set.alarms.isEmpty()) {
                item {
                    Text(
                        "No alarms in this set yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_set)) },
            text = { Text("Delete \"${set?.name}\" and its ${set?.alarms?.size ?: 0} alarms?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun NameField(name: String, onRename: (String) -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (it.isNotBlank()) onRename(it)
        },
        label = { Text(stringResource(R.string.set_name)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccentPicker(selected: SetAccent, onAccent: (SetAccent) -> Unit) {
    Column {
        Text(
            stringResource(R.string.accent_colour),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetAccent.entries.forEach { accent ->
                val colors = accentColorsFor(accent.name)
                Box(
                    Modifier
                        .size(if (accent == selected) 40.dp else 32.dp)
                        .clip(CircleShape)
                        .background(colors.marker)
                        .clickable { onAccent(accent) },
                )
            }
        }
    }
}

@Composable
private fun ShiftControls(onShift: (Int) -> Unit) {
    Column {
        Text(
            stringResource(R.string.shift_whole_set),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-30, -15, -5, 5, 15, 30).forEach { minutes ->
                AssistChip(
                    onClick = { onShift(minutes) },
                    label = { Text(if (minutes > 0) "+$minutes" else "$minutes") },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    alarm: Alarm,
    accentName: String,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val accent = accentColorsFor(accentName)

    Surface(
        shape = AlarmSetsShapeTokens.alarmRow,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // The timeline spine.
            Box(
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (alarm.enabled) accent.marker else MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    alarm.time.formatted(context),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(alarm.label.ifBlank { null }, alarm.days.summary())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onEnabledChange)
        }
    }
}
