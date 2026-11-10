package com.drivepet.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, RecordEntity::class, TripInProgressEntity::class, InterruptedTripEntity::class, WarningSeenEntity::class, DevSeedEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun recordDao(): RecordDao
    abstract fun tripInProgressDao(): TripInProgressDao
    abstract fun interruptedTripDao(): InterruptedTripDao
    abstract fun warningSeenDao(): WarningSeenDao
    abstract fun devSeedDao(): DevSeedDao

    companion object {
        /** v1 → v2: 중단된 트립 보관 테이블 추가. 기존 데이터는 그대로 둔다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interrupted_trip` (`profileId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                        "`summaryJson` TEXT NOT NULL, `distanceKm` REAL, `interruptedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `startedAt`))",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "drivepet.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
