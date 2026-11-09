package com.drivepet.vehicle.source

import com.drivepet.vehicle.model.StatePatch

/** 차 상태를 밀어 넣는 곳. [com.drivepet.vehicle.VehicleRepository]가 구현한다. 어느 스레드에서 불러도 된다. */
fun interface VehicleSink {
    fun update(patch: StatePatch)
}

/** 차 상태의 출처. 실제 차 / 가짜 / 시나리오 재생이 같은 모양을 가진다. */
interface VehicleSource {
    val name: String
    fun start(sink: VehicleSink)
    fun stop()
}
