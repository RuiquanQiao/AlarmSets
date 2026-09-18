package io.github.ruiquanqiao.alarmsets.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import io.github.ruiquanqiao.alarmsets.core.model.SnoozeConfig
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDay
import io.github.ruiquanqiao.alarmsets.ui.initial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(
    alarm: Alarm,
    ringtoneTitle: String,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Long) -> Unit,
    onPickRingtone: (Alarm) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(alarm.id, alarm.time) { mutableStateOf(alarm) }
    var keyboardEntry by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val timeState = rememberTimePickerState(
        initialHour = draft.time.hour,
        initialMinute = draft.time.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = androidx.compose.material3.ModalBottomSheetProperties(
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Dial or keyboard. Entering a whole timetable means setting a dozen
            // alarms in a row, and dragging a dial that many times is miserable,
            // so typing is one tap away and the choice sticks for the session.
            if (keyboardEntry) {
                TimeInput(
                    state = timeState,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                TimePicker(
                    state = timeState,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            TextButton(
                onClick = { keyboardEntry = !keyboardEntry },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(
                    if (keyboardEntry) Icons.Default.Schedule else Icons.Default.Keyboard,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (keyboardEntry) R.string.time_use_dial else R.string.time_use_keyboard,
                    ),
                )
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                label = { Text(stringResource(R.string.alarm_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(
                    stringResource(R.string.repeat),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekDay.entries.forEach { day ->
                        FilterChip(
                            selected = day in draft.days,
                            onClick = { draft = draft.copy(days = draft.days.toggle(day)) },
                            label = {
                                Text(
                                    day.initial(),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.size(width = 44.dp, height = 40.dp),
                        )
                    }
                }
            }

            SettingRow(
                title = stringResource(R.string.ringtone),
                value = ringtoneTitle,
                onClick = {
                    onPickRingtone(
                        draft.copy(
                            time = TimeOfDay.of(timeState.hour, timeState.minute),
                        ),
                    )
                },
            )

            ToggleRow(
                title = stringResource(R.string.vibrate),
                checked = draft.vibrate,
                onCheckedChange = { draft = draft.copy(vibrate = it) },
            )

            Column {
                Text(
                    "${stringResource(R.string.volume)} · ${draft.volumePercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = draft.volumePercent.toFloat(),
                    onValueChange = { draft = draft.copy(volumePercent = it.toInt()) },
                    valueRange = 0f..100f,
                )
            }

            ToggleRow(
                title = stringResource(R.string.snooze),
                subtitle = if (draft.snooze.enabled) {
                    stringResource(R.string.snooze_minutes, draft.snooze.minutes)
                } else {
                    null
                },
                checked = draft.snooze.enabled,
                onCheckedChange = {
                    draft = draft.copy(snooze = draft.snooze.copy(enabled = it))
                },
            )

            if (draft.snooze.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 9, 10, 15, 20).forEach { minutes ->
                        FilterChip(
                            selected = draft.snooze.minutes == minutes,
                            onClick = {
                                draft = draft.copy(
                                    snooze = draft.snooze.copy(minutes = minutes),
                                )
                            },
                            label = { Text("${minutes}m") },
                        )
                    }
                }
            }

            Column {
                Text(
                    stringResource(R.string.auto_silence),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 20, 30).forEach { minutes ->
                        FilterChip(
                            selected = draft.autoSilenceMinutes == minutes,
                            onClick = { draft = draft.copy(autoSilenceMinutes = minutes) },
                            label = {
                                Text(
                                    if (minutes == 0) {
                                        stringResource(R.string.auto_silence_never)
                                    } else {
                                        "${minutes}m"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (alarm.id != Alarm.NO_ID) {
                    TextButton(onClick = { onDelete(alarm.id) }) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = {
                        onSave(
                            draft.copy(time = TimeOfDay.of(timeState.hour, timeState.minute)),
                        )
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
