package com.drivepet.app.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.AppContainer
import com.drivepet.app.data.normalizeServerUrl
import com.drivepet.app.domain.formatExp
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.VehicleModule.Mode
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.TurnSide
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import retrofit2.HttpException
import java.io.IOException

val SCENARIOS = listOf("commute_12km", "demo_levelup", "demo_exit_warning")

/** 데이터 출처를 바꾸고 시작한다. 설정에 저장돼 다음 실행에도 이어진다. */
fun AppContainer.startMode(mode: Mode) {
    scope.launch { settings.setMode(mode) }
    coordinator.start(mode)
}

/** 시나리오를 골라 REPLAY로 시작한다. */
fun AppContainer.startScenario(name: String) {
    VehicleModule.scenarioAsset = "scenarios/$name.json"
    startMode(Mode.REPLAY)
}

/** 개발자 메뉴. DEMO_TOOLS 빌드에서만 경로가 열린다. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun DevScreen(container: AppContainer, onBack: () -> Unit) {
    val mode by container.settings.mode.collectAsStateWithLifecycle(null)
    val profile by container.coordinator.activeProfile.collectAsStateWithLifecycle()
    val seed by remember(profile?.profileId) { profile?.let { container.store.seed(it.profileId) } ?: flowOf(0L) }
        .collectAsStateWithLifecycle(0L)
    var scenario by remember { mutableStateOf(VehicleModule.scenarioAsset.substringAfterLast('/').removeSuffix(".json")) }
    var confirmReset by remember { mutableStateOf(false) }
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) confirmReset = false }
    val scope = rememberCoroutineScope()
    val fake = VehicleModule.fake
    val isFake = mode == Mode.FAKE
    val auth by container.auth.state.collectAsStateWithLifecycle(null)
    val pending by remember { container.auth.state.map { it.accountId }.flatMapLatest { a -> a?.let(container.syncStore::pendingCount) ?: flowOf(0) } }
        .collectAsStateWithLifecycle(0)
    var serverUrl by remember { mutableStateOf("") }
    var serverError by remember { mutableStateOf(false) }
    var unlinkFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { serverUrl = container.settings.serverUrl.first() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp), Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DevButton("← 라운지", onBack)
            Text("개발자 도구", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        }

        Section("데이터 출처")
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Mode.entries.forEach { m ->
                Row(
                    Modifier.selectable(mode == m, role = Role.RadioButton) { container.startMode(m) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(mode == m, null)
                    Text(m.name, Modifier.padding(start = 8.dp), fontSize = 28.sp)
                }
            }
        }

        Section("시나리오 (눌러서 REPLAY 시작)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SCENARIOS.forEach { s ->
                DevButton((if (mode == Mode.REPLAY && scenario == s) "● " else "") + s, { scenario = s; container.startScenario(s) })
            }
        }

        Section("가짜 차 조작 (FAKE에서만)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("기어"); Gear.entries.forEach { g -> DevButton(g.name, { fake.setGear(g) }, isFake) }
            Label("브레이크"); DevButton("밟기", { fake.setBrake(true) }, isFake); DevButton("떼기", { fake.setBrake(false) }, isFake)
            Label("거리"); DevButton("+1km", { fake.addKm(1.0) }, isFake); DevButton("+12km", { fake.addKm(12.0) }, isFake)
            Label("조향")
            listOf(-120.0, 0.0, 120.0).forEach { d -> DevButton("${d.toInt()}°", { fake.setSteering(d) }, isFake) }
            DevButton("급조향", {
                scope.launch { fake.setSteering(-60.0); delay(300); fake.setSteering(60.0); delay(700); fake.setSteering(0.0) }
            }, isFake)
            Label("방향지시등"); TurnSide.entries.forEach { s -> DevButton(s.name, { fake.setTurnSignal(s) }, isFake) }
            Label("운전석 문"); DevButton("열기", { fake.setDriverDoor(true) }, isFake); DevButton("닫기", { fake.setDriverDoor(false) }, isFake)
            Label("잠금"); DevButton("잠금", { fake.setLocked(true) }, isFake); DevButton("해제", { fake.setLocked(false) }, isFake)
            Label("창문"); listOf(0, 4, 10).forEach { l -> DevButton("$l", { fake.setWindow(l) }, isFake) }
            Label("전조등")
            DevButton("하향", { fake.setLowBeam(true) }, isFake); DevButton("상향", { fake.setHighBeam(true) }, isFake)
            DevButton("끄기", { fake.setLowBeam(false); fake.setHighBeam(false) }, isFake)
            Label("실내등"); DevButton("켜기", { fake.setCabinLight(true) }, isFake); DevButton("끄기", { fake.setCabinLight(false) }, isFake)
            Label("벨트"); DevButton("착용", { fake.setBelt(true) }, isFake); DevButton("해제", { fake.setBelt(false) }, isFake)
            Label("잔량"); DevButton("80%", { fake.setEnergy(80) }, isFake); DevButton("15%", { fake.setEnergy(15) }, isFake)
        }

        Section("시작 EXP (현재 ${formatExp(seed)})")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0L, 370_000L, 850_000L).forEach { milli ->
                DevButton(formatExp(milli), { profile?.let { p -> container.scope.launch { container.store.setSeed(p.profileId, milli) } } }, profile != null)
            }
        }

        Section("서버")
        OutlinedTextField(
            serverUrl, { serverUrl = it; serverError = false }, Modifier.fillMaxWidth(),
            label = { Text("서버 주소", fontSize = 22.sp) }, singleLine = true, isError = serverError,
            supportingText = { if (serverError) Text("http:// 또는 https:// 주소를 넣어 주세요", fontSize = 22.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DevButton("저장", {
                val url = normalizeServerUrl(serverUrl)
                if (url == null) serverError = true else { serverUrl = url; scope.launch { container.settings.setServerUrl(url) } }
            })
            DevButton("지금 동기화", { scope.launch { runCatching { container.sync.run() } } }) // 워커 재시도 대기(backoff)를 거치지 않고 바로 돈다
            DevButton("카카오 연결 해제", {
                // 해제 요청이 실패하면(오프라인 등) 연결 상태를 그대로 둔다.
                scope.launch { unlinkFailed = try { container.account.unlink(); false } catch (e: IOException) { true } catch (e: HttpException) { true } }
            }, auth?.accountId != null)
        }
        val a = auth
        Text(
            "${if (a?.deviceId != null) "기기 등록됨" else "기기 등록 안 됨"} · " +
                "${if (a?.accountId != null) "계정 ${a.nickname ?: ""}".trim() else "계정 미연결"} · 대기 ${pending}건",
            fontSize = 26.sp, color = DpColors.TextTertiary,
        )
        if (unlinkFailed) Text("연결을 해제하지 못했어요", fontSize = 24.sp, color = DpColors.Danger)

        Section("로컬 데이터")
        DevButton("로컬 데이터 초기화", { confirmReset = true })
    }

    if (confirmReset && !driving) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("앱 안의 기록과 설정을 모두 지울까요?", fontSize = 28.sp) },
            confirmButton = {
                TextButton({
                    confirmReset = false
                    // 화면이 온보딩으로 바뀌면 이 화면의 scope는 사라지므로 앱 scope에서 끝까지 지운다. AuthStore(기기 신원)는 남긴다.
                    container.scope.launch { container.wipeLocal() }
                }) { Text("지우기", fontSize = 26.sp, color = DpColors.Danger) }
            },
            dismissButton = { TextButton({ confirmReset = false }) { Text("취소", fontSize = 26.sp) } },
        )
    }
}

@Composable
private fun Section(title: String) = Text(title, Modifier.padding(top = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Label(text: String) = Text(text, Modifier.padding(start = 12.dp, top = 16.dp), fontSize = 24.sp, color = DpColors.TextTertiary)

@Composable
private fun DevButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick, Modifier.height(64.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}
