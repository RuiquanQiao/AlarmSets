package io.github.ruiquanqiao.alarmsets.core.data

import android.content.Context
import io.github.ruiquanqiao.alarmsets.core.data.db.AlarmSetsDatabase
import io.github.ruiquanqiao.alarmsets.core.domain.AlarmSetRepository

/**
 * The only entry point into this module.
 *
 * Callers get an [AlarmSetRepository] - a domain interface - and never see Room
 * at all. That keeps the storage engine an implementation detail: swapping Room
 * for SQLDelight when this app grows other platforms would touch nothing
 * outside this module.
 */
object AlarmSetData {

    fun createRepository(context: Context): AlarmSetRepository =
        RoomAlarmSetRepository(AlarmSetsDatabase.get(context.applicationContext).alarmSetDao())
}
