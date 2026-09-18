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

    companion object {
        val DEFAULT = SnoozeConfig()
        val DISABLED = SnoozeConfig(enabled = false)
    }
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
    /** Stop ringing unattended after this many minutes. 0 means never. */
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

    fun shiftedBy(minutes: Int): Alarm = copy(time = time.shiftedBy(minutes))

    companion object {
        const val NO_ID = 0L
        const val DEFAULT_RINGTONE_KEY = "dawn_chime"
    }
}
