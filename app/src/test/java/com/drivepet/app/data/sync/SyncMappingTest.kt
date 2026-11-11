package com.drivepet.app.data.sync

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SyncMappingTest {
    private val profile = ProfileEntity("p-1", "acc", "출퇴근 차량", "EV", "STARTER_1", "드리", true, 77.4, 1_000L)

    // 1부 6-3 예시(측정 0개 트립으로 바꿈: safetyScore·safetyBreakdown null)
    private val tripJson = """{"startedAt":"2026-11-10T08:30:00+09:00","endedAt":"2026-11-10T08:31:20+09:00",""" +
        """"startOdometerMm":12034000000,"endOdometerMm":12046000000,"distanceStatus":"VALID",""" +
        """"settlement":{"distanceMm":12000000,"baseDrivingExp":"24.000","bonusExp":"35.000","safetyScore":null,""" +
        """"safetyStatus":"UNMEASURED","safetyBreakdown":null,"healthScore":100,"healthStatus":"MEASURED"}}"""
    private val endedAtMs = OffsetDateTime.parse("2026-11-10T08:31:20+09:00").toInstant().toEpochMilli()

    @Test fun stateEncodesRequiredNulls() {
        val s = SyncJson.encodeToString(ProfileStateDto.serializer(), profile.toState())
        assertTrue(s, s.contains("\"inspectionDueDate\":null"))
        assertTrue(s, s.contains("\"maintenanceItems\":[]"))
    }

    @Test fun createOpHasTypeAndNoExtraKeys() {
        val op: SyncOp = CreateProfileOp(createOpId("p-1"), "p-1", profile.toState())
        val o = SyncJson.encodeToJsonElement(SyncOp.serializer(), op).jsonObject
        assertEquals(setOf("operationId", "type", "profileId", "state"), o.keys)
        assertEquals("CREATE_PROFILE", o["type"]!!.toString().trim('"'))
    }

    @Test fun tripRecordKeepsPayloadVerbatim() {
        val r = RecordEntity("r-1", "p-1", "acc", "TRIP", endedAtMs, payloadJson = tripJson).toSyncRecord()
        assertEquals(SyncJson.parseToJsonElement(tripJson), r.payload)
        assertEquals(JsonNull, r.payload["settlement"]!!.jsonObject["safetyBreakdown"])
        assertEquals("2026-11-10T08:31:20+09:00", r.occurredAt)
        assertEquals("growth-v1", r.ruleVersion)
    }

    @Test fun tripToEntityDerivesLedger() {
        val payload = SyncJson.parseToJsonElement(tripJson).jsonObject
        val e = SyncRecordDto("r-1", "TRIP", "2026-11-10T08:31:20+09:00", "growth-v1", payload).toEntity("p-1", "acc")!!
        assertEquals(35_000L, e.bonusMilli)
        assertEquals(true, e.valid)
        assertEquals(12_034_000_000L, e.startMm)
        assertEquals(12_046_000_000L, e.endMm)
        assertEquals(endedAtMs, e.occurredAt)
        assertEquals("SYNCED", e.syncState)
        assertEquals("acc", e.accountId)
        assertEquals(payload, SyncJson.parseToJsonElement(e.payloadJson))
    }

    @Test fun attendanceToEntity() {
        val e = SyncRecordDto("r-2", "ATTENDANCE", "2026-11-10T07:00:00+09:00", "growth-v1",
            buildJsonObject { put("localDate", "2026-11-10") }).toEntity("p-1", "acc")!!
        assertEquals("2026-11-10", e.localDate)
        assertEquals("ATTENDANCE", e.kind)
    }

    @Test fun unknownKindSkipped() {
        assertNull(SyncRecordDto("r-3", "FOO", "2026-11-10T07:00:00+09:00", "growth-v1", JsonObject(emptyMap())).toEntity("p-1", "acc"))
    }

    @Test fun restoredProfileHasNoBattery() {
        val state = SyncJson.encodeToJsonElement(ProfileStateDto.serializer(), profile.toState())
        val change = ChangeDto(7, "p-1", "PROFILE_CREATED", "2026-11-10T08:00:00+09:00", JsonObject(mapOf("state" to state)))

        val fresh = (change.toLocal("acc", null) as LocalChange.UpsertProfile).profile
        assertNull(fresh.batteryKwh)
        assertEquals(7L, fresh.stateRevision)
        assertEquals("acc", fresh.accountId)
        assertEquals(OffsetDateTime.parse("2026-11-10T08:00:00+09:00").toInstant().toEpochMilli(), fresh.createdAt)

        val kept = (change.toLocal("acc", profile) as LocalChange.UpsertProfile).profile
        assertEquals(77.4, kept.batteryKwh!!, 0.0)
        assertEquals(7L, kept.stateRevision)
    }
}
