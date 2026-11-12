package com.drivepet.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.sync.RoomSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 이전 버전 DB 파일(순수 SQL)을 만들어 Room으로 열고 마이그레이션이 데이터를 지키는지 본다.
 * 스키마 JSON은 v3부터만 있으므로 v1·v2 DDL은 여기에 직접 적는다(v1은 v2에서 interrupted_trip만 뺀 것).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After fun tearDown() { context.deleteDatabase(name) }

    private val v1Ddl = listOf(
        "CREATE TABLE IF NOT EXISTS `profile` (`profileId` TEXT NOT NULL, `accountId` TEXT, `vehicleName` TEXT NOT NULL, `fuelType` TEXT NOT NULL, `starterType` TEXT NOT NULL, `petNickname` TEXT NOT NULL, `onboardingCompleted` INTEGER NOT NULL, `batteryKwh` REAL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`))",
        "CREATE TABLE IF NOT EXISTS `record` (`recordId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `accountId` TEXT, `kind` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `ruleVersion` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `startMm` INTEGER, `endMm` INTEGER, `valid` INTEGER, `bonusMilli` INTEGER, `localDate` TEXT, `itemId` TEXT, `missionId` TEXT, `periodKey` TEXT, `rewardMilli` INTEGER, PRIMARY KEY(`recordId`))",
        "CREATE TABLE IF NOT EXISTS `trip_in_progress` (`profileId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `summaryJson` TEXT NOT NULL, `metricsJson` TEXT NOT NULL, `interrupted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`))",
        "CREATE TABLE IF NOT EXISTS `warning_seen` (`profileId` TEXT NOT NULL, `name` TEXT NOT NULL, `firstSeenDate` TEXT NOT NULL, PRIMARY KEY(`profileId`, `name`))",
        "CREATE TABLE IF NOT EXISTS `dev_seed` (`profileId` TEXT NOT NULL, `seedMilli` INTEGER NOT NULL, PRIMARY KEY(`profileId`))",
    )

    /** Room이 "다른 버전의 DB"로 보도록 identity hash가 다른 room_master_table을 심고 version을 맞춘다. */
    private fun createRaw(version: Int, ddl: List<String>, block: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            ddl.forEach(raw::execSQL)
            raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            raw.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'old-hash')")
            block(raw)
            raw.version = version
        }
    }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    @Test fun migrates1To2KeepingData() = runTest {
        createRaw(1, v1Ddl) { raw ->
            raw.execSQL("INSERT INTO profile VALUES ('p', NULL, '아이오닉5', 'EV', 'STARTER_1', '모모', 1, 72.6, 0)")
            raw.execSQL("INSERT INTO trip_in_progress VALUES ('p', 5, '{}', '{}', 1, 9)")
        }

        val db = open()
        try {
            val store = RoomRecordStore(db)
            assertEquals("모모", store.profiles().first().single().petNickname)
            assertEquals(5L, store.inProgress("p")!!.startedAt)
            assertTrue(store.interruptedTrips("p").first().isEmpty())
            store.saveInterrupted(InterruptedTripEntity("p", 5, "{}", 1.5, 10))
            assertEquals(listOf(5L), store.interruptedTrips("p").first().map { it.startedAt })
            store.deleteInterrupted("p", 5)
            assertTrue(store.interruptedTrips("p").first().isEmpty())
        } finally { db.close() }
    }

    @Test fun migrates2To3KeepingData() = runTest {
        val v2Ddl = v1Ddl + "CREATE TABLE IF NOT EXISTS `interrupted_trip` (`profileId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `summaryJson` TEXT NOT NULL, `distanceKm` REAL, `interruptedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `startedAt`))"
        createRaw(2, v2Ddl) { raw ->
            raw.execSQL("INSERT INTO profile VALUES ('p', 'acc', '아이오닉5', 'EV', 'STARTER_1', '모모', 1, 72.6, 7)")
            raw.execSQL("INSERT INTO record (recordId, profileId, accountId, kind, occurredAt, ruleVersion, payloadJson, localDate) VALUES ('r', 'p', 'acc', 'ATTENDANCE', 9, 'growth-v1', '{}', '2026-11-10')")
        }

        val db = open()
        try {
            val p = db.profileDao().profiles().first().single()
            assertEquals("모모", p.petNickname)
            assertEquals(72.6, p.batteryKwh!!, 0.0)
            assertEquals(7L, p.createdAt)
            assertNull(p.stateRevision)
            assertNull(p.deletedAt)
            val r = db.recordDao().records("p").first().single()
            assertEquals("2026-11-10", r.localDate)
            assertNull(r.syncState)
            assertNull(RoomSyncStore(db).cursor("acc")) // sync_cursor 테이블이 있어야 조회가 된다
            db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'index_record_profileId'").use {
                assertTrue(it.moveToFirst())
            }
        } finally { db.close() }
    }

    @Test fun migrate3To4KeepsData() = runTest {
        val v3Ddl = v1Ddl.map { if (it.contains("`profile`")) it.replace(", PRIMARY KEY(`profileId`))", ", `stateRevision` INTEGER, `deletedAt` INTEGER, PRIMARY KEY(`profileId`))") else it }
            .map { if (it.contains("TABLE IF NOT EXISTS `record`")) it.replace(", PRIMARY KEY(`recordId`))", ", `syncState` TEXT, PRIMARY KEY(`recordId`))") else it } +
            "CREATE TABLE IF NOT EXISTS `interrupted_trip` (`profileId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `summaryJson` TEXT NOT NULL, `distanceKm` REAL, `interruptedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `startedAt`))" +
            "CREATE INDEX IF NOT EXISTS `index_record_profileId` ON `record` (`profileId`)" +
            "CREATE TABLE IF NOT EXISTS `sync_cursor` (`accountId` TEXT NOT NULL, `cursor` TEXT NOT NULL, PRIMARY KEY(`accountId`))"
        createRaw(3, v3Ddl) { raw ->
            raw.execSQL("INSERT INTO profile VALUES ('p', 'acc', '아이오닉5', 'EV', 'STARTER_1', '모모', 1, 72.6, 7, 12, NULL)")
            raw.execSQL("INSERT INTO record (recordId, profileId, accountId, kind, occurredAt, ruleVersion, payloadJson, startMm, endMm, valid, bonusMilli, syncState) VALUES ('t', 'p', 'acc', 'TRIP', 9, 'growth-v1', '{}', 0, 12000000, 1, 15000, 'SYNCED')")
        }

        val db = open()
        try {
            val store = RoomRecordStore(db)
            val p = store.profiles().first().single()
            assertEquals(12L, p.stateRevision)
            val r = store.records("p").first().single()
            assertEquals("SYNCED", r.syncState)
            assertEquals(12_000_000L, r.endMm)
            assertTrue(store.maintenanceItems("p").first().isEmpty())
            assertNull(store.vehicleCare("p").first())
        } finally { db.close() }
    }
}
