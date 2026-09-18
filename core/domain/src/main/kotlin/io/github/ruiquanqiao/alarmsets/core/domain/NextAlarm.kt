package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet

/** The soonest alarm that will ring, with the set it belongs to. */
data class NextAlarm(
    val alarm: Alarm,
    val set: AlarmSet,
    val triggerAtMillis: Long,
)

/**
 * Finds the soonest upcoming alarm across every set.
 *
 * Only alarms passing both switches are considered - see [AlarmSet.activeAlarms].
 */
class ResolveNextAlarm(
    private val nextTriggerResolver: NextTriggerResolver,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(sets: List<AlarmSet>): NextAlarm? {
        val now = timeProvider.nowEpochMillis()
        return sets.flatMap { set ->
            set.activeAlarms.mapNotNull { alarm ->
                nextTriggerResolver.nextTrigger(alarm, now)?.let { NextAlarm(alarm, set, it) }
            }
        }.minByOrNull { it.triggerAtMillis }
    }
}

/**
 * Expands the sets into every pending (alarm, instant) pair the platform should
 * currently hold. This is what an [AlarmScheduler] implementation registers.
 */
class ResolvePendingTriggers(
    private val nextTriggerResolver: NextTriggerResolver,
    private val timeProvider: TimeProvider,
) {
    data class PendingTrigger(
        val alarm: Alarm,
        val setId: Long,
        val triggerAtMillis: Long,
    )

    operator fun invoke(sets: List<AlarmSet>): List<PendingTrigger> {
        val now = timeProvider.nowEpochMillis()
        return sets.flatMap { set ->
            set.activeAlarms.mapNotNull { alarm ->
                nextTriggerResolver.nextTrigger(alarm, now)
                    ?.let { PendingTrigger(alarm, set.id, it) }
            }
        }.sortedBy { it.triggerAtMillis }
    }
}
