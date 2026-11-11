package com.drivepet.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.drivepet.app.AppContainer
import com.drivepet.app.BuildConfig
import com.drivepet.app.dev.DevScreen
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.domain.Growth
import com.drivepet.app.ui.components.Tab
import com.drivepet.app.ui.components.TopTabs
import com.drivepet.app.ui.drive.DrivingScreen
import com.drivepet.app.ui.exit.ExitCheckScreen
import com.drivepet.app.ui.drive.RestrictedEntryDialog
import com.drivepet.app.ui.lounge.LoungeScreen
import com.drivepet.app.ui.lounge.LoungeViewModel
import com.drivepet.app.ui.onboarding.MateScreen
import com.drivepet.app.ui.onboarding.OnboardingViewModel
import com.drivepet.app.ui.onboarding.ScanScreen
import com.drivepet.app.ui.settlement.LevelUpScreen
import com.drivepet.app.ui.settlement.SettlementScreen
import com.drivepet.app.ui.trips.TripsScreen
import com.drivepet.app.ui.theme.skyBackground
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.isOk
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object Routes {
    const val ONBOARD_SCAN = "onboard/scan"
    const val ONBOARD_MATE = "onboard/mate"
    const val LOUNGE = "lounge"
    const val GARAGE = "garage"
    const val TRIPS = "trips"
    const val DRIVING = "driving"
    const val SETTLEMENT = "settlement"
    const val LEVEL_UP = "levelup"
    const val EXIT_CHECK = "exit"
    const val DEV = "dev"
}

/** 레벨 업 화면 경로. 레벨은 경로 인자로 넘긴다. */
fun levelUpRoute(before: Int, after: Int) = "${Routes.LEVEL_UP}/$before/$after"

private val TAB_ROUTES = Tab.entries.associateBy { it.name.lowercase() } // 라우트 문자열 == Tab 이름 소문자

/** 주행 중이면 true. 대화상자(별도 창)는 덮개 위에 그려지므로 스스로 닫는 데 쓴다. */
val LocalDriving = compositionLocalOf { false }

/** 주행 제한이 읽히면 그 값을 따르고, 못 읽으면(Ok 아님) 기어가 D/R일 때 주행 중으로 본다. */
fun isDriving(s: VehicleState): Boolean {
    val ux = s.uxRestricted.valueOrNull()
    if (ux != null) return ux
    val gear = s.gear.valueOrNull()
    return gear == Gear.D || gear == Gear.R
}

/** 상태가 정하는 강제 화면. DRIVING·EXIT_CHECK·SETTLEMENT는 NavHost 위 덮개로, ONBOARDING은 NavHost 경로로 보여 준다. */
enum class Forced { NONE, DRIVING, EXIT_CHECK, SETTLEMENT, ONBOARDING }

/**
 * 우선순위: 주행 제한 > 프로필 없음(읽기 완료 후) > (정차 중 운전석 문이 열렸으면 하차 점검 > 정산, 아니면 정산 > 하차 점검) > 평상시.
 * 프로필이 없으면 점검·정산 대상도 없다.
 */
fun routeFor(
    loaded: Boolean, hasProfile: Boolean, driving: Boolean, exitOpen: Boolean, settlementPending: Boolean, parked: Boolean,
    doorOpenedParked: Boolean,
): Forced = when {
    driving -> Forced.DRIVING
    loaded && !hasProfile -> Forced.ONBOARDING
    exitOpen && doorOpenedParked -> Forced.EXIT_CHECK
    settlementPending && parked -> Forced.SETTLEMENT
    exitOpen -> Forced.EXIT_CHECK
    else -> Forced.NONE
}

/**
 * 화면 전환은 상태가 정한다(사용자 탭이 아님). 주행·하차 점검·정산은 NavHost 위 덮개라 NavHost의 스택은 그대로이고,
 * 조건이 풀리면 이전 화면이 그대로 보인다. 덮개가 떠 있는 동안 뒤로 가기는 소비한다.
 */
@Composable
fun AppNav(container: AppContainer) {
    val coordinator = container.coordinator
    val state by VehicleModule.repository.state.collectAsStateWithLifecycle()
    val profile by coordinator.activeProfile.collectAsStateWithLifecycle()
    val settlement by coordinator.pendingSettlement.collectAsStateWithLifecycle()
    val exitCheck by coordinator.exitCheck.collectAsStateWithLifecycle()
    // activeProfile은 저장소가 처음 값을 내기 전까지 null이라, 읽기가 끝나기 전에는 '프로필 없음'으로 보지 않는다.
    val loaded by produceState(false) {
        container.settings.activeProfileId.first()
        container.store.profiles().first()
        withTimeoutOrNull(300) { coordinator.activeProfile.first { it != null } }
        value = true
    }

    val scope = rememberCoroutineScope()
    val trip by VehicleModule.repository.trip.collectAsStateWithLifecycle()
    val pet by remember { petLook(container) }.collectAsStateWithLifecycle(Pair("STARTER_1", 1))

    val driving = isDriving(state)
    val forced = routeFor(
        loaded, profile != null, driving, exitCheck != null, settlement != null, state.isParked,
        doorOpenedParked = state.isParked && state.driverDoorOpen.valueOrNull() == true,
    )

    // 02 모달: 앱이 처음 본 상태가 D/R(또는 주행 제한)일 때만, 정차하면 사라지고 다시 뜨지 않는다.
    var launchedWhileDriving by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state, driving) {
        if (launchedWhileDriving == null && (state.gear.isOk || state.uxRestricted.isOk)) {
            launchedWhileDriving = driving
        } else if (launchedWhileDriving == true && !driving) {
            launchedWhileDriving = false
        }
    }

    if (!loaded && !driving) return
    val nav = rememberNavController()
    val start = remember { if (forced == Forced.ONBOARDING) Routes.ONBOARD_SCAN else Routes.LOUNGE }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val overlay = forced != Forced.NONE && forced != Forced.ONBOARDING

    // 입력창(IME)도 별도 창이라 주행이 시작되면 포커스를 풀어 닫는다.
    val focus = LocalFocusManager.current
    LaunchedEffect(driving) { if (driving) focus.clearFocus() }

    CompositionLocalProvider(LocalDriving provides driving) {
        Box(Modifier.fillMaxSize()) {
            // 덮개 아래 화면은 접근성 트리에서도 숨긴다.
            Column(Modifier.fillMaxSize().then(if (overlay) Modifier.clearAndSetSemantics {} else Modifier)) {
                TAB_ROUTES[route]?.let { selected ->
                    TopTabs(selected, onSelect = { tab ->
                        nav.navigate(tab.name.lowercase()) { popUpTo(Routes.LOUNGE); launchSingleTop = true }
                    })
                }
                NavHost(
                    nav, startDestination = start, modifier = Modifier.weight(1f),
                    enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                ) {
                    // 두 온보딩 화면이 입력값을 함께 쓰도록 ViewModel은 ONBOARD_SCAN 항목에 둔다.
                    composable(Routes.ONBOARD_SCAN) { entry ->
                        ScanScreen(viewModel(entry, factory = OnboardingViewModel.factory(container))) { nav.navigate(Routes.ONBOARD_MATE) }
                    }
                    composable(Routes.ONBOARD_MATE) {
                        val scanEntry = remember(it) { nav.getBackStackEntry(Routes.ONBOARD_SCAN) }
                        MateScreen(viewModel(scanEntry, factory = OnboardingViewModel.factory(container))) { nav.popBackStack() }
                    }
                    composable(Routes.LOUNGE) {
                        LoungeScreen(viewModel(factory = LoungeViewModel.factory(container)), container.speaker) { nav.navigate(Routes.DEV) }
                    }
                    composable(Routes.GARAGE) { Placeholder("준비 중이에요") }
                    composable(Routes.TRIPS) { TripsScreen(container) }
                    composable(
                        "${Routes.LEVEL_UP}/{before}/{after}",
                        arguments = listOf(navArgument("before") { type = NavType.IntType }, navArgument("after") { type = NavType.IntType }),
                    ) { entry ->
                        val args = entry.arguments
                        LevelUpScreen(pet.first, args?.getInt("before") ?: 1, args?.getInt("after") ?: 1, visible = forced == Forced.NONE) {
                            // 설계: "좋아요" → 라운지. 어디서 왔든(DEV·주행기록 등) 라운지로 돌아간다
                            nav.navigate(Routes.LOUNGE) { popUpTo(Routes.LOUNGE); launchSingleTop = true }
                        }
                    }
                    // 릴리스 빌드(DEMO_TOOLS 꺼짐)에는 경로 자체가 없다.
                    if (BuildConfig.DEMO_TOOLS) composable(Routes.DEV) { DevScreen(container) { nav.popBackStack() } }
                }
            }
            if (overlay) {
                BackHandler {}
                // 덮개: 아래 화면을 완전히 가리고 터치를 삼킨다. Routes.DRIVING/SETTLEMENT/EXIT_CHECK는 이 덮개의 자리(이후 Task가 실제 화면을 넣는다).
                Box(Modifier.fillMaxSize().skyBackground().pointerInput(Unit) { detectTapGestures() }) {
                    when (forced) {
                        Forced.DRIVING -> {
                            DrivingScreen(trip, pet.first, pet.second)
                            if (launchedWhileDriving == true) RestrictedEntryDialog()
                        }
                        Forced.SETTLEMENT -> settlement?.let { s ->
                            val profileId = profile?.profileId
                            // (정산 전, 정산 후) 총 EXP. 전 = 이 기록만 뺀 원장, 후 = 전체 원장(둘 다 시작 EXP 포함).
                            val totals by produceState<Pair<Long, Long>?>(null, s.recordId, profileId) {
                                value = profileId?.let {
                                    val records = container.store.records(it).first()
                                    val seed = container.store.seed(it).first()
                                    growthTotalMilli(records.filter { r -> r.recordId != s.recordId }, seed) to growthTotalMilli(records, seed)
                                }
                            }
                            totals?.let { (before, after) ->
                                SettlementScreen(s, before, after, pet.first, pet.second) {
                                    coordinator.consumeSettlement()
                                    if (s.levelAfter > s.levelBefore) nav.navigate(levelUpRoute(s.levelBefore, s.levelAfter))
                                }
                            }
                        }
                        else -> exitCheck?.let { ExitCheckScreen(it, pet.first, pet.second) { scope.launch { coordinator.dismissExitCheck() } } }
                    }
                }
            }
        }
    }
    LaunchedEffect(forced) { syncRoute(nav, forced) }
}

private fun syncRoute(nav: NavHostController, forced: Forced) {
    val cur = nav.currentDestination?.route
    val onboarding = cur == Routes.ONBOARD_SCAN || cur == Routes.ONBOARD_MATE
    val reset = { target: String -> nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true } } }
    when {
        forced == Forced.ONBOARDING && !onboarding -> reset(Routes.ONBOARD_SCAN)
        forced == Forced.NONE && onboarding -> reset(Routes.LOUNGE)
    }
}

/** 12에 보일 캐릭터(계열, 단계). 프로필이 없으면 기본값. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun petLook(c: AppContainer): Flow<Pair<String, Int>> =
    combine(c.settings.activeProfileId, c.store.profiles()) { id, list -> list.firstOrNull { it.profileId == id } }
        .flatMapLatest { p ->
            if (p == null) flowOf(Pair("STARTER_1", 1))
            else combine(c.store.records(p.profileId), c.store.seed(p.profileId)) { records, seed ->
                Pair(p.starterType, Growth.level(growthTotalMilli(records, seed)))
            }
        }

@Composable
private fun Placeholder(title: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(24.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Text(title, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        extra()
    }
}
