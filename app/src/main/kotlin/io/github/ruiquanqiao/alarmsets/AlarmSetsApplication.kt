package io.github.ruiquanqiao.alarmsets

import android.app.Application
import android.util.Log
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmNotifications
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmSetsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)
        // Receivers and services resolve their dependencies through this.
        AlarmRuntimeHolder.install(container)

        AlarmNotifications.ensureChannels(this)

        // Re-register everything at startup. Cheap, idempotent, and it repairs
        // the schedule after the process was killed, the app was updated, or a
        // boot broadcast was missed.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { container.syncSchedule() }
                .onFailure { Log.e(TAG, "initial schedule sync failed", it) }
        }
    }

    private companion object {
        const val TAG = "AlarmSetsApp"
    }
}
