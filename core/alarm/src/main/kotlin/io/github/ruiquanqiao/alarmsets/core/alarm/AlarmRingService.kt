package io.github.ruiquanqiao.alarmsets.core.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns a ringing alarm: sound, vibration, the full-screen notification, and the
 * auto-silence timeout.
 *
 * A foreground service rather than a bare activity, because the ring has to
 * survive the user navigating away, the screen turning off, and the activity
 * being destroyed.
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null
    private var current: Alarm? = null
    private var currentSetName: String = ""
    private var snoozeCount: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            AlarmIntents.ACTION_DISMISS -> handleDismiss()
            AlarmIntents.ACTION_SNOOZE -> handleSnooze()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val setId = intent.getLongExtra(AlarmIntents.EXTRA_SET_ID, -1L)
        snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)

        val runtime = AlarmRuntimeHolder.peek()
        if (runtime == null) {
            Log.e(TAG, "runtime not installed; cannot ring alarm $alarmId")
            stopSelf()
            return
        }

        acquireWakeLock()

        scope.launch {
            val alarm = runtime.repository.getAlarm(alarmId)
            if (alarm == null) {
                Log.w(TAG, "alarm $alarmId no longer exists")
                stopAndRelease()
                return@launch
            }
            current = alarm
            currentSetName = runtime.repository.getSet(alarm.setId)?.name.orEmpty()

            startForegroundWithNotification(runtime, alarm, setId)
            runtime.tonePlayer.play(
                ref = alarm.ringtone,
                volumePercent = alarm.volumePercent,
                loop = true,
                fadeInMillis = FADE_IN_MILLIS,
            ).onFailure { Log.e(TAG, "tone failed for alarm $alarmId", it) }

            if (alarm.vibrate) startVibration()

            if (alarm.autoSilenceMinutes > 0) {
                autoSilenceJob = scope.launch {
                    delay(alarm.autoSilenceMinutes * 60_000L)
                    Log.i(TAG, "auto-silencing alarm $alarmId")
                    handleDismiss()
                }
            }

            // A one-shot alarm has now done its job and switches itself off;
            // a repeating one just needs its next occurrence booked.
            if (alarm.isOneShot) {
                runtime.repository.setAlarmEnabled(alarm.id, false)
            }
            runtime.scheduler.sync(runtime.repository.observeSets().first())
        }
    }

    private fun startForegroundWithNotification(
        runtime: AlarmRuntime,
        alarm: Alarm,
        setId: Long,
    ) {
        AlarmNotifications.ensureChannels(this)

        val fullScreen = AlarmNotifications.pendingActivity(
            context = this,
            intent = runtime.ringIntent(this, alarm.id, setId),
            requestCode = AlarmIntents.triggerRequestCode(alarm.id),
        )
        val dismiss = AlarmNotifications.pendingServiceAction(
            context = this,
            action = AlarmIntents.ACTION_DISMISS,
            alarmId = alarm.id,
            requestCode = AlarmIntents.triggerRequestCode(alarm.id) + 1,
        )
        val snooze = if (canSnooze(alarm)) {
            AlarmNotifications.pendingServiceAction(
                context = this,
                action = AlarmIntents.ACTION_SNOOZE,
                alarmId = alarm.id,
                requestCode = AlarmIntents.triggerRequestCode(alarm.id) + 2,
            )
        } else {
            null
        }

        val notification = AlarmNotifications.buildRinging(
            context = this,
            alarm = alarm,
            setName = currentSetName,
            fullScreenIntent = fullScreen,
            dismissIntent = dismiss,
            snoozeIntent = snooze,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotifications.RINGING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmNotifications.RINGING_NOTIFICATION_ID, notification)
        }
    }

    private fun canSnooze(alarm: Alarm): Boolean =
        alarm.snooze.enabled &&
            (alarm.snooze.maxRepeats == 0 || snoozeCount < alarm.snooze.maxRepeats)

    private fun handleSnooze() {
        val runtime = AlarmRuntimeHolder.peek() ?: return stopAndRelease()
        val alarm = current ?: return stopAndRelease()
        if (!canSnooze(alarm)) return handleDismiss()

        scope.launch {
            val at = runtime.timeProvider.nowEpochMillis() + alarm.snooze.minutes * 60_000L
            runtime.scheduler.scheduleSnooze(alarm.id, at)
            Log.i(TAG, "snoozed alarm ${alarm.id} for ${alarm.snooze.minutes}m")
            stopAndRelease()
        }
    }

    private fun handleDismiss() {
        val runtime = AlarmRuntimeHolder.peek()
        val alarmId = current?.id
        if (runtime != null && alarmId != null) {
            scope.launch { runtime.scheduler.cancelSnooze(alarmId) }
        }
        stopAndRelease()
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        val pattern = longArrayOf(0, 500, 500)
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { vibrator?.cancel() }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun stopAndRelease() {
        autoSilenceJob?.cancel()
        AlarmRuntimeHolder.peek()?.tonePlayer?.stop()
        stopVibration()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAndRelease()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_START = "io.github.ruiquanqiao.alarmsets.RING_START"
        private const val WAKE_LOCK_TAG = "AlarmSets:ring"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15 * 60 * 1000L
        private const val FADE_IN_MILLIS = 6_000L
    }
}
