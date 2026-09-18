package io.github.ruiquanqiao.alarmsets.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmScheduler
import io.github.ruiquanqiao.alarmsets.core.domain.ResolvePendingTriggers
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps the platform's pending alarms in step with the stored sets.
 *
 * ## Why it cancels everything first
 *
 * `AlarmManager` has no way to enumerate what is currently pending, so there is
 * no way to diff. The only reliable way to reach a known state is to cancel the
 * alarms this class last registered and then register the current set. The last
 * registered ids are kept in memory *and* recomputed from storage on boot, so a
 * process death cannot strand an alarm.
 */
class AlarmManagerScheduler(
    private val context: Context,
    private val resolvePendingTriggers: ResolvePendingTriggers,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Alarm ids currently registered with the platform. */
    private val registered = AtomicReference<Set<Long>>(emptySet())

    override suspend fun sync(sets: List<AlarmSet>): Unit = withContext(io) {
        val triggers = resolvePendingTriggers(sets)
        val wanted = triggers.map { it.alarm.id }.toSet()

        // Drop anything that is no longer wanted.
        (registered.get() - wanted).forEach { cancelTrigger(it) }

        val exact = canScheduleExactAlarms()
        triggers.forEach { trigger ->
            val pendingIntent = triggerPendingIntent(trigger.alarm.id, trigger.setId)
            if (exact) {
                alarmManager.setAlarmClock(
                    // setAlarmClock is the right API for a user-visible alarm:
                    // it is exempt from Doze batching and the system shows the
                    // next-alarm chip in the status bar, which users rely on.
                    AlarmManager.AlarmClockInfo(
                        trigger.triggerAtMillis,
                        showIntentFor(trigger.alarm.id),
                    ),
                    pendingIntent,
                )
            } else {
                // Without exact-alarm permission the best available is an
                // inexact window. The UI warns the user that this is degraded.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger.triggerAtMillis,
                    pendingIntent,
                )
            }
        }

        registered.set(wanted)
        Log.i(TAG, "synced ${triggers.size} alarms (exact=$exact)")
    }

    override suspend fun cancelAll() = withContext(io) {
        registered.get().forEach { cancelTrigger(it) }
        registered.set(emptySet())
    }

    override suspend fun scheduleSnooze(
        alarmId: Long,
        triggerAtMillis: Long,
        snoozeCount: Int,
    ) = withContext(io) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_SNOOZE_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmIntents.snoozeRequestCode(alarmId),
            intent,
            PENDING_FLAGS,
        )
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    override suspend fun cancelSnooze(alarmId: Long) = withContext(io) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_SNOOZE_FIRED
        }
        PendingIntent.getBroadcast(
            context,
            AlarmIntents.snoozeRequestCode(alarmId),
            intent,
            PENDING_FLAGS,
        ).let(alarmManager::cancel)
    }

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun cancelTrigger(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
        }
        PendingIntent.getBroadcast(
            context,
            AlarmIntents.triggerRequestCode(alarmId),
            intent,
            PENDING_FLAGS,
        ).let(alarmManager::cancel)
    }

    private fun triggerPendingIntent(alarmId: Long, setId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmIntents.EXTRA_SET_ID, setId)
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmIntents.triggerRequestCode(alarmId),
            intent,
            PENDING_FLAGS,
        )
    }

    /** Tapping the status-bar alarm chip opens the app. */
    private fun showIntentFor(alarmId: Long): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            AlarmIntents.triggerRequestCode(alarmId),
            launch,
            PENDING_FLAGS,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
