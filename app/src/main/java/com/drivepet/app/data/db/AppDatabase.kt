package com.drivepet.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, RecordEntity::class, TripInProgressEntity::class, InterruptedTripEntity::class, WarningSeenEntity::class, DevSeedEntity::class, SyncCursorEntity::class, MaintenanceItemEntity::class, VehicleCareEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun recordDao(): RecordDao
    abstract fun tripInProgressDao(): TripInProgressDao
    abstract fun interruptedTripDao(): InterruptedTripDao
    abstract fun warningSeenDao(): WarningSeenDao
    abstract fun devSeedDao(): DevSeedDao
    abstract fun syncDao(): SyncDao
    abstract fun maintenanceDao(): MaintenanceDao

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

        /** v2 → v3: 동기화 표시 열·인덱스·커서 테이블 추가. 기존 데이터는 그대로 둔다. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `stateRevision` INTEGER")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `record` ADD COLUMN `syncState` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_record_profileId` ON `record` (`profileId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_cursor` (`accountId` TEXT NOT NULL, `cursor` TEXT NOT NULL, PRIMARY KEY(`accountId`))")
            }
        }

        /** v3 → v4: 소모품 기준·검사일 테이블 추가(기기 안에만). 기존 데이터는 그대로 둔다. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `maintenance_item` (`itemId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`intervalKm` INTEGER NOT NULL, `baselineOdometerMm` INTEGER, `baselineServicedOn` TEXT, `baselineServicedPeriod` TEXT, " +
                        "`archived` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_item_profileId` ON `maintenance_item` (`profileId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `vehicle_care` (`profileId` TEXT NOT NULL, `inspectionDueDate` TEXT, PRIMARY KEY(`profileId`))")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "drivepet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
