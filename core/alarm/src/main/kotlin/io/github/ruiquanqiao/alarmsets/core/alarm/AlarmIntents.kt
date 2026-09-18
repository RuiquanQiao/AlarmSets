package io.github.ruiquanqiao.alarmsets.core.alarm

/** Intent contract shared by the scheduler, the receiver and the ring service. */
object AlarmIntents {

    const val ACTION_ALARM_FIRED = "io.github.ruiquanqiao.alarmsets.ALARM_FIRED"
    const val ACTION_SNOOZE_FIRED = "io.github.ruiquanqiao.alarmsets.SNOOZE_FIRED"
    const val ACTION_DISMISS = "io.github.ruiquanqiao.alarmsets.DISMISS"
    const val ACTION_SNOOZE = "io.github.ruiquanqiao.alarmsets.SNOOZE"

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SET_ID = "set_id"
    const val EXTRA_SNOOZE_COUNT = "snooze_count"

    /**
     * PendingIntent request codes must be unique per alarm and stable across
     * process restarts, or cancelling one alarm silently cancels another.
     *
     * Alarm ids are database row ids, so they are unique and stable. Snooze
     * uses a disjoint range so a snoozed alarm and its next scheduled
     * occurrence can be pending at the same time without colliding.
     */
    fun triggerRequestCode(alarmId: Long): Int = (alarmId % Int.MAX_VALUE).toInt()

    fun snoozeRequestCode(alarmId: Long): Int =
        (SNOOZE_OFFSET + (alarmId % (Int.MAX_VALUE - SNOOZE_OFFSET))).toInt()

    private const val SNOOZE_OFFSET = 1_000_000_000L
}
