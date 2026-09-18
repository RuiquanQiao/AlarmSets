package io.github.ruiquanqiao.alarmsets.ui.sets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.designsystem.AlarmSetsShapeTokens
import io.github.ruiquanqiao.alarmsets.core.designsystem.accentColorsFor
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.ui.formatCountdown
import io.github.ruiquanqiao.alarmsets.ui.formatted
import io.github.ruiquanqiao.alarmsets.ui.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetsScreen(
    state: SetsUiState,
    expandedSetIds: Set<Long>,
    onToggleExpanded: (Long) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    onAlarmEnabled: (Long, Boolean) -> Unit,
    onOpenSet: (Long) -> Unit,
    onCreateSet: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.sets_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateSet,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_set)) },
            )
        },
    ) { padding ->
        if (!state.loading && state.sets.isEmpty()) {
            EmptyState(onCreateSet, Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NextAlarmBanner(state, context)
            }

            if (state.exactAlarmsBlocked) {
                item {
                    WarningRow(
                        title = stringResource(R.string.settings_exact_alarms_blocked),
                        body = stringResource(R.string.settings_exact_alarms_blocked_desc),
                        onClick = onOpenSettings,
                    )
                }
            }
            if (state.alarmStreamMuted) {
                item {
                    WarningRow(
                        title = stringResource(R.string.settings_alarm_volume_muted),
                        body = stringResource(R.string.settings_alarm_volume_muted_desc),
                        onClick = onOpenSettings,
                        muted = true,
                    )
                }
            }

            items(state.sets, key = { it.id }) { set ->
                SetCard(
                    set = set,
                    expanded = set.id in expandedSetIds,
                    onToggleExpanded = { onToggleExpanded(set.id) },
                    onSetEnabled = { onSetEnabled(set.id, it) },
                    onAlarmEnabled = onAlarmEnabled,
                    onOpen = { onOpenSet(set.id) },
                )
            }
        }
    }
}

@Composable
private fun NextAlarmBanner(state: SetsUiState, context: android.content.Context) {
    val next = state.nextAlarm
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        if (next == null) {
            Text(
                stringResource(R.string.no_upcoming_alarm),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                next.alarm.time.formatted(context),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(formatCountdown(next.triggerAtMillis, System.currentTimeMillis(), context))
                    append(" · ")
                    append(next.alarm.label.ifBlank { next.set.name })
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetCard(
    set: AlarmSet,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onAlarmEnabled: (Long, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val accent = accentColorsFor(set.accent.name)
    val context = LocalContext.current
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    ElevatedCard(
        shape = AlarmSetsShapeTokens.setCard,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (set.enabled) accent.container else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (set.enabled) accent.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (set.enabled) accent.marker else MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    set.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    setSummary(set, context),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            IconButton(onClick = onToggleExpanded) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
            Switch(checked = set.enabled, onCheckedChange = onSetEnabled)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                set.sortedByTime().forEach { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        setEnabled = set.enabled,
                        onEnabledChange = { onAlarmEnabled(alarm.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    setEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = AlarmSetsShapeTokens.alarmRow,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    alarm.time.formatted(context),
                    style = MaterialTheme.typography.titleMedium,
                    // The set's master switch masks its alarms without
                    // changing them, so a masked alarm is dimmed, not off.
                    color = if (setEnabled && alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val detail = listOfNotNull(
                    alarm.label.ifBlank { null },
                    alarm.days.summary(),
                ).joinToString(" · ")
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun WarningRow(
    title: String,
    body: String,
    onClick: () -> Unit,
    muted: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.Default.Warning, null)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateSet: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                stringResource(R.string.sets_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sets_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            ExtendedFloatingActionButton(
                onClick = onCreateSet,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.sets_empty_action)) },
            )
        }
    }
}

private fun setSummary(set: AlarmSet, context: android.content.Context): String {
    if (set.alarms.isEmpty()) return "No alarms yet"
    val on = set.individuallyEnabledAlarms.size
    val range = listOfNotNull(set.earliest, set.latest)
    val span = if (range.size == 2 && range[0] != range[1]) {
        "${range[0].formatted(context)} – ${range[1].formatted(context)}"
    } else {
        range.firstOrNull()?.formatted(context).orEmpty()
    }
    return "$span · $on of ${set.alarms.size} on"
}
