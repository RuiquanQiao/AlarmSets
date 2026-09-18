package io.github.ruiquanqiao.alarmsets.core.alarm

import io.github.ruiquanqiao.alarmsets.core.domain.NextTriggerResolver
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.WeekDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns an alarm's recurrence rule into the next absolute instant it fires.
 *
 * The weekday arithmetic itself lives in the pure model
 * ([io.github.ruiquanqiao.alarmsets.core.model.WeekDays.daysUntilNext]) and is
 * unit tested there without a clock. This class only supplies the calendar
 * context: today's weekday, and how a wall-clock time maps onto an instant in
 * the current time zone.
 */
class AndroidNextTriggerResolver(
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : NextTriggerResolver {

    override fun nextTrigger(alarm: Alarm, afterEpochMillis: Long): Long? {
        val zone = zoneProvider()
        val now = Instant.ofEpochMilli(afterEpochMillis).atZone(zone)
        val today = now.toLocalDate()

        val timeToday = today.atTime(alarm.time.hour, alarm.time.minute)
        // "Already passed" includes exactly now: an alarm must not fire twice
        // for the same instant when the schedule is re-synced.
        val passedToday = !timeToday.isAfter(now.toLocalDateTime())

        val date: LocalDate = if (alarm.days.isEmpty) {
            if (passedToday) today.plusDays(1) else today
        } else {
            val offset = alarm.days.daysUntilNext(
                from = WeekDay.ofIso(now.dayOfWeek.value),
                timeAlreadyPassedToday = passedToday,
            ) ?: return null
            today.plusDays(offset.toLong())
        }

        // On the spring-forward transition the chosen wall-clock time may not
        // exist. ZonedDateTime resolves that by moving forward past the gap,
        // which is the behaviour a user expects: the alarm still goes off, once.
        return date.atTime(alarm.time.hour, alarm.time.minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}
