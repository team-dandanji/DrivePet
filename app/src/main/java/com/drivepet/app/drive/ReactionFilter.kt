package com.drivepet.app.drive

import com.drivepet.vehicle.model.DriveEvent

/** 소스 시작·모드 전환 직후 쏟아지는 초기 상태 이벤트가 캐릭터 반응으로 번지지 않게 막는다. */
class ReactionFilter(private val clock: () -> Long, private val graceMs: Long = 3_000) {
    private var startedAt: Long? = null

    fun sourceStarted() {
        startedAt = clock()
    }

    fun allowReaction(e: DriveEvent): Boolean {
        val started = startedAt ?: return true
        val gated = e is DriveEvent.HeadlightOn || e is DriveEvent.WindowOpened ||
            e is DriveEvent.VehicleWarning || e is DriveEvent.DriverDoorOpened
        return !gated || clock() - started >= graceMs
    }
}
