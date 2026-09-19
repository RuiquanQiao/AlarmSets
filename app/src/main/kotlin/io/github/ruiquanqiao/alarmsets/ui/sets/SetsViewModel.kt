package io.github.ruiquanqiao.alarmsets.ui.sets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ruiquanqiao.alarmsets.AppContainer
import io.github.ruiquanqiao.alarmsets.core.domain.NextAlarm
import io.github.ruiquanqiao.alarmsets.core.domain.TemplateError
import io.github.ruiquanqiao.alarmsets.core.domain.TemplateException
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetsUiState(
    val sets: List<AlarmSet> = emptyList(),
    val nextAlarm: NextAlarm? = null,
    val exactAlarmsBlocked: Boolean = false,
    val alarmStreamMuted: Boolean = false,
    val loading: Boolean = true,
)

sealed interface SetsEvent {
    /** A template became a set; [setId] is where to send the user next. */
    data class Imported(val setId: Long, val name: String, val alarmCount: Int) : SetsEvent
    data class ImportFailed(val reason: String) : SetsEvent
}

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

    private val events = Channel<SetsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val expanded = MutableStateFlow<Set<Long>>(emptySet())
    val expandedSetIds: StateFlow<Set<Long>> = expanded.asStateFlow()

    /**
     * Turns exported template JSON back into a set.
     *
     * Building a timetable by hand means a couple of dozen near-identical
     * alarms, so a schedule someone already wrote - their own export, or one
     * shared with them - should come in as a file rather than be retyped. The
     * set arrives switched off, which [io.github.ruiquanqiao.alarmsets.core
     * .domain.TemplateCodec] guarantees, so importing can never start ringing
     * on someone's phone before they have looked at it.
     */
    fun importTemplate(json: String) {
        viewModelScope.launch {
            val result = container.templateCodec.parse(json)
                .mapCatching { container.templateCodec.toAlarmSet(it).getOrThrow() }

            result.fold(
                onSuccess = { set ->
                    val id = container.saveAlarmSet(set)
                    events.send(SetsEvent.Imported(id, set.name, set.alarms.size))
                },
                onFailure = { events.send(SetsEvent.ImportFailed(describe(it))) },
            )
        }
    }

    private fun describe(error: Throwable): String = when (
        val reason = (error as? TemplateException)?.error
    ) {
        TemplateError.NotATemplate, null -> "That file is not an AlarmSets template"
        is TemplateError.UnsupportedVersion ->
            "That template needs a newer version of AlarmSets"
        is TemplateError.Malformed -> "Could not read that template: ${reason.reason}"
    }

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
