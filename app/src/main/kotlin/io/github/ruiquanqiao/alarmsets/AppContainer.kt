package io.github.ruiquanqiao.alarmsets

import android.content.Context
import android.content.Intent
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmManagerScheduler
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmRuntime
import io.github.ruiquanqiao.alarmsets.core.alarm.AndroidNextTriggerResolver
import io.github.ruiquanqiao.alarmsets.core.audio.AlarmTonePlayer
import io.github.ruiquanqiao.alarmsets.core.audio.AndroidRingtoneCatalog
import io.github.ruiquanqiao.alarmsets.core.audio.AndroidRingtonePreviewPlayer
import io.github.ruiquanqiao.alarmsets.core.audio.ImportedRingtoneStore
import io.github.ruiquanqiao.alarmsets.core.data.AlarmSetData
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmScheduler
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmSetRepository
import io.github.ruiquanqiao.alarmsets.core.domain.DeleteAlarm
import io.github.ruiquanqiao.alarmsets.core.domain.DeleteAlarmSet
import io.github.ruiquanqiao.alarmsets.core.domain.DuplicateAlarmSet
import io.github.ruiquanqiao.alarmsets.core.domain.NextTriggerResolver
import io.github.ruiquanqiao.alarmsets.core.domain.ResolveNextAlarm
import io.github.ruiquanqiao.alarmsets.core.domain.ResolvePendingTriggers
import io.github.ruiquanqiao.alarmsets.core.domain.RingtoneCatalog
import io.github.ruiquanqiao.alarmsets.core.domain.RingtonePreviewPlayer
import io.github.ruiquanqiao.alarmsets.core.domain.SaveAlarm
import io.github.ruiquanqiao.alarmsets.core.domain.SaveAlarmSet
import io.github.ruiquanqiao.alarmsets.core.domain.ShiftAlarmSet
import io.github.ruiquanqiao.alarmsets.core.domain.SyncSchedule
import io.github.ruiquanqiao.alarmsets.core.domain.TemplateCodec
import io.github.ruiquanqiao.alarmsets.core.domain.TimeProvider
import io.github.ruiquanqiao.alarmsets.core.domain.ToggleAlarm
import io.github.ruiquanqiao.alarmsets.core.domain.ToggleAlarmSet
import io.github.ruiquanqiao.alarmsets.core.domain.UpdateChecker
import io.github.ruiquanqiao.alarmsets.core.update.ApkDownloader
import io.github.ruiquanqiao.alarmsets.core.update.GitHubReleaseUpdateChecker
import io.github.ruiquanqiao.alarmsets.ring.RingActivity

/**
 * Hand-written dependency graph.
 *
 * No DI framework on purpose. The graph is small, and every annotation
 * processor added here is build time paid on every change. More importantly,
 * Hilt is Android-only: wiring the app by hand keeps the composition root the
 * single place that knows about platform types, which is what makes the
 * core modules portable.
 */
class AppContainer(private val context: Context) : AlarmRuntime {

    override val repository: AlarmSetRepository by lazy {
        AlarmSetData.createRepository(context)
    }

    override val timeProvider: TimeProvider = TimeProvider { System.currentTimeMillis() }

    val nextTriggerResolver: NextTriggerResolver by lazy { AndroidNextTriggerResolver() }

    private val resolvePendingTriggers by lazy {
        ResolvePendingTriggers(nextTriggerResolver, timeProvider)
    }

    val resolveNextAlarm by lazy { ResolveNextAlarm(nextTriggerResolver, timeProvider) }

    override val scheduler: AlarmScheduler by lazy {
        AlarmManagerScheduler(context, resolvePendingTriggers)
    }

    private val importedRingtoneStore by lazy { ImportedRingtoneStore(context) }

    override val tonePlayer: AlarmTonePlayer by lazy {
        AlarmTonePlayer(context, importedRingtoneStore)
    }

    val ringtoneCatalog: RingtoneCatalog by lazy {
        AndroidRingtoneCatalog(context, importedRingtoneStore)
    }

    val ringtonePreviewPlayer: RingtonePreviewPlayer by lazy {
        AndroidRingtonePreviewPlayer(context)
    }

    val templateCodec by lazy { TemplateCodec() }

    val updateChecker: UpdateChecker by lazy {
        GitHubReleaseUpdateChecker(
            owner = BuildConfig.UPDATE_REPO_OWNER,
            repo = BuildConfig.UPDATE_REPO_NAME,
            currentVersionName = BuildConfig.VERSION_NAME,
        )
    }

    val apkDownloader by lazy { ApkDownloader(context) }

    // ---- Use cases --------------------------------------------------------

    val syncSchedule by lazy { SyncSchedule(repository, scheduler) }
    val toggleAlarmSet by lazy { ToggleAlarmSet(repository, syncSchedule) }
    val toggleAlarm by lazy { ToggleAlarm(repository, syncSchedule) }
    val shiftAlarmSet by lazy { ShiftAlarmSet(repository, syncSchedule) }
    val saveAlarm by lazy { SaveAlarm(repository, syncSchedule) }
    val deleteAlarm by lazy { DeleteAlarm(repository, syncSchedule) }
    val saveAlarmSet by lazy { SaveAlarmSet(repository, syncSchedule) }
    val deleteAlarmSet by lazy { DeleteAlarmSet(repository, syncSchedule) }
    val duplicateAlarmSet by lazy { DuplicateAlarmSet(repository, syncSchedule) }

    // ---- AlarmRuntime -----------------------------------------------------

    override fun ringIntent(context: Context, alarmId: Long, setId: Long): Intent =
        RingActivity.intent(context, alarmId, setId)

    override fun mainIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
}
