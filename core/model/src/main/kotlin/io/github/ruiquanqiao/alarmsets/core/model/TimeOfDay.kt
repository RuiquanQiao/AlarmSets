package io.github.ruiquanqiao.alarmsets.core.model

import kotlinx.serialization.Serializable

/**
 * A wall-clock time with no date attached, stored as minutes since midnight.
 *
 * Hand-rolled rather than `java.time.LocalTime` so this module stays free of
 * any JVM-only API and can move to Kotlin Multiplatform untouched.
 */
@Serializable
@JvmInline
value class TimeOfDay private constructor(val minutesOfDay: Int) : Comparable<TimeOfDay> {

    val hour: Int get() = minutesOfDay / MINUTES_PER_HOUR
    val minute: Int get() = minutesOfDay % MINUTES_PER_HOUR

    /** Shifts by [delta] minutes, wrapping around midnight. */
    fun shiftedBy(delta: Int): TimeOfDay {
        val wrapped = ((minutesOfDay + delta) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return TimeOfDay(wrapped)
    }

    /**
     * Whether shifting by [delta] would cross midnight. Callers that must not
     * silently move an alarm to the previous or next day check this first.
     */
    fun wrapsAcrossMidnight(delta: Int): Boolean {
        val raw = minutesOfDay + delta
        return raw < 0 || raw >= MINUTES_PER_DAY
    }

    override fun compareTo(other: TimeOfDay): Int = minutesOfDay.compareTo(other.minutesOfDay)

    override fun toString(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        val MIDNIGHT = TimeOfDay(0)

        fun of(hour: Int, minute: Int): TimeOfDay {
            require(hour in 0..23) { "hour out of range: $hour" }
            require(minute in 0..59) { "minute out of range: $minute" }
            return TimeOfDay(hour * MINUTES_PER_HOUR + minute)
        }

        fun ofMinutesOfDay(minutes: Int): TimeOfDay {
            require(minutes in 0 until MINUTES_PER_DAY) { "minutes out of range: $minutes" }
            return TimeOfDay(minutes)
        }

        /** Parses `HH:mm`. Returns null rather than throwing on malformed input. */
        fun parseOrNull(text: String): TimeOfDay? {
            val parts = text.split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return of(h, m)
        }
    }
}
