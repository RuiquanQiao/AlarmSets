package io.github.ruiquanqiao.alarmsets.ui.sets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ruiquanqiao.alarmsets.AppContainer
import io.github.ruiquanqiao.alarmsets.core.domain.NextAlarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetsUiState(
    val sets: List<AlarmSet> = emptyList(),
    val nextAlarm: NextAlarm? = null,
    val exactAlarmsBlocked: Boolean = false,
    val alarmStreamMuted: Boolean = false,
    val loading: Boolean = true,
)

class SetsViewModel(private val container: AppContainer) : ViewModel() {

    private val warnings = MutableStateFlow(readWarnings())

    val uiState: StateFlow<SetsUiState> =
        combine(container.repository.observeSets(), warnings) { sets, warning ->
            SetsUiState(
                sets = sets,
                nextAlarm = container.resolveNextAlarm(sets),
                exactAlarmsBlocked = warning.exactBlocked,
                alarmStreamMuted = warning.streamMuted,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetsUiState(),
        )

    private val expanded = MutableStateFlow<Set<Long>>(emptySet())
    val expandedSetIds: StateFlow<Set<Long>> = expanded.asStateFlow()

    fun toggleExpanded(setId: Long) {
        expanded.value = if (setId in expanded.value) {
            expanded.value - setId
        } else {
            expanded.value + setId
        }
    }

    fun setEnabled(setId: Long, enabled: Boolean) {
        viewModelScope.launch { container.toggleAlarmSet(setId, enabled) }
    }

    fun alarmEnabled(alarmId: Long, enabled: Boolean) {
        viewModelScope.launch { container.toggleAlarm(alarmId, enabled) }
    }

    fun refreshWarnings() {
        warnings.value = readWarnings()
    }

    private fun readWarnings() = Warnings(
        exactBlocked = !container.scheduler.canScheduleExactAlarms(),
        streamMuted = container.tonePlayer.isAlarmStreamMuted(),
    )

    private data class Warnings(val exactBlocked: Boolean, val streamMuted: Boolean)
}
