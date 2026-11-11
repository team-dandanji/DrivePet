package com.drivepet.app.ui.care

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModelTest {
    private val store = FakeRecordStore()
    private val state = MutableStateFlow(VehicleState(gear = Signal.Ok(Gear.P, 0L), odometerKm = Signal.Ok(24_800.0, 0L)))
    private var now = Instant.parse("2026-11-04T03:00:00Z")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        kotlinx.coroutines.runBlocking { store.upsertProfile(ProfileEntity("p1", null, "아이오닉5", "EV", "STARTER_1", "모모", true, null, 0L)) }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = GarageViewModel(store, state, MutableStateFlow("p1")) { now }

    @Test fun ensureDefaultItemsTwiceCreatesOneSet() = runTest {
        vm(); vm()
        val items = store.maintenanceItems("p1").first()
        assertEquals(setOf("TIRE", "CABIN_FILTER", "WIPER"), items.map { it.kind }.toSet())
        assertEquals(3, items.size)
    }

    @Test fun replaceSameItemTwiceSameDayPaysOnce() = runTest {
        val v = vm()
        val tire = v.ui.value!!.rows.first { it.care.name == "TIRE" }.item.itemId
        val first = v.replace(tire)!!
        now = now.plusSeconds(60)
        val second = v.replace(tire)!!
        val records = store.records("p1").first()
        assertEquals(2, records.count { it.kind == "REPLACEMENT" })
        assertEquals(50_000L, growthTotalMilli(records, 0))
        assertTrue(v.done(first).first()!!.paid)
        val again = v.done(second).first()!!
        assertFalse(again.paid)
        assertEquals(100, again.lifePct)
        assertEquals(24_800, again.odometerKm)
    }

    @Test fun replaceWithUnknownOdometerStoresNull() = runTest {
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 0L), odometerKm = Signal.Unknown)
        val v = vm()
        val wiper = v.ui.value!!.rows.first { it.care.name == "WIPER" }.item.itemId
        val id = v.replace(wiper)!!
        assertTrue(store.records("p1").first().single().payloadJson.contains("\"odometerMm\":null"))
        assertNull(v.ui.value!!.rows.first { it.care.name == "WIPER" }.life.lifePct)
        assertNull(v.done(id).first()!!.lifePct)
    }
}
