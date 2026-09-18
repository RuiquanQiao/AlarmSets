package io.github.ruiquanqiao.alarmsets.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Receives the `AlarmManager` broadcast and hands off to the ringing service.
 *
 * Kept deliberately thin. A receiver's `onReceive` runs on the main thread with
 * a short deadline, so it must not touch the database or start playback - it
 * only starts the foreground service that does.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) {
            Log.w(TAG, "broadcast without a valid alarm id: ${intent.action}")
            return
        }

        val setId = intent.getLongExtra(AlarmIntents.EXTRA_SET_ID, -1L)
        val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)

        val service = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_START
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmIntents.EXTRA_SET_ID, setId)
            putExtra(
                AlarmIntents.EXTRA_SNOOZE_COUNT,
                if (intent.action == AlarmIntents.ACTION_SNOOZE_FIRED) snoozeCount else 0,
            )
        }

        runCatching {
            ContextCompat.startForegroundService(context, service)
        }.onFailure { error ->
            // Android 12+ can refuse a foreground service start from the
            // background in some states. Log loudly: a silent failure here is
            // an alarm that never rings.
            Log.e(TAG, "could not start ring service for alarm $alarmId", error)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        /** Android 14 tightened foreground service start rules; used for logging context. */
        val isApi34OrLater: Boolean get() = Build.VERSION.SDK_INT >= 34
    }
}
