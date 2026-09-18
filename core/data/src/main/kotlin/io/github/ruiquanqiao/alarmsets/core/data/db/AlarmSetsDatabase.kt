package io.github.ruiquanqiao.alarmsets.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmSetEntity::class, AlarmEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AlarmSetsDatabase : RoomDatabase() {

    abstract fun alarmSetDao(): AlarmSetDao

    companion object {
        private const val NAME = "alarmsets.db"

        /**
         * Adds the ring behaviour column. Existing alarms keep the only
         * behaviour the app had until now, so nobody's schedule changes
         * underneath them on upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN ring_behaviour TEXT NOT NULL " +
                        "DEFAULT 'UNTIL_DISMISSED'",
                )
            }
        }

        @Volatile
        private var instance: AlarmSetsDatabase? = null

        fun get(context: Context): AlarmSetsDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AlarmSetsDatabase =
            Room.databaseBuilder(context, AlarmSetsDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // No destructive fallback. Losing a user's alarms on an upgrade
                // is not an acceptable failure mode for an alarm clock; every
                // schema change gets a real migration.
                .build()
    }
}
