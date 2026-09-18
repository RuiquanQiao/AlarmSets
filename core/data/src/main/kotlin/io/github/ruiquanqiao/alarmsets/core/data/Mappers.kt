package io.github.ruiquanqiao.alarmsets.core.data

import io.github.ruiquanqiao.alarmsets.core.data.db.AlarmEntity
import io.github.ruiquanqiao.alarmsets.core.data.db.AlarmSetEntity
import io.github.ruiquanqiao.alarmsets.core.data.db.AlarmSetWithAlarms
import io.github.ruiquanqiao.alarmsets.core.data.db.SnoozeEmbedded
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import io.github.ruiquanqiao.alarmsets.core.model.SetAccent
import io.github.ruiquanqiao.alarmsets.core.model.SnoozeConfig
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDays
import kotlinx.serialization.json.Json

/**
 * Translation between the storage schema and the domain model.
 *
 * Kept as free functions in the data module so the domain types stay unaware
 * that a database exists.
 */

private val json = Json { ignoreUnknownKeys = true }

internal fun RingtoneRef.encode(): String = json.encodeToString(RingtoneRef.serializer(), this)

internal fun decodeRingtone(raw: String): RingtoneRef =
    runCatching { json.decodeFromString(RingtoneRef.serializer(), raw) }
        // A tone that cannot be decoded must never stop an alarm from ringing.
        .getOrElse { RingtoneRef.Bundled(Alarm.DEFAULT_RINGTONE_KEY) }

internal fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    setId = setId,
    label = label,
    time = TimeOfDay.ofMinutesOfDay(minutesOfDay.coerceIn(0, TimeOfDay.MINUTES_PER_DAY - 1)),
    enabled = enabled,
    days = WeekDays(dayMask and 0b111_1111),
    ringtone = decodeRingtone(ringtoneJson),
    vibrate = vibrate,
    volumePercent = volumePercent.coerceIn(0, 100),
    autoSilenceMinutes = autoSilenceMinutes.coerceAtLeast(0),
    snooze = SnoozeConfig(
        enabled = snooze.enabled,
        minutes = snooze.minutes.coerceIn(1, 60),
        maxRepeats = snooze.maxRepeats.coerceAtLeast(0),
    ),
    sortIndex = sortIndex,
)

internal fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    setId = setId,
    label = label,
    minutesOfDay = time.minutesOfDay,
    enabled = enabled,
    dayMask = days.bits,
    ringtoneJson = ringtone.encode(),
    vibrate = vibrate,
    volumePercent = volumePercent,
    autoSilenceMinutes = autoSilenceMinutes,
    snooze = SnoozeEmbedded(
        enabled = snooze.enabled,
        minutes = snooze.minutes,
        maxRepeats = snooze.maxRepeats,
    ),
    sortIndex = sortIndex,
)

internal fun AlarmSetWithAlarms.toDomain(): AlarmSet = AlarmSet(
    id = set.id,
    name = set.name,
    enabled = set.enabled,
    accent = runCatching { SetAccent.valueOf(set.accent) }.getOrDefault(SetAccent.BLUE),
    note = set.note,
    sortIndex = set.sortIndex,
    alarms = alarms
        .sortedWith(compareBy({ it.minutesOfDay }, { it.sortIndex }, { it.id }))
        .map { it.toDomain() },
)

internal fun AlarmSet.toEntity(): AlarmSetEntity = AlarmSetEntity(
    id = id,
    name = name,
    enabled = enabled,
    accent = accent.name,
    note = note,
    sortIndex = sortIndex,
)
