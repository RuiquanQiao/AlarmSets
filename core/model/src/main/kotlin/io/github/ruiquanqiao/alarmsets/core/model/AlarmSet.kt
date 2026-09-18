package io.github.ruiquanqiao.alarmsets.core.model

import kotlinx.serialization.Serializable

/**
 * Accent colour for a set. Stored as a semantic name, not an ARGB value, so the
 * palette can follow the system theme (including Material You dynamic colour)
 * instead of being frozen at creation time.
 */
@Serializable
enum class SetAccent {
    BLUE,
    GREEN,
    AMBER,
    ROSE,
    VIOLET,
    TEAL,
    ;

    companion object {
        fun forIndex(index: Int): SetAccent = entries[index.mod(entries.size)]
    }
}

/**
 * A named schedule: a whole day's worth of alarms that are managed together.
 *
 * ## The one rule that matters
 *
 * A set's [enabled] switch is a *mask*, not a bulk edit. An alarm rings when
 * `set.enabled && alarm.enabled`. Turning the set off and on again therefore
 * restores every alarm's individual state exactly as the user left it, and
 * never destroys it.
 *
 * Every competing app that got this wrong forces the user to remember which
 * alarms inside a group were individually off. Do not "simplify" this into a
 * cascade that rewrites the children.
 */
@Serializable
data class AlarmSet(
    val id: Long = NO_ID,
    val name: String,
    val enabled: Boolean = true,
    val accent: SetAccent = SetAccent.BLUE,
    val alarms: List<Alarm> = emptyList(),
    val sortIndex: Int = 0,
    /** Free-text note, e.g. where a shared template came from. */
    val note: String = "",
) {
    /** Alarms that will actually ring, honouring both switches. */
    val activeAlarms: List<Alarm>
        get() = if (enabled) alarms.filter { it.enabled } else emptyList()

    /** Alarms the user has switched on, regardless of the set's master switch. */
    val individuallyEnabledAlarms: List<Alarm>
        get() = alarms.filter { it.enabled }

    val earliest: TimeOfDay? get() = alarms.minOfOrNull { it.time }

    val latest: TimeOfDay? get() = alarms.maxOfOrNull { it.time }

    /** The union of every repeat day used inside the set. */
    val coveredDays: WeekDays
        get() = WeekDays(alarms.fold(0) { acc, alarm -> acc or alarm.days.bits })

    fun sortedByTime(): List<Alarm> = alarms.sortedBy { it.time.minutesOfDay }

    /**
     * Moves the whole schedule by [minutes] - "everything 15 minutes later".
     *
     * Refuses to wrap any alarm across midnight, because silently moving an
     * 07:00 alarm to 23:45 the previous evening is never what was meant.
     * Returns null so the caller can tell the user why instead of guessing.
     */
    fun shiftedBy(minutes: Int): AlarmSet? {
        if (alarms.any { it.time.wrapsAcrossMidnight(minutes) }) return null
        return copy(alarms = alarms.map { it.shiftedBy(minutes) })
    }

    companion object {
        const val NO_ID = 0L
    }
}
