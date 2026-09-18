package io.github.ruiquanqiao.alarmsets.core.model

import kotlinx.serialization.Serializable

/** How an alarm behaves after the user hits snooze. */
@Serializable
data class SnoozeConfig(
    val enabled: Boolean = true,
    val minutes: Int = 9,
    /** 0 means unlimited. */
    val maxRepeats: Int = 3,
) {
    init {
        require(minutes in 1..60) { "snooze minutes out of range: $minutes" }
        require(maxRepeats >= 0) { "maxRepeats must not be negative: $maxRepeats" }
    }

    /**
     * Whether the user may snooze once more, having already snoozed
     * [timesAlreadySnoozed] times.
     *
     * Pure and testable on purpose: the count has to be carried across process
     * boundaries by the scheduler, and when that plumbing broke once, the limit
     * silently stopped applying and snoozing became unlimited. The rule lives
     * here so a test can hold it.
     */
    fun allowsAnother(timesAlreadySnoozed: Int): Boolean =
        enabled && (maxRepeats == UNLIMITED || timesAlreadySnoozed < maxRepeats)

    companion object {
        /** `maxRepeats == 0` means no limit. */
        const val UNLIMITED = 0

        val DEFAULT = SnoozeConfig()
        val DISABLED = SnoozeConfig(enabled = false)
    }
}

/**
 * How the alarm behaves once it starts ringing.
 *
 * These are two genuinely different things, which is why this is separate from
 * [Alarm.autoSilenceMinutes]. "Stop after 10 minutes" still means a tone
 * looping for ten minutes; it is a safety net on a wake-up alarm, not a way to
 * play something once.
 */
@Serializable
enum class RingBehaviour {
    /**
     * Loops until the user dismisses it, or until [Alarm.autoSilenceMinutes]
     * gives up. The right behaviour for getting someone out of bed.
     */
    UNTIL_DISMISSED,

    /**
     * Plays the tone through exactly once and stops on its own, with no fade-in
     * and no full-screen takeover. This is how a school bell, a factory hooter
     * or a period chime works: it marks a moment and then it is over. Nothing
     * has to be dismissed, because nothing is still ringing.
     */
    PLAY_ONCE,
}

/**
 * A single point in time that rings.
 *
 * [enabled] is the alarm's *own* switch and nothing else is allowed to write
 * it. Turning the containing set off must not touch it - see [AlarmSet] for
 * why that distinction carries the whole product.
 */
@Serializable
data class Alarm(
    val id: Long = NO_ID,
    val setId: Long,
    val label: String = "",
    val time: TimeOfDay,
    val enabled: Boolean = true,
    val days: WeekDays = WeekDays.NONE,
    val ringtone: RingtoneRef = RingtoneRef.Bundled(DEFAULT_RINGTONE_KEY),
    val vibrate: Boolean = true,
    val volumePercent: Int = 80,
    val ringBehaviour: RingBehaviour = RingBehaviour.UNTIL_DISMISSED,
    /**
     * Give up on an unattended alarm after this many minutes; 0 means never,
     * which really does mean it loops forever until dismissed.
     *
     * Ignored when [ringBehaviour] is [RingBehaviour.PLAY_ONCE], because a tone
     * that plays once is already over in seconds.
     */
    val autoSilenceMinutes: Int = 10,
    val snooze: SnoozeConfig = SnoozeConfig.DEFAULT,
    /** Position within its set, so a schedule keeps a stable visual order. */
    val sortIndex: Int = 0,
) {
    init {
        require(volumePercent in 0..100) { "volumePercent out of range: $volumePercent" }
        require(autoSilenceMinutes >= 0) { "autoSilenceMinutes must not be negative" }
    }

    val isOneShot: Boolean get() = days.isEmpty

    /** True when the alarm ends by itself and needs no dismissal. */
    val stopsByItself: Boolean get() = ringBehaviour == RingBehaviour.PLAY_ONCE

    fun shiftedBy(minutes: Int): Alarm = copy(time = time.shiftedBy(minutes))

    /**
     * The settings a *new* alarm in the same set should start from: everything
     * except this alarm's identity and its place in the day.
     *
     * Building a timetable means entering a dozen alarms that differ only in
     * time, so re-picking the tone, the repeat days and the ring behaviour each
     * time is the single most tedious thing about the app.
     */
    fun asTemplateFor(nextTime: TimeOfDay, sortIndex: Int): Alarm = copy(
        id = NO_ID,
        time = nextTime,
        enabled = true,
        sortIndex = sortIndex,
    )

    companion object {
        const val NO_ID = 0L
        const val DEFAULT_RINGTONE_KEY = "dawn_chime"
    }
}
