package io.github.ruiquanqiao.alarmsets.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Rebuilds the schedule after events that silently wipe or invalidate pending
 * alarms.
 *
 * `AlarmManager` registrations do not survive a reboot, and a time-zone or
 * clock change moves every wall-clock alarm to a different instant. Missing any
 * of these means alarms quietly stop working, which users experience as the app
 * being broken rather than the platform.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val runtime = AlarmRuntimeHolder.peek()
        if (runtime == null) {
            Log.e(TAG, "runtime not installed; cannot rebuild schedule after $action")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sets = runtime.repository.observeSets().first()
                runtime.scheduler.sync(sets)
                Log.i(TAG, "rebuilt schedule after $action (${sets.size} sets)")
            } catch (error: Throwable) {
                Log.e(TAG, "failed to rebuild schedule after $action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
