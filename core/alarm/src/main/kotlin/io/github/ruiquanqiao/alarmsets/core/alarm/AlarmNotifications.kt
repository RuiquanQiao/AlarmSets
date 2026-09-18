package io.github.ruiquanqiao.alarmsets.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.ruiquanqiao.alarmsets.core.model.Alarm

object AlarmNotifications {

    const val CHANNEL_RINGING = "alarm_ringing"
    const val CHANNEL_STATUS = "alarm_status"
    const val RINGING_NOTIFICATION_ID = 0x5E7

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RINGING,
                context.getString(R.string.channel_ringing_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_ringing_desc)
                // The service owns sound and vibration so it can ramp the
                // volume and honour the per-alarm tone. Letting the channel
                // play its own sound as well would double up.
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_status_desc)
            },
        )
    }

    fun buildRinging(
        context: Context,
        alarm: Alarm,
        setName: String,
        fullScreenIntent: PendingIntent,
        dismissIntent: PendingIntent,
        snoozeIntent: PendingIntent?,
    ): Notification {
        val title = alarm.label.ifBlank { setName.ifBlank { context.getString(R.string.alarm) } }

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(alarm.time.toString())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // The full-screen intent is what turns the screen on and shows the
            // ring UI over the lock screen.
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .apply {
                if (snoozeIntent != null) {
                    addAction(
                        0,
                        context.getString(R.string.action_snooze),
                        snoozeIntent,
                    )
                }
                addAction(
                    0,
                    context.getString(R.string.action_dismiss),
                    dismissIntent,
                )
            }
            .build()
    }

    fun pendingActivity(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun pendingServiceAction(
        context: Context,
        action: String,
        alarmId: Long,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmRingService::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
