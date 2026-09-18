package io.github.ruiquanqiao.alarmsets.core.data

import io.github.ruiquanqiao.alarmsets.core.data.db.AlarmSetDao
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmSetRepository
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Room-backed implementation of the domain's storage port. */
class RoomAlarmSetRepository(
    private val dao: AlarmSetDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AlarmSetRepository {

    override fun observeSets(): Flow<List<AlarmSet>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    override fun observeSet(setId: Long): Flow<AlarmSet?> =
        dao.observeOne(setId).map { it?.toDomain() }.flowOn(io)

    override suspend fun getSet(setId: Long): AlarmSet? =
        withContext(io) { dao.findOne(setId)?.toDomain() }

    override suspend fun getAlarm(alarmId: Long): Alarm? =
        withContext(io) { dao.findAlarm(alarmId)?.toDomain() }

    override suspend fun upsertSet(set: AlarmSet): Long = withContext(io) {
        if (set.id == AlarmSet.NO_ID && set.alarms.isNotEmpty()) {
            // A set created from a template arrives with its alarms attached.
            dao.insertSetWithAlarms(set.toEntity(), set.alarms.map { it.toEntity() })
        } else {
            val id = dao.upsertSet(set.toEntity())
            if (set.id == AlarmSet.NO_ID) id else set.id
        }
    }

    override suspend fun deleteSet(setId: Long) = withContext(io) {
        // Alarms go with it via the foreign key's cascade.
        dao.deleteSet(setId)
    }

    override suspend fun setSetEnabled(setId: Long, enabled: Boolean) = withContext(io) {
        dao.setSetEnabled(setId, enabled)
    }

    override suspend fun reorderSets(orderedIds: List<Long>) = withContext(io) {
        dao.reorderSets(orderedIds)
    }

    override suspend fun upsertAlarm(alarm: Alarm): Long = withContext(io) {
        val id = dao.upsertAlarm(alarm.toEntity())
        if (alarm.id == Alarm.NO_ID) id else alarm.id
    }

    override suspend fun deleteAlarm(alarmId: Long) = withContext(io) {
        dao.deleteAlarm(alarmId)
    }

    override suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean) = withContext(io) {
        dao.setAlarmEnabled(alarmId, enabled)
    }

    override suspend fun replaceAlarms(setId: Long, alarms: List<Alarm>) = withContext(io) {
        dao.replaceAlarms(setId, alarms.map { it.toEntity() })
    }
}
