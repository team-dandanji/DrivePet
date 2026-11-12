package com.drivepet.app.ui.care

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.helper.NaviHelper
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.Choice
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors

/** 09 탭. 라운지 08의 "병원 가기" → REPAIR, "밥 주러 가기" → ENERGY. */
enum class PlaceTab { REPAIR, ENERGY }

fun placeRoute(tab: PlaceTab) = "place/${tab.name}"

/**
 * 09 정비소·충전소 안내. NaviHelper 3.0.5에는 앱 안에서 장소를 검색하는 함수가 없어(공개 API는 경로·경유지·내비 화면 열기뿐)
 * 내비의 장소 검색 화면을 연다(설계 5-5 "검색이 없으면 NaviHelper 화면으로"). 내비에서 고른 곳은 내비가 목적지·경유지로 넣는다.
 */
@Composable
fun PlaceScreen(start: PlaceTab, fuel: FuelType, warning: String?, starter: String, stage: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val navi = remember { NaviLauncher(context.applicationContext) }
    DisposableEffect(navi) { onDispose { navi.release() } }
    var tab by remember { mutableStateOf(start) }
    var failed by remember { mutableStateOf(false) }
    val energy = if (fuel == FuelType.EV) "충전소" else "주유소"
    val target = if (tab == PlaceTab.REPAIR) "정비소" else energy

    val narrow = isNarrowWindow()
    val guide: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice("정비소", tab == PlaceTab.REPAIR, { tab = PlaceTab.REPAIR; failed = false }, Modifier.width(240.dp))
                Choice(if (fuel == FuelType.EV) "충전소" else "충전소 · 주유소", tab == PlaceTab.ENERGY, { tab = PlaceTab.ENERGY; failed = false }, Modifier.width(300.dp))
            }
            OnboardCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("내비게이션에서 가까운 ${target}를 골라 주세요", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("고른 곳은 내비게이션에서 목적지나 경유지로 바로 추가할 수 있어요.", fontSize = 26.sp, color = DpColors.TextTertiary)
                    if (failed) Text("내비게이션을 열지 못했어요. 내비게이션 앱에서 직접 찾아 주세요.", fontSize = 26.sp, color = DpColors.Danger)
                }
            }
        }
    }
    val status: @Composable (Modifier) -> Unit = { m ->
        OnboardCard(m) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("차량 상태", fontSize = 26.sp, color = DpColors.TextTertiary)
                Text(warning?.let { "차량 경고: $it" } ?: if (tab == PlaceTab.REPAIR) "켜진 경고등 없음" else "배터리·연료가 부족해요", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (tab == PlaceTab.REPAIR) "정비소에서 점검을 요청하세요. 다녀온 뒤 내 차 정비소에 관리 기록을 남길 수 있어요." else "가까운 ${energy}에서 충분히 채워 주세요.",
                    fontSize = 24.sp, color = DpColors.TextTertiary,
                )
            }
        }
    }
    Column(Modifier.fillMaxSize().scrollWhen(narrow).padding(40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text("가까운 ${target}를 찾아요", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        // 좁은 창: 탭·안내 카드·차량 상태 카드를 위아래로
        if (narrow) {
            guide(Modifier.fillMaxWidth())
            status(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                guide(Modifier.weight(1f))
                status(Modifier.width(520.dp))
            }
        }
        // Hi-fi "펫 돌보기"처럼 가운데에 캐릭터
        Box((if (narrow) Modifier else Modifier.weight(1f)).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Character(starter, stage, if (tab == PlaceTab.REPAIR) Mood.SICK_1 else Mood.HUNGRY)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (narrow) Arrangement.spacedBy(24.dp) else Arrangement.SpaceBetween) {
            TextButton(onBack, (if (narrow) Modifier.weight(1f) else Modifier.width(360.dp)).height(100.dp).background(DpColors.Secondary, RoundedCornerShape(16.dp))) {
                Text("라운지로 돌아가기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DpColors.Text)
            }
            Box(if (narrow) Modifier.weight(1f) else Modifier) {
                FilledButton("내비게이션에서 찾기", {
                    failed = false
                    val req = if (tab == PlaceTab.ENERGY && fuel == FuelType.EV) NavigationScreenRequest.NearbyChargingStations else NavigationScreenRequest.SearchPlace(target)
                    navi.open(req) { ok -> if (!ok) failed = true }
                }, if (narrow) Modifier.fillMaxWidth() else Modifier.width(520.dp))
            }
        }
    }
}

private const val TAG = "DrivePetNavi"

/** NaviHelper 연결을 열 때 한 번 맺고, 화면을 떠날 때 푼다. 실패(내비 서비스 없음 등)는 [open]의 콜백으로 false. */
private class NaviLauncher(context: Context) {
    private val helper = NaviHelper(context)
    private var ready = false

    fun open(req: NavigationScreenRequest, done: (Boolean) -> Unit) {
        val send = { runCatching { helper.requestNavigationScreen(req) }.onFailure { Log.w(TAG, "requestNavigationScreen 실패", it) }.isSuccess }
        if (ready) { done(send()); return }
        runCatching {
            helper.initialize({ ready = true; done(send()) }, { Log.w(TAG, "NaviHelper 연결 실패", it); done(false) })
        }.onFailure { Log.w(TAG, "NaviHelper 초기화 실패", it); done(false) }
    }

    fun release() { runCatching { helper.release() } }
}
