package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import io.github.ruiquanqiao.alarmsets.core.model.SetAccent
import io.github.ruiquanqiao.alarmsets.core.model.SnoozeConfig
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDays
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The portable form of a set: a schedule anyone can export, send to someone
 * else and import.
 *
 * Deliberately free of database ids and of any reference to imported audio
 * files, which do not travel. An alarm whose tone was a user import falls back
 * to a bundled tone on the receiving device rather than importing broken.
 *
 * Bump [CURRENT_VERSION] on any breaking change and keep reading old versions.
 */
@Serializable
data class AlarmSetTemplate(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val name: String,
    val note: String = "",
    val accent: String = SetAccent.BLUE.name,
    val alarms: List<TemplateAlarm>,
) {
    companion object {
        const val FORMAT = "alarmsets.template"
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class TemplateAlarm(
    val label: String = "",
    /** `HH:mm`. */
    val time: String,
    val days: List<String> = emptyList(),
    val ringtoneKey: String? = null,
    val vibrate: Boolean = true,
    val volumePercent: Int = 80,
    val autoSilenceMinutes: Int = 10,
    val snoozeMinutes: Int = 9,
    val snoozeEnabled: Boolean = true,
)

sealed interface TemplateError {
    data object NotATemplate : TemplateError
    data class UnsupportedVersion(val found: Int) : TemplateError
    data class Malformed(val reason: String) : TemplateError
}

/** Converts between a stored set and its portable representation. */
class TemplateCodec(
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun export(set: AlarmSet): String {
        val template = AlarmSetTemplate(
            name = set.name,
            note = set.note,
            accent = set.accent.name,
            alarms = set.sortedByTime().map { alarm ->
                TemplateAlarm(
                    label = alarm.label,
                    time = alarm.time.toString(),
                    days = alarm.days.asList().map { it.name },
                    // Imported audio cannot travel with the template.
                    ringtoneKey = (alarm.ringtone as? RingtoneRef.Bundled)?.key,
                    vibrate = alarm.vibrate,
                    volumePercent = alarm.volumePercent,
                    autoSilenceMinutes = alarm.autoSilenceMinutes,
                    snoozeMinutes = alarm.snooze.minutes,
                    snoozeEnabled = alarm.snooze.enabled,
                )
            },
        )
        return json.encodeToString(AlarmSetTemplate.serializer(), template)
    }

    fun parse(text: String): Result<AlarmSetTemplate> = runCatching {
        json.decodeFromString(AlarmSetTemplate.serializer(), text)
    }.mapCatching { template ->
        if (template.format != AlarmSetTemplate.FORMAT) {
            throw TemplateException(TemplateError.NotATemplate)
        }
        if (template.version > AlarmSetTemplate.CURRENT_VERSION) {
            throw TemplateException(TemplateError.UnsupportedVersion(template.version))
        }
        template
    }

    fun toAlarmSet(template: AlarmSetTemplate): Result<AlarmSet> = runCatching {
        val accent = runCatching { SetAccent.valueOf(template.accent) }
            .getOrDefault(SetAccent.BLUE)

        val alarms = template.alarms.mapIndexed { index, item ->
            val time = TimeOfDay.parseOrNull(item.time)
                ?: throw TemplateException(TemplateError.Malformed("bad time: ${item.time}"))

            val days = item.days.fold(WeekDays.NONE) { acc, name ->
                val day = runCatching {
                    io.github.ruiquanqiao.alarmsets.core.model.WeekDay.valueOf(name)
                }.getOrElse { throw TemplateException(TemplateError.Malformed("bad day: $name")) }
                acc.with(day)
            }

            Alarm(
                setId = AlarmSet.NO_ID,
                label = item.label,
                time = time,
                days = days,
                ringtone = item.ringtoneKey
                    ?.let { RingtoneRef.Bundled(it) }
                    ?: RingtoneRef.Bundled(Alarm.DEFAULT_RINGTONE_KEY),
                vibrate = item.vibrate,
                volumePercent = item.volumePercent.coerceIn(0, 100),
                autoSilenceMinutes = item.autoSilenceMinutes.coerceAtLeast(0),
                snooze = SnoozeConfig(
                    enabled = item.snoozeEnabled,
                    minutes = item.snoozeMinutes.coerceIn(1, 60),
                ),
                sortIndex = index,
            )
        }

        AlarmSet(
            name = template.name,
            note = template.note,
            accent = accent,
            // An imported schedule arrives switched off so it cannot ring at
            // 06:00 tomorrow before the user has even looked at it.
            enabled = false,
            alarms = alarms,
        )
    }
}

class TemplateException(val error: TemplateError) : Exception(error.toString())
