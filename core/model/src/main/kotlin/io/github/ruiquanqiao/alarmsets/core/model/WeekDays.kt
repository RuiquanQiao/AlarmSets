package io.github.ruiquanqiao.alarmsets.core.model

import kotlinx.serialization.Serializable

/** Days of the week, ISO-8601 ordered: Monday is 1, Sunday is 7. */
enum class WeekDay(val isoIndex: Int) {
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6),
    SUNDAY(7);

    companion object {
        fun ofIso(index: Int): WeekDay = entries.first { it.isoIndex == index }
    }
}

/**
 * The set of weekdays an alarm repeats on, held as a 7-bit mask.
 *
 * An empty mask means "fire once, on the next occurrence of this time, then
 * disable itself" - the familiar one-shot alarm.
 */
@Serializable
@JvmInline
value class WeekDays(val bits: Int) {

    init {
        require(bits in 0..0b111_1111) { "week day mask out of range: $bits" }
    }

    val isEmpty: Boolean get() = bits == 0
    val isRepeating: Boolean get() = bits != 0

    operator fun contains(day: WeekDay): Boolean = bits and day.mask != 0

    fun with(day: WeekDay): WeekDays = WeekDays(bits or day.mask)

    fun without(day: WeekDay): WeekDays = WeekDays(bits and day.mask.inv() and ALL_BITS)

    fun toggle(day: WeekDay): WeekDays = if (day in this) without(day) else with(day)

    fun asList(): List<WeekDay> = WeekDay.entries.filter { it in this }

    /**
     * How many days from [from] until this alarm should next fire.
     *
     * [timeAlreadyPassedToday] tells the caller's "today" apart from a genuine
     * match: if today is selected but the alarm's time is behind us, the answer
     * is the next selected day, not 0.
     *
     * Returns 0..6 for a repeating alarm, or null when nothing is selected -
     * one-shot alarms are resolved by the caller instead.
     *
     * Kept here, free of any clock, so the tricky wrap-around cases are unit
     * testable without faking time.
     */
    fun daysUntilNext(from: WeekDay, timeAlreadyPassedToday: Boolean): Int? {
        if (isEmpty) return null
        val start = if (timeAlreadyPassedToday) 1 else 0
        for (offset in start..7) {
            val candidateIso = ((from.isoIndex - 1 + offset) % 7) + 1
            if (WeekDay.ofIso(candidateIso) in this) return offset
        }
        return null
    }

    override fun toString(): String =
        if (isEmpty) "once" else asList().joinToString(",") { it.name.take(3) }

    companion object {
        private const val ALL_BITS = 0b111_1111

        val NONE = WeekDays(0)
        val EVERY_DAY = WeekDays(ALL_BITS)
        val WEEKDAYS = WeekDays(
            WeekDay.MONDAY.mask or WeekDay.TUESDAY.mask or WeekDay.WEDNESDAY.mask or
                WeekDay.THURSDAY.mask or WeekDay.FRIDAY.mask,
        )
        val WEEKEND = WeekDays(WeekDay.SATURDAY.mask or WeekDay.SUNDAY.mask)

        fun of(vararg days: WeekDay): WeekDays =
            WeekDays(days.fold(0) { acc, day -> acc or day.mask })
    }
}

private val WeekDay.mask: Int get() = 1 shl (isoIndex - 1)
