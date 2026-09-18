package io.github.ruiquanqiao.alarmsets.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmSetEntity::class, AlarmEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AlarmSetsDatabase : RoomDatabase() {

    abstract fun alarmSetDao(): AlarmSetDao

    companion object {
        private const val NAME = "alarmsets.db"

        @Volatile
        private var instance: AlarmSetsDatabase? = null

        fun get(context: Context): AlarmSetsDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AlarmSetsDatabase =
            Room.databaseBuilder(context, AlarmSetsDatabase::class.java, NAME)
                // No destructive fallback. Losing a user's alarms on an upgrade
                // is not an acceptable failure mode for an alarm clock; every
                // schema change gets a real migration.
                .build()
    }
}
