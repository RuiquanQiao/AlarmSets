package io.github.ruiquanqiao.alarmsets.core.alarm

import android.content.Context
import android.content.Intent
import io.github.ruiquanqiao.alarmsets.core.audio.AlarmTonePlayer
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmScheduler
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmSetRepository
import io.github.ruiquanqiao.alarmsets.core.domain.TimeProvider

/**
 * What the broadcast receiver and the ringing service need from the rest of the
 * app.
 *
 * Android instantiates receivers and services itself, so they cannot be given
 * constructor dependencies. Rather than have this module depend on `:app` (which
 * would invert the module graph), the app installs an implementation at startup
 * through [AlarmRuntimeHolder].
 */
interface AlarmRuntime {
    val repository: AlarmSetRepository
    val scheduler: AlarmScheduler
    val timeProvider: TimeProvider
    val tonePlayer: AlarmTonePlayer

    /** The full-screen UI shown while an alarm is going off. */
    fun ringIntent(context: Context, alarmId: Long, setId: Long): Intent

    /** Opening the app normally, for the notification's tap target. */
    fun mainIntent(context: Context): Intent
}

object AlarmRuntimeHolder {

    @Volatile
    private var runtime: AlarmRuntime? = null

    fun install(runtime: AlarmRuntime) {
        this.runtime = runtime
    }

    /**
     * Returns null when the process was started for a broadcast before the
     * Application object finished initialising. Callers treat that as "try
     * again", never as a crash - dropping an alarm is worse than logging.
     */
    fun peek(): AlarmRuntime? = runtime

    fun require(): AlarmRuntime =
        runtime ?: error("AlarmRuntime was not installed; call install() from Application.onCreate")
}
