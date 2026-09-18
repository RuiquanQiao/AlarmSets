package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import kotlinx.coroutines.flow.first

/**
 * Rescheduling is required after *any* change that can alter which alarms
 * ring or when. Rather than making every call site remember, mutations funnel
 * through these use cases and each one re-syncs.
 */
class SyncSchedule(
    private val repository: AlarmSetRepository,
    private val scheduler: AlarmScheduler,
) {
    suspend operator fun invoke() {
        scheduler.sync(repository.observeSets().first())
    }
}

/**
 * Flips a set's master switch.
 *
 * This writes only the set row. The alarms inside keep their own enabled
 * flags, which is what lets the user turn a set back on and get exactly the
 * schedule they had. See the note on [AlarmSet].
 */
class ToggleAlarmSet(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(setId: Long, enabled: Boolean) {
        repository.setSetEnabled(setId, enabled)
        syncSchedule()
    }
}

/** Flips one alarm's own switch, independent of its set. */
class ToggleAlarm(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(alarmId: Long, enabled: Boolean) {
        repository.setAlarmEnabled(alarmId, enabled)
        syncSchedule()
    }
}

/** Result of asking to move a whole schedule earlier or later. */
sealed interface ShiftResult {
    data class Shifted(val set: AlarmSet) : ShiftResult
    data object WouldCrossMidnight : ShiftResult
    data object SetNotFound : ShiftResult
}

/**
 * Moves every alarm in a set by the same offset - "the whole day starts 15
 * minutes later". Refuses rather than silently wrapping an alarm onto the
 * adjacent day.
 */
class ShiftAlarmSet(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(setId: Long, minutes: Int): ShiftResult {
        val set = repository.getSet(setId) ?: return ShiftResult.SetNotFound
        val shifted = set.shiftedBy(minutes) ?: return ShiftResult.WouldCrossMidnight
        repository.replaceAlarms(setId, shifted.alarms)
        syncSchedule()
        return ShiftResult.Shifted(shifted)
    }
}

class SaveAlarm(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(alarm: Alarm): Long {
        val id = repository.upsertAlarm(alarm)
        syncSchedule()
        return id
    }
}

class DeleteAlarm(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(alarmId: Long) {
        repository.deleteAlarm(alarmId)
        syncSchedule()
    }
}

class SaveAlarmSet(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(set: AlarmSet): Long {
        val id = repository.upsertSet(set)
        syncSchedule()
        return id
    }
}

class DeleteAlarmSet(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(setId: Long) {
        repository.deleteSet(setId)
        syncSchedule()
    }
}

/** Duplicates a set, including its alarms, under a new name. */
class DuplicateAlarmSet(
    private val repository: AlarmSetRepository,
    private val syncSchedule: SyncSchedule,
) {
    suspend operator fun invoke(setId: Long, newName: String): Long? {
        val source = repository.getSet(setId) ?: return null
        val copy = source.copy(
            id = AlarmSet.NO_ID,
            name = newName,
            alarms = source.alarms.map { it.copy(id = Alarm.NO_ID, setId = AlarmSet.NO_ID) },
        )
        val id = repository.upsertSet(copy)
        syncSchedule()
        return id
    }
}
