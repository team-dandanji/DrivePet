package com.drivepet.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.InterruptedTripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 DB 파일(순수 SQL)을 만들어 Room v2로 열고 1→2 마이그레이션이 데이터를 지키며 새 테이블을 만드는지 본다.
 * 스키마를 내보내지 않으므로 v1 DDL은 v2 DB에서 interrupted_trip만 빼고 베껴 쓴다(v1·v2에서 나머지 테이블은 같다).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After fun tearDown() { context.deleteDatabase(name); context.deleteDatabase("$name-src") }

    private fun v1Ddl(): List<String> {
        val src = Room.databaseBuilder(context, AppDatabase::class.java, "$name-src").build()
        try {
            return src.openHelper.writableDatabase.query(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT IN ('interrupted_trip', 'room_master_table') AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
            ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        } finally { src.close() }
    }

    @Test fun migrates1To2KeepingData() = runTest {
        val ddl = v1Ddl()
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            ddl.forEach(raw::execSQL)
            raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            raw.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'v1-hash')")
            raw.execSQL("INSERT INTO profile VALUES ('p', NULL, '아이오닉5', 'EV', 'STARTER_1', '모모', 1, 72.6, 0)")
            raw.execSQL("INSERT INTO trip_in_progress VALUES ('p', 5, '{}', '{}', 1, 9)")
            raw.version = 1
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
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
}
