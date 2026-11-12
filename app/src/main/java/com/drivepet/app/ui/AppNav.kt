package com.drivepet.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.drivepet.app.drive.isDriving
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.domain.Growth
import com.drivepet.app.ui.components.Tab
import com.drivepet.app.ui.components.HeaderMenu
import com.drivepet.app.ui.components.MenuItem
import com.drivepet.app.ui.components.TabHeader
import com.drivepet.app.data.PendingCare
import com.drivepet.app.domain.CareItem
import com.drivepet.app.ui.care.CareCourseScreen
import com.drivepet.app.ui.care.CareCourseViewModel
import com.drivepet.app.ui.care.CareDoneScreen
import com.drivepet.app.ui.care.CareRecordPopup
import com.drivepet.app.ui.care.CourseUi
import com.drivepet.app.ui.care.GarageEditScreen
import com.drivepet.app.ui.care.GarageScreen
import com.drivepet.app.ui.care.GarageViewModel
import com.drivepet.app.ui.care.RouteDoneScreen
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.exit.ExitCheckPopup
import com.drivepet.app.ui.exit.exitPopupNeeded
import com.drivepet.app.ui.garage.AccountMenu
import com.drivepet.app.ui.lounge.LoungeScreen
import com.drivepet.app.ui.link.LinkScreen
import com.drivepet.app.ui.link.LinkViewModel
import com.drivepet.app.ui.lounge.LoungeViewModel
import com.drivepet.app.ui.onboarding.CareQuestionScreen
import com.drivepet.app.ui.onboarding.IntroScreen
import com.drivepet.app.ui.onboarding.MateScreen
import com.drivepet.app.ui.onboarding.MileageScreen
import com.drivepet.app.ui.onboarding.TermsScreen
import com.drivepet.app.ui.onboarding.OnboardingViewModel
import com.drivepet.app.ui.onboarding.ScanScreen
import com.drivepet.app.ui.settlement.LevelUpScreen
import com.drivepet.app.ui.settlement.SettlementScreen
import com.drivepet.app.ui.trips.TripsScreen
import com.drivepet.app.ui.welcome.WelcomeScreen
import com.drivepet.app.ui.welcome.WelcomeViewModel
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object Routes {
    const val WELCOME = "welcome"
    const val ONBOARD_INTRO = "onboard/intro"
    const val ONBOARD_TERMS = "onboard/terms"
    const val LINK = "onboard/link?next={next}" // next = scan(기본, 온보딩 이어가기) | welcome(로그인·01에서 계정만 바꾸기)
    const val ONBOARD_SCAN = "onboard/scan"
    const val ONBOARD_CARE = "onboard/care/{index}" // 소모품 질문 하나(0부터)
    const val ONBOARD_MILEAGE = "onboard/mileage"
    const val ONBOARD_MATE = "onboard/mate"
    const val LOUNGE = "lounge"
    const val GARAGE = "garage"
    const val GARAGE_EDIT = "garage/edit"
    const val CARE_DONE = "garage/done/{recordId}"
    const val TRIPS = "trips"
    const val CARE_COURSE = "care/course"
    const val CARE_ROUTE_DONE = "care/route-done"
    const val SETTLEMENT = "settlement"
    const val LEVEL_UP = "levelup"
    const val DEV = "dev"
}

fun linkRoute(next: String) = "onboard/link?next=$next"

/** 레벨 업 화면 경로. 레벨은 경로 인자로 넘긴다. */
fun levelUpRoute(before: Int, after: Int) = "${Routes.LEVEL_UP}/$before/$after"

private val TAB_ROUTES = Tab.entries.associateBy { it.name.lowercase() } // 라우트 문자열 == Tab 이름 소문자

/** 헤더 메뉴: 왼쪽 앱 이름(프로필 수정) · 오른쪽 ⋯(카카오 계정). */
private enum class HeaderMenuKind { APP, MORE }

/** 주행 중이면 true. 대화상자(별도 창)는 주행 팝업 위에 그려지므로 스스로 닫는 데 쓴다. */
val LocalDriving = compositionLocalOf { false }

/** 상태가 정하는 강제 화면. DRIVING·EXIT_CHECK는 보던 화면 위 팝업, SETTLEMENT는 NavHost 위 덮개, ONBOARDING·WELCOME은 NavHost 경로로 보여 준다. */
enum class Forced { NONE, DRIVING, EXIT_CHECK, SETTLEMENT, ONBOARDING, WELCOME }

/**
 * 우선순위: 주행 제한 > 활성 프로필 없음(읽기 완료 후; 저장된 프로필이 있으면 WELCOME, 없으면 ONBOARDING) > (정차 중 운전석 문이 열렸으면 하차 점검 > 정산, 아니면 정산 > 하차 점검) > 평상시.
 * 프로필이 없으면 점검·정산 대상도 없다.
 */
fun routeFor(
    loaded: Boolean, hasProfile: Boolean, hasAnyProfile: Boolean, driving: Boolean, exitOpen: Boolean, settlementPending: Boolean,
    parked: Boolean, doorOpenedParked: Boolean,
): Forced = when {
    driving -> Forced.DRIVING
    loaded && !hasProfile && hasAnyProfile -> Forced.WELCOME
    loaded && !hasProfile -> Forced.ONBOARDING
    exitOpen && doorOpenedParked -> Forced.EXIT_CHECK
    settlementPending && parked -> Forced.SETTLEMENT
    exitOpen -> Forced.EXIT_CHECK
    else -> Forced.NONE
}

/** 온보딩 경로. 새 온보딩 화면은 여기에 넣는다(resetTarget·주행 제한 문구가 함께 쓴다). */
val ONBOARDING_ROUTES: Set<String> = setOf(
    Routes.ONBOARD_INTRO, Routes.ONBOARD_TERMS, Routes.LINK, Routes.ONBOARD_SCAN, Routes.ONBOARD_CARE, Routes.ONBOARD_MILEAGE, Routes.ONBOARD_MATE,
)

private const val PARK_FIRST_TITLE = "안전하게 주차한 뒤 시작해주세요"

/**
 * 주행 중 팝업 문구(제목, 본문). 온보딩·인트로·링크 화면이거나 아직 활성 프로필이 없으면(첫 실행을 주행 중에 연 경우
 * 시작 경로가 라운지여도) 주차 안내, 그 밖에는 사용 제한 안내.
 */
fun restrictedCopy(route: String?, hasProfile: Boolean): Pair<String, String> =
    if (!hasProfile || (route != null && (route in ONBOARDING_ROUTES || route == Routes.WELCOME))) {
        PARK_FIRST_TITLE to "계정 연결과 초기 설정은 주차 중에만 이용할 수 있어요\n주행 중에는 화면을 조작하지 마세요"
    } else {
        "주행 중에는 서비스 사용이 제한돼요" to "안전을 위해 주행 중에는 화면을 쓸 수 없어요\n차를 세우면 다시 이용할 수 있어요"
    }

/**
 * 화면 전환은 상태가 정한다(사용자 탭이 아님). 주행 제한·하차 점검은 보던 화면 위 팝업, 정산은 NavHost 위 덮개라 NavHost의 스택은 그대로이고,
 * 조건이 풀리면 이전 화면이 그대로 보인다. 팝업·덮개가 떠 있는 동안 뒤로 가기는 소비한다.
 * [onFinish]: 하차 점검이 모두 끝나면 앱을 닫는다.
 */
@Composable
fun AppNav(container: AppContainer, onFinish: () -> Unit = {}) {
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
    // 저장된 프로필이 하나라도 있는지(삭제된 것 제외). 첫 값이 오기 전(null)에는 읽기 완료로 보지 않는다.
    val anyProfile by remember { container.store.profiles().map { it.isNotEmpty() } }.collectAsStateWithLifecycle<Boolean?>(null)
    val ready = loaded && anyProfile != null

    val scope = rememberCoroutineScope()
    // 내비 연결은 콜백으로 늦게 완성되므로 미리 맺는다(주행 팝업의 [네비게이션으로 돌아가기]가 첫 탭에 먹도록).
    LaunchedEffect(Unit) { container.navi }
    val pet by remember { petLook(container) }.collectAsStateWithLifecycle(Pair("STARTER_1", 1))

    val driving = isDriving(state)
    val forced = routeFor(
        ready, profile != null, anyProfile == true, driving, exitCheck != null, settlement != null, state.isParked,
        doorOpenedParked = state.isParked && state.driverDoorOpen.valueOrNull() == true,
    )

    // 하차 점검 팝업은 미조치 항목이 보일 때만 뜬다. 한 번 뜨면 모두 정상이 될 때까지(앱이 닫힐 때까지) 유지한다.
    val exitNeeded = exitCheck?.let(::exitPopupNeeded) == true
    var exitShown by remember { mutableStateOf(false) }
    LaunchedEffect(exitCheck == null, exitNeeded) {
        if (exitCheck == null) exitShown = false else if (exitNeeded) exitShown = true
    }
    // 보일 항목이 처음부터 없으면 점검 상태만 조용히 닫는다(앱은 닫지 않는다).
    LaunchedEffect(forced, exitShown, exitNeeded) {
        if (forced == Forced.EXIT_CHECK && !exitShown && !exitNeeded) coordinator.dismissExitCheck()
    }

    if (!ready && !driving) return
    val nav = rememberNavController()
    // 앱을 켤 때 한 번만 정한다(회전·복귀에는 다시 01을 띄우지 않는다). 프로필이 있으면 01, 없으면 온보딩.
    val start = remember {
        when {
            forced == Forced.ONBOARDING -> Routes.ONBOARD_INTRO
            anyProfile == true -> Routes.WELCOME
            else -> Routes.LOUNGE
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val overlay = forced != Forced.NONE && forced != Forced.ONBOARDING && forced != Forced.WELCOME
    // 팝업·덮개 아래 화면은 접근성 트리에서도 숨긴다.
    val hidden = forced == Forced.DRIVING || forced == Forced.SETTLEMENT || (forced == Forced.EXIT_CHECK && exitShown)

    var menu by remember { mutableStateOf<HeaderMenuKind?>(null) }
    LaunchedEffect(overlay) { if (overlay) menu = null }
    // 탈퇴·연결 성공으로 경로가 바뀌면 닫힌다. 실패하면 경로가 그대로라 메뉴에 안내 문구가 남는다.
    LaunchedEffect(route) { menu = null }

    // 입력창(IME)도 별도 창이라 주행이 시작되면 포커스를 풀어 닫는다.
    val focus = LocalFocusManager.current
    LaunchedEffect(driving) { if (driving) focus.clearFocus() }

    CompositionLocalProvider(LocalDriving provides driving) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)) {
                TAB_ROUTES[route]?.let { selected ->
                    TabHeader(
                        selected,
                        onSelect = { tab -> nav.navigate(tab.name.lowercase()) { popUpTo(Routes.LOUNGE); launchSingleTop = true } },
                        onAppMenu = { menu = HeaderMenuKind.APP }, onMoreMenu = { menu = HeaderMenuKind.MORE },
                    )
                }
                NavHost(
                    nav, startDestination = start, modifier = Modifier.weight(1f),
                    enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                ) {
                    composable(Routes.WELCOME) {
                        // 01 -> 07. 온보딩 대기열을 비우고 라운지로 들어간다. 시작·다시 설정은 03(카카오 연결)을 거쳐 04로 간다.
                        WelcomeScreen(
                            viewModel(factory = WelcomeViewModel.factory(container)),
                            onLounge = { nav.navigate(Routes.LOUNGE) { popUpTo(nav.graph.id) { inclusive = true } } }, // 저장이 끝난 뒤 불린다. 헤더 「프로필 수정」으로 들어온 01 아래의 라운지까지 비운다
                            onSetup = { nav.navigate(linkRoute("scan")) },
                            onAccount = { nav.navigate(linkRoute("welcome")) },
                        )
                    }
                    composable(Routes.ONBOARD_INTRO) {
                        IntroScreen(onCreate = { nav.navigate(Routes.ONBOARD_TERMS) }, onLogin = { nav.navigate(linkRoute("welcome")) })
                    }
                    composable(Routes.ONBOARD_TERMS) { entry ->
                        // 약관은 저장하지 않으므로 이 화면 항목에 따로 둔다(01의 다시 설정은 약관을 거치지 않는다).
                        val termsVm: OnboardingViewModel = viewModel(entry, factory = OnboardingViewModel.factory(container))
                        TermsScreen(termsVm, onBack = { nav.popBackStack() }) {
                            container.scope.launch { termsVm.saveTerms() } // 선택 약관(위치)은 이 항목의 ViewModel에만 있어 여기서 저장한다
                            nav.navigate(linkRoute("scan"))
                        }
                    }
                    composable(Routes.LINK, arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = "scan" })) { entry ->
                        val next = entry.arguments?.getString("next") ?: "scan"
                        LinkScreen(
                            viewModel(factory = LinkViewModel.factory(container)), login = next == "welcome",
                            onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null,
                            onLinked = { linked ->
                                container.kickSync() // 내려받기가 실패했거나 올릴 것이 남았으면 이어서 맞춘다
                                if (afterLink(next, linked.restored, anyProfile == true) == Routes.WELCOME) nav.navigate(Routes.WELCOME) { popUpTo(nav.graph.id) { inclusive = true } }
                                else nav.navigate(Routes.ONBOARD_SCAN) { popUpTo(Routes.LINK) { inclusive = true } } // QR은 스택에 남기지 않는다
                            },
                        )
                    }
                    // 차량 정보 → 소모품 질문 → 주행거리 → 메이트가 입력값을 함께 쓰도록 ViewModel은 ONBOARD_SCAN 항목에 둔다.
                    composable(Routes.ONBOARD_SCAN) { entry ->
                        ScanScreen(viewModel(entry, factory = OnboardingViewModel.factory(container)), onBack = { nav.popBackStack() }) {
                            nav.navigate(careRoute(0))
                        }
                    }
                    composable(Routes.ONBOARD_CARE, arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
                        val vm = onboardingVm(nav, entry, container)
                        val i = entry.arguments?.getInt("index") ?: 0
                        CareQuestionScreen(vm, i, onBack = { nav.popBackStack() }) {
                            // 마지막 질문 뒤에는 주행거리. 0.4초 사이에 화면이 바뀌었으면 넘기지 않는다.
                            if (nav.currentBackStackEntry == entry) nav.navigate(if (i + 1 < vm.careQuestions.size) careRoute(i + 1) else Routes.ONBOARD_MILEAGE)
                        }
                    }
                    composable(Routes.ONBOARD_MILEAGE) { entry ->
                        MileageScreen(onboardingVm(nav, entry, container), onBack = { nav.popBackStack() }) { nav.navigate(Routes.ONBOARD_MATE) }
                    }
                    composable(Routes.ONBOARD_MATE) { entry ->
                        MateScreen(
                            onboardingVm(nav, entry, container),
                            onBack = { nav.popBackStack() },
                            // 첫 설치든 01에서 다시 설정하든, 끝나면 스택을 비우고 라운지로.
                            // 저장한 프로필이 활성으로 반영된 뒤 옮긴다. 먼저 옮기면 그 사이 WELCOME 판정이 라운지를 01로 되돌린다.
                            onDone = {
                                scope.launch {
                                    val id = container.settings.activeProfileId.first()
                                    withTimeoutOrNull(1_000) { coordinator.activeProfile.first { it?.profileId == id } }
                                    nav.navigate(Routes.LOUNGE) { popUpTo(nav.graph.id) { inclusive = true } }
                                }
                            },
                        )
                    }
                    composable(Routes.LOUNGE) {
                        // 정비 기록 팝업은 라운지 위에 같은 창 안으로 겹친다(LoungeScreen은 건드리지 않는다).
                        Box(Modifier.fillMaxSize()) {
                            LoungeScreen(
                                viewModel(factory = LoungeViewModel.factory(container)), container.speaker,
                                onCare = { nav.navigate(Routes.CARE_COURSE) },
                            ) { nav.navigate(Routes.DEV) }
                            CareRecordPopup(container, visible = forced == Forced.NONE) { id -> nav.navigate("garage/done/$id") }
                        }
                    }
                    composable(Routes.GARAGE) {
                        GarageScreen(
                            viewModel(factory = GarageViewModel.factory(container)),
                            onEdit = { nav.navigate(Routes.GARAGE_EDIT) },
                            onReplaced = { id -> nav.navigate("garage/done/$id") },
                        )
                    }
                    composable(Routes.GARAGE_EDIT) {
                        GarageEditScreen(viewModel(factory = GarageViewModel.factory(container))) { nav.popBackStack() }
                    }
                    composable(Routes.CARE_DONE, arguments = listOf(navArgument("recordId") { type = NavType.StringType })) { entry ->
                        CareDoneScreen(
                            viewModel(factory = GarageViewModel.factory(container)), entry.arguments?.getString("recordId").orEmpty(),
                            pet.first, pet.second,
                            // 라운지 팝업에서 왔으면 스택에 차 정비소가 없으므로 탭 이동처럼 간다.
                            onGarage = { nav.navigate(Routes.GARAGE) { popUpTo(Routes.LOUNGE); launchSingleTop = true } },
                            onLounge = { nav.navigate(Routes.LOUNGE) { popUpTo(Routes.LOUNGE); launchSingleTop = true } },
                        )
                    }
                    composable(Routes.TRIPS) { TripsScreen(container) }
                    composable(Routes.CARE_COURSE) { entry ->
                        // 정비 항목은 들어올 때 한 번 정한다. ViewModel은 이 항목에 두고 경로 등록 완료 화면이 같이 쓴다.
                        val target by produceState<CareItem?>(null, profile?.profileId) { value = CareCourseViewModel.targetFor(container, profile?.profileId) }
                        target?.let { t ->
                            CareCourseScreen(
                                viewModel(entry, factory = CareCourseViewModel.factory(container, t)),
                                onBack = { nav.popBackStack() }, onRegister = { nav.navigate(Routes.CARE_ROUTE_DONE) },
                            )
                        }
                    }
                    composable(Routes.CARE_ROUTE_DONE) {
                        val courseEntry = remember(it) { nav.getBackStackEntry(Routes.CARE_COURSE) }
                        // 앞 화면이 만든 것을 그대로 받는다(팩토리는 프로세스 복원 때만 쓰인다)
                        val vm: CareCourseViewModel = viewModel(courseEntry, factory = CareCourseViewModel.factory(container, CareItem.TIRE))
                        val ready = vm.ui.collectAsStateWithLifecycle().value as? CourseUi.Ready
                        val stop = ready?.stops?.getOrNull(ready.selected)
                        if (ready == null || stop == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                        val toLounge = { nav.navigate(Routes.LOUNGE) { popUpTo(Routes.LOUNGE); launchSingleTop = true } }
                        RouteDoneScreen(
                            stop.place.name, pet.first, pet.second, onBack = { nav.popBackStack() }, onLounge = toLounge,
                            onStart = {
                                container.navi.startGuidance(stop.place).also { ok ->
                                    if (ok) {
                                        // 방문 후 기록 팝업(Task 5)이 쓴다. 앱 범위에서 저장해 화면을 떠나도 끝까지 쓴다.
                                        profile?.profileId?.let { pid -> container.scope.launch { container.settings.setPendingCare(PendingCare(ready.target.name, stop.place.name, pid)) } }
                                        toLounge()
                                    }
                                }
                            },
                        )
                    }
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
            // 헤더 메뉴: 탭 화면에서만. 팝업·덮개(주행 포함)가 뜨면 닫는다.
            if (!overlay && TAB_ROUTES[route] != null) when (menu) {
                HeaderMenuKind.APP -> HeaderMenu(Alignment.TopStart, { menu = null }) {
                    MenuItem("프로필 수정", { menu = null; nav.navigate(Routes.WELCOME) })
                }
                HeaderMenuKind.MORE -> AccountMenu(container, onLink = { menu = null; nav.navigate(linkRoute("welcome")) }, onDismiss = { menu = null })
                null -> {}
            }
            if (overlay) BackHandler {}
            when (forced) {
                Forced.DRIVING -> {
                    // 팝업: dim이 터치를 삼키고 뒤로 가기는 소비한다. 닫히는 건 주행이 끝날 때뿐.
                    // 프로필 읽기가 끝나기 전에는 프로필이 있는 것으로 보고 경로만 따른다.
                    val (title, body) = restrictedCopy(route, hasProfile = !ready || profile != null)
                    DpPopup(title, body, onDismissRequest = {}) {
                        PopupButton("네비게이션으로 돌아가기", { scope.launch { container.navi.bringToFront() } }, primary = true)
                    }
                }
                Forced.EXIT_CHECK -> if (exitShown) exitCheck?.let { check ->
                    ExitCheckPopup(
                        check,
                        onStay = { scope.launch { coordinator.dismissExitCheck() } },
                        // 대기 중인 정산이 있으면 앱을 닫지 않고 점검만 닫아 정산이 보이게 한다.
                        onAllDone = { scope.launch { coordinator.dismissExitCheck(); if (coordinator.pendingSettlement.value == null) onFinish() } },
                    )
                }
                Forced.SETTLEMENT -> settlement?.let { s ->
                    // 덮개: 아래 화면을 완전히 가리고 터치를 삼킨다(정산은 창 화면이라 Task 8에서 바뀐다).
                    Box(Modifier.fillMaxSize().skyBackground().pointerInput(Unit) { detectTapGestures() }) {
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
                }
                else -> {}
            }
        }
    }
    // 효과가 늦게 돌 수 있어 실행 시점에 활성 프로필을 다시 본다(그 사이 활성이 정해졌으면 01로 되돌리지 않는다).
    LaunchedEffect(forced) {
        // 라운지에서 WELCOME이 잠깐 잡히는 경우(첫 설치의 저장 → 활성 반영 사이)는 활성 프로필이 오는지 조금 기다린 뒤 본다.
        if (forced == Forced.WELCOME && nav.currentDestination?.route == Routes.LOUNGE) {
            withTimeoutOrNull(1_000) { coordinator.activeProfile.first { it != null } }
        }
        resetTarget(nav.currentDestination?.route, forced, coordinator.activeProfile.value == null)?.let { target ->
            nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
}

fun careRoute(index: Int) = "onboard/care/$index"

/**
 * 카카오 연결 뒤 갈 곳. 복원됐거나 01에서 계정만 바꾸는 경우(기기에 프로필이 있음)는 01, 그 밖에는 차량 정보로 온보딩을 이어 간다.
 * 인트로의 「이미 계정이 있어요」로 연결했는데 서버 기록도 기기 프로필도 없으면 01로 가 봐야 곧바로 인트로로 되돌아가므로 이어 간다.
 */
fun afterLink(next: String, restored: Boolean, hasAnyProfile: Boolean): String =
    if (restored || (next == "welcome" && hasAnyProfile)) Routes.WELCOME else Routes.ONBOARD_SCAN

/** ONBOARD_SCAN 항목에 둔 공유 ViewModel(차량 정보 뒤 온보딩 화면들이 쓴다). */
@Composable
private fun onboardingVm(nav: NavHostController, entry: NavBackStackEntry, container: AppContainer): OnboardingViewModel {
    val scanEntry = remember(entry) { nav.getBackStackEntry(Routes.ONBOARD_SCAN) }
    return viewModel(scanEntry, factory = OnboardingViewModel.factory(container))
}

/** 강제 화면이 NavHost 경로일 때 스택을 비우고 갈 곳. 그대로 두면 null. */
fun resetTarget(cur: String?, forced: Forced, noActive: Boolean): String? {
    val onboarding = cur in ONBOARDING_ROUTES
    return when {
        forced == Forced.ONBOARDING && !onboarding -> Routes.ONBOARD_INTRO
        forced == Forced.WELCOME && !onboarding && cur != Routes.WELCOME && noActive -> Routes.WELCOME
        else -> null
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
