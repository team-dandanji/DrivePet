package com.drivepet.app.ui.welcome

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val store = FakeRecordStore()
    private val active = MutableStateFlow<String?>(null)
    private val kakao = MutableStateFlow<String?>(null)
    private val setCalls = mutableListOf<String>()

    private fun profile(id: String, createdAt: Long, fuel: String = "EV") =
        ProfileEntity(id, null, "차$id", fuel, "STARTER_1", "펫$id", true, null, createdAt)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        kotlinx.coroutines.runBlocking {
            store.upsertProfile(profile("p1", 1L))
            store.upsertProfile(profile("p2", 2L, "HEV"))
            store.upsertProfile(profile("p3", 3L, "ICE"))
        }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = WelcomeViewModel(store, active, { setCalls += it; active.value = it }, kakao)

    @Test fun currentIsActiveOthersRest() = runTest {
        active.value = "p2"
        val v = vm()
        assertEquals(ProfileCard("p2", "펫p2", "차p2", "하이브리드", 1), v.current.value)
        assertEquals(listOf("p1", "p3"), v.others.value.map { it.profileId })
    }

    @Test fun noActivePicksNewest() = runTest {
        val v = vm()
        assertEquals("p3", v.current.value!!.profileId)
        assertEquals("내연기관", v.current.value!!.fuelLabel)
        assertEquals(listOf("p1", "p2"), v.others.value.map { it.profileId })
    }

    @Test fun levelFromLedger() = runTest {
        // 거리 200km = 400 EXP → Lv2
        store.insertIfAbsent(RecordEntity("r1", "p3", null, "TRIP", 10L, payloadJson = "{}", startMm = 0, endMm = 200_000_000, valid = true, bonusMilli = 0))
        val v = vm()
        assertEquals(2, v.current.value!!.level)
        assertEquals(1, v.others.value.first().level)
    }

    @Test fun chooseSetsActive() = runTest {
        val v = vm()
        v.choose("p1")
        assertEquals(listOf("p1"), setCalls)
    }

    @Test fun enterSavesCurrentUnlessAlreadyActive() = runTest {
        val v = vm()
        v.enter()
        assertEquals(listOf("p3"), setCalls)
        v.enter()
        assertEquals(listOf("p3"), setCalls) // 이미 활성이면 그대로
    }

    @Test fun kakaoFollowsAuth() = runTest {
        val v = vm()
        assertNull(v.kakao.value)
        kakao.value = "지우"
        assertEquals("지우", v.kakao.value)
        kakao.value = null
        assertNull(v.kakao.value)
    }
}
