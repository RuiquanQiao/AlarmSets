package io.github.ruiquanqiao.alarmsets.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "alarm_sets")
data class AlarmSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /**
     * The set's master switch. This is a mask over the child alarms, never a
     * cascade: toggling it must not write `AlarmEntity.enabled`.
     */
    val enabled: Boolean,
    val accent: String,
    val note: String,
    val sortIndex: Int,
)

@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = AlarmSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val setId: Long,
    val label: String,
    /** Minutes since midnight. */
    @ColumnInfo(name = "minutes_of_day") val minutesOfDay: Int,
    /** The alarm's own switch, independent of its set. */
    val enabled: Boolean,
    /** Seven-bit weekday mask. */
    @ColumnInfo(name = "day_mask") val dayMask: Int,
    /** Serialised [io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef]. */
    @ColumnInfo(name = "ringtone_json") val ringtoneJson: String,
    val vibrate: Boolean,
    @ColumnInfo(name = "volume_percent") val volumePercent: Int,
    /** Name of a [io.github.ruiquanqiao.alarmsets.core.model.RingBehaviour]. */
    @ColumnInfo(name = "ring_behaviour", defaultValue = "UNTIL_DISMISSED")
    val ringBehaviour: String,
    @ColumnInfo(name = "auto_silence_minutes") val autoSilenceMinutes: Int,
    @Embedded(prefix = "snooze_") val snooze: SnoozeEmbedded,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
)

data class SnoozeEmbedded(
    val enabled: Boolean,
    val minutes: Int,
    @ColumnInfo(name = "max_repeats") val maxRepeats: Int,
)

/** A set together with the alarms that belong to it. */
data class AlarmSetWithAlarms(
    @Embedded val set: AlarmSetEntity,
    @Relation(parentColumn = "id", entityColumn = "setId")
    val alarms: List<AlarmEntity>,
)
