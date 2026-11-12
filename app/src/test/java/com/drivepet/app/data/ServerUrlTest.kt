package com.drivepet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun acceptsAndAddsSlash() {
        assertEquals("http://10.0.2.2:8080/api/v1/", normalizeServerUrl(" http://10.0.2.2:8080/api/v1 "))
    }

    @Test fun keepsHttps() {
        assertEquals("https://mac.tail1234.ts.net/api/v1/", normalizeServerUrl("https://mac.tail1234.ts.net/api/v1/"))
    }

    @Test fun rejectsOthers() {
        listOf("10.0.2.2:8080", "ftp://x", "").forEach { assertNull(it, normalizeServerUrl(it)) }
    }
}
