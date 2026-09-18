package io.github.ruiquanqiao.alarmsets.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a ringtone's audio comes from. */
@Serializable
sealed interface RingtoneRef {

    /** A tone shipped with the app, addressed by its stable key. */
    @Serializable
    @SerialName("bundled")
    data class Bundled(val key: String) : RingtoneRef

    /**
     * Audio the user imported. [uri] is an opaque platform string; the app
     * copies imported audio into its own storage on import so the reference
     * cannot rot when the original file moves or its permission lapses.
     *
     * There is deliberately no duration limit anywhere in this model.
     */
    @Serializable
    @SerialName("imported")
    data class Imported(val uri: String, val displayName: String) : RingtoneRef

    /** Whatever the platform considers the default alarm sound. */
    @Serializable
    @SerialName("system_default")
    data object SystemDefault : RingtoneRef

    /** No audio at all. Useful for vibrate-only alarms. */
    @Serializable
    @SerialName("silent")
    data object Silent : RingtoneRef
}

/** Grouping used by the picker UI. */
enum class RingtoneCategory {
    GENERAL,
    SCHOOL,
    IMPORTED,
    SYSTEM,
}

/** A ringtone as presented to the user: a reference plus what to show for it. */
data class RingtoneOption(
    val ref: RingtoneRef,
    val title: String,
    val description: String,
    val category: RingtoneCategory,
    val durationMillis: Long? = null,
)
