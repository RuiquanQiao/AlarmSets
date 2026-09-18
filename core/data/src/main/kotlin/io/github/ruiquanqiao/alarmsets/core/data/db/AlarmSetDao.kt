package io.github.ruiquanqiao.alarmsets.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmSetDao {

    @Transaction
    @Query("SELECT * FROM alarm_sets ORDER BY sortIndex ASC, id ASC")
    fun observeAll(): Flow<List<AlarmSetWithAlarms>>

    @Transaction
    @Query("SELECT * FROM alarm_sets WHERE id = :setId")
    fun observeOne(setId: Long): Flow<AlarmSetWithAlarms?>

    @Transaction
    @Query("SELECT * FROM alarm_sets WHERE id = :setId")
    suspend fun findOne(setId: Long): AlarmSetWithAlarms?

    @Transaction
    @Query("SELECT * FROM alarm_sets ORDER BY sortIndex ASC, id ASC")
    suspend fun findAll(): List<AlarmSetWithAlarms>

    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun findAlarm(alarmId: Long): AlarmEntity?

    @Upsert
    suspend fun upsertSet(set: AlarmSetEntity): Long

    @Query("DELETE FROM alarm_sets WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    /**
     * Writes only the master switch. Deliberately narrow: a broader update
     * would make it possible to clobber the child alarms' own switches, which
     * is the behaviour this app exists to avoid.
     */
    @Query("UPDATE alarm_sets SET enabled = :enabled WHERE id = :setId")
    suspend fun setSetEnabled(setId: Long, enabled: Boolean)

    @Query("UPDATE alarm_sets SET sortIndex = :sortIndex WHERE id = :setId")
    suspend fun setSetSortIndex(setId: Long, sortIndex: Int)

    @Upsert
    suspend fun upsertAlarm(alarm: AlarmEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<AlarmEntity>)

    @Update
    suspend fun updateAlarms(alarms: List<AlarmEntity>)

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun deleteAlarm(alarmId: Long)

    @Query("DELETE FROM alarms WHERE setId = :setId")
    suspend fun deleteAlarmsOfSet(setId: Long)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :alarmId")
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean)

    @Transaction
    suspend fun replaceAlarms(setId: Long, alarms: List<AlarmEntity>) {
        deleteAlarmsOfSet(setId)
        insertAlarms(alarms.map { it.copy(id = 0L, setId = setId) })
    }

    @Transaction
    suspend fun insertSetWithAlarms(set: AlarmSetEntity, alarms: List<AlarmEntity>): Long {
        val setId = upsertSet(set)
        val resolved = if (set.id == 0L) setId else set.id
        insertAlarms(alarms.map { it.copy(id = 0L, setId = resolved) })
        return resolved
    }

    @Transaction
    suspend fun reorderSets(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSetSortIndex(id, index) }
    }

    @Query("SELECT COUNT(*) FROM alarm_sets")
    suspend fun countSets(): Int
}
