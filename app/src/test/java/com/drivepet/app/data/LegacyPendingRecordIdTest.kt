package com.drivepet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class LegacyPendingRecordIdTest {
    /** 서버는 record_id로 UUID만 받는다. 같은 대기는 늘 같은 id, 다른 항목은 다른 id. */
    @Test fun isStableUuid() {
        val id = legacyPendingRecordId("p1", "TIRE")
        assertEquals(id, UUID.fromString(id).toString())
        assertEquals(id, legacyPendingRecordId("p1", "TIRE"))
        assertNotEquals(id, legacyPendingRecordId("p1", "WIPER"))
    }
}
