package io.github.ruiquanqiao.alarmsets.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ruiquanqiao.alarmsets.AppContainer
import io.github.ruiquanqiao.alarmsets.core.domain.ShiftResult
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.SetAccent
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
    data object Closed : EditorEvent
    data class Exported(val json: String, val setName: String) : EditorEvent
}

class SetEditorViewModel(
    private val container: AppContainer,
    private val setId: Long,
) : ViewModel() {

    val set: StateFlow<AlarmSet?> =
        container.repository.observeSet(setId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val editing = MutableStateFlow<Alarm?>(null)
    val editingAlarm: StateFlow<Alarm?> = editing.asStateFlow()

    fun rename(name: String) {
        val current = set.value ?: return
        viewModelScope.launch { container.saveAlarmSet(current.copy(name = name)) }
    }

    fun setAccent(accent: SetAccent) {
        val current = set.value ?: return
        viewModelScope.launch { container.saveAlarmSet(current.copy(accent = accent)) }
    }

    fun setNote(note: String) {
        val current = set.value ?: return
        viewModelScope.launch { container.saveAlarmSet(current.copy(note = note)) }
    }

    fun shift(minutes: Int) {
        viewModelScope.launch {
            when (val result = container.shiftAlarmSet(setId, minutes)) {
                is ShiftResult.Shifted -> events.send(
                    EditorEvent.Message(
                        "Moved every alarm ${if (minutes > 0) "+" else ""}$minutes min",
                    ),
                )

                ShiftResult.WouldCrossMidnight -> events.send(
                    EditorEvent.Message("Cannot shift: an alarm would cross midnight"),
                )

                ShiftResult.SetNotFound -> Unit
            }
        }
    }

    fun startNewAlarm() {
        val current = set.value ?: return
        val suggested = current.latest?.shiftedBy(45) ?: TimeOfDay.of(7, 0)
        editing.value = Alarm(setId = setId, time = suggested)
    }

    fun startEditing(alarm: Alarm) {
        editing.value = alarm
    }

    fun cancelEditing() {
        editing.value = null
    }

    fun saveEditing(alarm: Alarm) {
        viewModelScope.launch {
            container.saveAlarm(alarm.copy(setId = setId))
            editing.value = null
        }
    }

    fun deleteAlarm(alarmId: Long) {
        viewModelScope.launch {
            container.deleteAlarm(alarmId)
            editing.value = null
        }
    }

    fun setAlarmEnabled(alarmId: Long, enabled: Boolean) {
        viewModelScope.launch { container.toggleAlarm(alarmId, enabled) }
    }

    fun duplicate() {
        val current = set.value ?: return
        viewModelScope.launch {
            container.duplicateAlarmSet(setId, "${current.name} copy")
            events.send(EditorEvent.Message("Duplicated"))
        }
    }

    fun export() {
        val current = set.value ?: return
        viewModelScope.launch {
            events.send(EditorEvent.Exported(container.templateCodec.export(current), current.name))
        }
    }

    fun delete() {
        viewModelScope.launch {
            container.deleteAlarmSet(setId)
            events.send(EditorEvent.Closed)
        }
    }
}
