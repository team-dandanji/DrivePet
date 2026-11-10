package com.drivepet.app.drive

import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.ExitItem
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.isOk
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** items: 항목 → 해결됨. lockUnknown이면 잠금 상태를 읽을 수 없어 DOORS_UNLOCKED는 해결되지 않는다. */
data class ExitCheck(val items: Map<ExitItem, Boolean>, val lockUnknown: Boolean) {
    val allResolved: Boolean get() = items.values.all { it }
}

class ExitCheckTracker {
    private val _open = MutableStateFlow<ExitCheck?>(null)
    /** null = 점검 화면 없음. */
    val open: StateFlow<ExitCheck?> = _open

    /** 사용자가 점검 화면을 닫는다. 다음 PreExitCheck가 오면 다시 열린다. */
    fun dismiss() { _open.value = null }

    fun onEvent(e: DriveEvent) {
        if (e is DriveEvent.PreExitCheck) {
            _open.value = ExitCheck(e.items.associateWith { false }, lockUnknown = false)
        }
    }

    fun onState(s: VehicleState) {
        val cur = _open.value ?: return
        val gear = s.gear.valueOrNull()
        if (gear != null && gear != Gear.P) {
            _open.value = null
            return
        }
        val items = cur.items.mapValues { (item, done) ->
            done || when (item) {
                ExitItem.WINDOW_OPEN -> s.driverWindow.valueOrNull() == 0
                ExitItem.HEADLIGHT_ON -> (s.lowBeam.isOk || s.highBeam.isOk) && !s.headlightOn
                ExitItem.DOORS_UNLOCKED -> s.doorsLocked.valueOrNull() == true
            }
        }
        _open.value = ExitCheck(items, !s.doorsLocked.isOk)
    }
}
