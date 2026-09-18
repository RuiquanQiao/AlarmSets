package io.github.ruiquanqiao.alarmsets.core.audio

import android.content.Context
import io.github.ruiquanqiao.alarmsets.core.domain.RingtoneCatalog
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneCategory
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneOption
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef

class AndroidRingtoneCatalog(
    private val context: Context,
    private val store: ImportedRingtoneStore = ImportedRingtoneStore(context),
) : RingtoneCatalog {

    override suspend fun options(): List<RingtoneOption> = buildList {
        BundledRingtones.ALL.forEach { tone ->
            add(
                RingtoneOption(
                    ref = RingtoneRef.Bundled(tone.key),
                    title = tone.title,
                    description = tone.description,
                    category = tone.category,
                    durationMillis = tone.durationMillis,
                ),
            )
        }
        store.list().forEach { entry ->
            add(
                RingtoneOption(
                    ref = RingtoneRef.Imported(entry.id, entry.displayName),
                    title = entry.displayName,
                    description = formatDuration(entry.durationMillis),
                    category = RingtoneCategory.IMPORTED,
                    durationMillis = entry.durationMillis,
                ),
            )
        }
        add(
            RingtoneOption(
                ref = RingtoneRef.SystemDefault,
                title = context.getString(R.string.ringtone_system_default),
                description = context.getString(R.string.ringtone_system_default_desc),
                category = RingtoneCategory.SYSTEM,
            ),
        )
        add(
            RingtoneOption(
                ref = RingtoneRef.Silent,
                title = context.getString(R.string.ringtone_silent),
                description = context.getString(R.string.ringtone_silent_desc),
                category = RingtoneCategory.SYSTEM,
            ),
        )
    }

    override suspend fun resolve(ref: RingtoneRef): RingtoneOption? = when (ref) {
        is RingtoneRef.Bundled -> BundledRingtones.find(ref.key)?.let { tone ->
            RingtoneOption(ref, tone.title, tone.description, tone.category, tone.durationMillis)
        }

        is RingtoneRef.Imported -> store.find(ref.uri)?.let { entry ->
            RingtoneOption(
                ref = ref,
                title = entry.displayName,
                description = formatDuration(entry.durationMillis),
                category = RingtoneCategory.IMPORTED,
                durationMillis = entry.durationMillis,
            )
        }

        RingtoneRef.SystemDefault, RingtoneRef.Silent -> options().firstOrNull { it.ref == ref }
    }

    override suspend fun importAudio(
        sourceUri: String,
        displayName: String,
    ): Result<RingtoneRef.Imported> =
        store.import(sourceUri, displayName).map { entry ->
            RingtoneRef.Imported(uri = entry.id, displayName = entry.displayName)
        }

    override suspend fun deleteImported(ref: RingtoneRef.Imported) = store.delete(ref.uri)

    private fun formatDuration(millis: Long): String {
        if (millis <= 0L) return ""
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        } else {
            "${seconds}s"
        }
    }
}
