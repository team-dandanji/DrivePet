package com.drivepet.app.drive

import com.drivepet.app.data.AttendancePayload
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.seoulDate
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class AttendanceWatcher(private val store: RecordStore, private val clock: () -> Instant) {
    private val _beltReadable = MutableStateFlow(false)
    /** driverBeltBuckled가 Ok(true)를 한 번이라도 받았으면 true, 이후 유지. 실제 차 모드의 Ok(false) 고정은 "읽을 수 없음"이다. */
    val beltReadable: StateFlow<Boolean> = _beltReadable

    suspend fun onState(profileId: String, s: VehicleState): Boolean {
        if (s.driverBeltBuckled.valueOrNull() != true) return false
        _beltReadable.value = true
        return checkIn(profileId)
    }

    /** 벨트 착용(true)이 한 번도 안 온 차에서, 정차(P) 중에만 수동 출석. */
    suspend fun manualCheckIn(profileId: String, s: VehicleState): Boolean {
        if (_beltReadable.value || !s.isParked) return false
        return checkIn(profileId)
    }

    private suspend fun checkIn(profileId: String): Boolean {
        val now = clock()
        val date = seoulDate(now)
        return store.insertIfAbsent(
            RecordEntity(
                recordId = UUID.nameUUIDFromBytes("att:$profileId:$date".toByteArray()).toString(),
                profileId = profileId,
                accountId = null,
                kind = "ATTENDANCE",
                occurredAt = now.toEpochMilli(),
                payloadJson = Json.encodeToString(AttendancePayload(date.toString())),
                localDate = date.toString(),
            ),
        )
    }
}
