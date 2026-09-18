package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneOption
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import kotlinx.coroutines.flow.Flow

/**
 * Every capability the app needs from the outside world, declared here as an
 * interface so the core never names a platform type.
 *
 * Porting to another platform is the job of writing new implementations of the
 * interfaces in this file. If you find yourself wanting to add an Android (or
 * iOS, or desktop) type to a signature here, add a port instead.
 */

interface AlarmSetRepository {
    fun observeSets(): Flow<List<AlarmSet>>
    fun observeSet(setId: Long): Flow<AlarmSet?>

    suspend fun getSet(setId: Long): AlarmSet?
    suspend fun getAlarm(alarmId: Long): Alarm?

    suspend fun upsertSet(set: AlarmSet): Long
    suspend fun deleteSet(setId: Long)
    suspend fun setSetEnabled(setId: Long, enabled: Boolean)
    suspend fun reorderSets(orderedIds: List<Long>)

    suspend fun upsertAlarm(alarm: Alarm): Long
    suspend fun deleteAlarm(alarmId: Long)
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean)
    suspend fun replaceAlarms(setId: Long, alarms: List<Alarm>)
}

/** Reads the clock. Injected so time-dependent logic stays testable. */
fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

/**
 * Turns an alarm's recurrence rule into the next absolute instant it fires.
 *
 * Calendar arithmetic (time zones, DST, "what weekday is it") is genuinely
 * platform work, so it lives behind this port rather than being reimplemented
 * in the core.
 */
fun interface NextTriggerResolver {
    /** Epoch millis of the next firing strictly after [afterEpochMillis], or null. */
    fun nextTrigger(alarm: Alarm, afterEpochMillis: Long): Long?
}

/** Owns the platform's alarm registrations. */
interface AlarmScheduler {
    /**
     * Makes the platform's pending alarms match [sets] exactly. Implementations
     * must be idempotent: calling this twice with the same input changes
     * nothing the second time.
     */
    suspend fun sync(sets: List<AlarmSet>)

    suspend fun cancelAll()

    suspend fun scheduleSnooze(alarmId: Long, triggerAtMillis: Long)

    suspend fun cancelSnooze(alarmId: Long)

    /**
     * Android 12+ can withhold exact alarm permission, and an alarm app that
     * cannot be exact is not worth much. The UI surfaces this.
     */
    fun canScheduleExactAlarms(): Boolean
}

/** Supplies the ringtones the picker shows and handles user imports. */
interface RingtoneCatalog {
    suspend fun options(): List<RingtoneOption>

    suspend fun resolve(ref: RingtoneRef): RingtoneOption?

    /**
     * Copies audio at [sourceUri] into app-owned storage and returns a stable
     * reference to it. There is no duration limit, by design.
     */
    suspend fun importAudio(sourceUri: String, displayName: String): Result<RingtoneRef.Imported>

    suspend fun deleteImported(ref: RingtoneRef.Imported)
}

/** Previews a ringtone while the user is choosing one. */
interface RingtonePreviewPlayer {
    suspend fun play(ref: RingtoneRef, volumePercent: Int)
    fun stop()
}

/** Checks for, downloads and hands off a newer build of the app. */
interface UpdateChecker {
    suspend fun check(): Result<UpdateStatus>
}

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(
        val versionName: String,
        val versionCode: Long,
        val releaseNotes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val publishedAt: String,
    ) : UpdateStatus
}
