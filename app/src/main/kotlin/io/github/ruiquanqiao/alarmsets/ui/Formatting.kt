package io.github.ruiquanqiao.alarmsets.ui

import android.content.Context
import android.text.format.DateFormat
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDays
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Formats a [TimeOfDay] honouring the device's 12/24-hour setting. */
fun TimeOfDay.formatted(context: Context): String =
    if (DateFormat.is24HourFormat(context)) {
        toString()
    } else {
        val suffix = if (hour < 12) "AM" else "PM"
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "$display:${minute.toString().padStart(2, '0')} $suffix"
    }

/** Short, human weekday summary: "Weekdays", "Mon Wed Fri", "Once". */
fun WeekDays.summary(): String = when {
    isEmpty -> "Once"
    this == WeekDays.EVERY_DAY -> "Every day"
    this == WeekDays.WEEKDAYS -> "Weekdays"
    this == WeekDays.WEEKEND -> "Weekends"
    else -> asList().joinToString(" ") { it.shortLabel() }
}

fun WeekDay.shortLabel(): String = when (this) {
    WeekDay.MONDAY -> "Mon"
    WeekDay.TUESDAY -> "Tue"
    WeekDay.WEDNESDAY -> "Wed"
    WeekDay.THURSDAY -> "Thu"
    WeekDay.FRIDAY -> "Fri"
    WeekDay.SATURDAY -> "Sat"
    WeekDay.SUNDAY -> "Sun"
}

fun WeekDay.initial(): String = shortLabel().first().toString()

/** "in 7 hours", "in 12 minutes", "tomorrow at 06:30". */
fun formatCountdown(triggerAtMillis: Long, nowMillis: Long, context: Context): String {
    val minutes = ChronoUnit.MINUTES.between(
        Instant.ofEpochMilli(nowMillis),
        Instant.ofEpochMilli(triggerAtMillis),
    )
    if (minutes < 1) return "in under a minute"
    if (minutes < 60) return "in $minutes min"

    val hours = minutes / 60
    if (hours < 24) {
        val remainder = minutes % 60
        return if (remainder == 0L) "in ${hours}h" else "in ${hours}h ${remainder}m"
    }

    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(triggerAtMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, target.toLocalDate())
    val time = TimeOfDay.of(target.hour, target.minute).formatted(context)

    return when (days) {
        1L -> "tomorrow at $time"
        else -> "${target.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} at $time"
    }
}
