package com.drivepet.app.ui

import com.drivepet.app.drive.isDriving
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteForTest {
    private data class Case(
        val name: String, val expected: Forced,
        val loaded: Boolean = true, val hasProfile: Boolean = true, val hasAnyProfile: Boolean = true, val driving: Boolean = false,
        val exitOpen: Boolean = false, val settlement: Boolean = false, val parked: Boolean = true,
        val doorOpenedParked: Boolean = false,
    )

    private val cases = listOf(
        Case("평상시", Forced.NONE),
        Case("주행 중이면 무엇보다 먼저 DRIVING", Forced.DRIVING, driving = true, exitOpen = true, settlement = true, hasProfile = false),
        Case("프로필 없어도 주행 중이면 DRIVING", Forced.DRIVING, driving = true, hasProfile = false),
        Case("로딩 전에도 주행 중이면 DRIVING", Forced.DRIVING, driving = true, loaded = false),
        Case("문이 안 열렸으면 정산이 하차 점검보다 앞", Forced.SETTLEMENT, exitOpen = true, settlement = true),
        Case("정차 중 문이 열렸으면 하차 점검이 정산보다 앞", Forced.EXIT_CHECK, exitOpen = true, settlement = true, doorOpenedParked = true),
        Case("정산이 없으면 하차 점검", Forced.EXIT_CHECK, exitOpen = true),
        Case("주행 중이면 문이 열려도 DRIVING", Forced.DRIVING, driving = true, exitOpen = true, settlement = true, doorOpenedParked = true),
        Case("프로필이 없으면 하차 점검보다 ONBOARDING", Forced.ONBOARDING, exitOpen = true, hasProfile = false, hasAnyProfile = false),
        Case("정차 중 정산 대기 → SETTLEMENT", Forced.SETTLEMENT, settlement = true),
        Case("정차가 아니면 정산은 보류", Forced.NONE, settlement = true, parked = false),
        Case("프로필이 없으면 정산보다 ONBOARDING", Forced.ONBOARDING, settlement = true, hasProfile = false, hasAnyProfile = false),
        Case("읽기 완료 후 프로필 없음 → ONBOARDING", Forced.ONBOARDING, hasProfile = false, hasAnyProfile = false),
        Case("읽기 전에는 프로필 없음으로 보지 않는다", Forced.NONE, hasProfile = false, loaded = false),
        Case("활성 프로필은 없지만 프로필이 있으면 WELCOME", Forced.WELCOME, hasProfile = false, hasAnyProfile = true),
        Case("프로필이 하나도 없으면 ONBOARDING", Forced.ONBOARDING, hasProfile = false, hasAnyProfile = false),
        Case("주행 중이면 WELCOME 조건이어도 DRIVING", Forced.DRIVING, driving = true, hasProfile = false, hasAnyProfile = true),
        Case("읽기 전에는 WELCOME도 아니다", Forced.NONE, hasProfile = false, hasAnyProfile = true, loaded = false),
    )

    @Test
    fun routeFor_table() {
        for (c in cases) {
            assertEquals(c.name, c.expected, routeFor(c.loaded, c.hasProfile, c.hasAnyProfile, c.driving, c.exitOpen, c.settlement, c.parked, c.doorOpenedParked))
        }
    }

    @Test
    fun isDriving_truthTable() {
        fun st(ux: Signal<Boolean>, gear: Signal<Gear>) = VehicleState(uxRestricted = ux, gear = gear)
        val d = Signal.Ok(Gear.D, 0); val r = Signal.Ok(Gear.R, 0); val p = Signal.Ok(Gear.P, 0); val n = Signal.Ok(Gear.N, 0)
        assertEquals(true, isDriving(st(Signal.Ok(true, 0), p)))
        assertEquals(true, isDriving(st(Signal.Ok(true, 0), Signal.Unknown)))
        assertEquals(false, isDriving(st(Signal.Ok(false, 0), d))) // 제한이 읽히면 그 값이 우선
        assertEquals(false, isDriving(st(Signal.Ok(false, 0), Signal.Unknown)))
        for (ux in listOf(Signal.Unknown, Signal.Unsupported, Signal.NoPermission)) {
            assertEquals(true, isDriving(st(ux, d)))
            assertEquals(true, isDriving(st(ux, r)))
            assertEquals(false, isDriving(st(ux, p)))
            assertEquals(false, isDriving(st(ux, n)))
            assertEquals(false, isDriving(st(ux, Signal.Unknown)))
        }
    }

    /** 06 완료 직후: WELCOME 판정이 늦게 돌아도 그 사이 활성 프로필이 정해졌으면 라운지를 01로 되돌리지 않는다. */
    @Test fun resetTargetRechecksActiveProfile() {
        assertEquals(null, resetTarget(Routes.LOUNGE, Forced.WELCOME, noActive = false))
        assertEquals(Routes.WELCOME, resetTarget(Routes.LOUNGE, Forced.WELCOME, noActive = true)) // 다른 곳에서 활성 프로필이 지워짐
        assertEquals(null, resetTarget(Routes.ONBOARD_MATE, Forced.WELCOME, noActive = true))
        assertEquals(null, resetTarget(Routes.WELCOME, Forced.WELCOME, noActive = true))
        assertEquals(linkRoute("scan"), resetTarget(Routes.LOUNGE, Forced.ONBOARDING, noActive = true))
        assertEquals(null, resetTarget(Routes.ONBOARD_SCAN, Forced.ONBOARDING, noActive = true))
        assertEquals(null, resetTarget(Routes.LOUNGE, Forced.NONE, noActive = false))
    }

    @Test
    fun restrictedCopy_introVsGeneral() {
        val intro = "안전하게 주차한 뒤 시작해주세요"
        val general = "주행 중에는 서비스 사용이 제한돼요"
        assertEquals(intro, restrictedCopy(Routes.ONBOARD_SCAN, hasProfile = true).first)
        assertEquals(intro, restrictedCopy(Routes.LINK, hasProfile = true).first)
        assertEquals(intro, restrictedCopy(Routes.WELCOME, hasProfile = true).first)
        assertEquals(intro, restrictedCopy(Routes.LOUNGE, hasProfile = false).first) // 프로필 없이 주행 중에 처음 연 경우
        assertEquals(general, restrictedCopy(Routes.LOUNGE, hasProfile = true).first)
        assertEquals(general, restrictedCopy(null, hasProfile = true).first)
        assertEquals("계정 연결과 초기 설정은 주차 중에만 이용할 수 있어요\n주행 중에는 화면을 조작하지 마세요", restrictedCopy(Routes.LOUNGE, hasProfile = false).second)
        assertEquals("안전을 위해 주행 중에는 화면을 쓸 수 없어요\n차를 세우면 다시 이용할 수 있어요", restrictedCopy(Routes.LOUNGE, hasProfile = true).second)
    }
}
