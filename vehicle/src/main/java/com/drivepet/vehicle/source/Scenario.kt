package com.drivepet.vehicle.source

import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState

/**
 * 시연용 주행 시나리오. assets/scenarios 폴더의 JSON 파일 형식과 같다.
 *
 * ```json
 * { "name": "출근 12km", "speed": 4,
 *   "start": { "gear": "P", "odometerKm": 12034.0 },
 *   "steps": [ { "t": 3, "set": { "gear": "D" } }, ... ] }
 * ```
 * - t: 시작 후 초(재생 배속 적용 전)
 * - set: 바꿀 값. 키 목록은 [KEYS]
 * - gear를 바꾸면서 uxRestricted를 주지 않으면 실제 차처럼 자동으로 맞춘다(P만 false).
 */
data class Scenario(
    val name: String,
    val start: Map<String, Any?>,
    val steps: List<Step>,
    val speed: Double = 1.0,
) {
    data class Step(val t: Double, val set: Map<String, Any?>)

    companion object {
        val KEYS = setOf(
            "gear", "uxRestricted", "odometerKm", "speedKmh", "brakePressed", "steeringDeg", "turnSignal",
            "driverDoorOpen", "doorsLocked", "driverWindow", "windows.driver", "lowBeam", "highBeam",
            "driverBeltBuckled", "energyPct", "warnings",
        )

        /** [values]를 상태에 적용. 알 수 없는 키는 무시한다. */
        fun apply(state: VehicleState, values: Map<String, Any?>, now: Long): VehicleState {
            var s = state
            for ((key, raw) in values) s = applyOne(s, key, raw, now)
            val gear = values["gear"]
            if (gear != null && "uxRestricted" !in values) {
                val g = parseGear(gear)
                if (g != null) s = s.copy(uxRestricted = Signal.Ok(g != Gear.P, now))
            }
            return s
        }

        private fun applyOne(s: VehicleState, key: String, v: Any?, now: Long): VehicleState = when (key) {
            "gear" -> parseGear(v)?.let { s.copy(gear = Signal.Ok(it, now)) } ?: s
            "uxRestricted" -> s.copy(uxRestricted = Signal.Ok(v as Boolean, now))
            "odometerKm" -> s.copy(odometerKm = Signal.Ok((v as Number).toDouble(), now))
            "speedKmh" -> s.copy(speedKmh = Signal.Ok((v as Number).toDouble(), now))
            "brakePressed" -> s.copy(brakePressed = Signal.Ok(v as Boolean, now))
            "steeringDeg" -> s.copy(steeringDeg = Signal.Ok((v as Number).toDouble(), now))
            "turnSignal" -> s.copy(turnSignal = Signal.Ok(TurnSide.valueOf(v.toString()), now))
            "driverDoorOpen" -> s.copy(driverDoorOpen = Signal.Ok(v as Boolean, now))
            "doorsLocked" -> s.copy(doorsLocked = Signal.Ok(v as Boolean, now))
            "driverWindow", "windows.driver" -> s.copy(driverWindow = Signal.Ok((v as Number).toInt().coerceIn(0, 10), now))
            "lowBeam" -> s.copy(lowBeam = Signal.Ok(v as Boolean, now))
            "highBeam" -> s.copy(highBeam = Signal.Ok(v as Boolean, now))
            "driverBeltBuckled" -> s.copy(driverBeltBuckled = Signal.Ok(v as Boolean, now))
            "energyPct" -> s.copy(energyPct = Signal.Ok((v as Number).toInt(), now))
            "warnings" -> s.copy(warnings = Signal.Ok((v as List<*>).map { it.toString() }, now))
            else -> s
        }

        private fun parseGear(v: Any?): Gear? = runCatching { Gear.valueOf(v.toString()) }.getOrNull()

        /** 필드 하나를 '미지원'으로 표시. */
        fun applyUnsupported(s: VehicleState, field: String): VehicleState = when (field) {
            "odometerKm" -> s.copy(odometerKm = Signal.Unsupported)
            "speedKmh" -> s.copy(speedKmh = Signal.Unsupported)
            "steeringDeg" -> s.copy(steeringDeg = Signal.Unsupported)
            "driverBeltBuckled" -> s.copy(driverBeltBuckled = Signal.Unsupported)
            "energyPct" -> s.copy(energyPct = Signal.Unsupported)
            "warnings" -> s.copy(warnings = Signal.NoPermission)
            else -> s
        }
    }
}
