package com.drivepet.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.AppContainer
import com.drivepet.app.BuildConfig
import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.toLedger
import com.drivepet.app.data.tripPayload
import com.drivepet.app.dev.SCENARIOS
import com.drivepet.app.dev.startScenario
import com.drivepet.app.domain.formatExp
import com.drivepet.app.domain.TripLedger
import com.drivepet.app.domain.tripPaidMilli
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.theme.DpColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M'월' d'일' HH:mm").withZone(ZoneId.of("Asia/Seoul"))

/** 목록 한 줄. exp가 null이면 EXP 칸을 비운다(중단된 트립). */
data class TripRow(val date: String, val distance: String, val safety: String, val exp: String?, val interrupted: Boolean = false)

private fun formatDate(ms: Long) = DATE_FORMAT.format(Instant.ofEpochMilli(ms))

/** 줄 하나. [paidMilli]는 원장이 이 트립에 실제로 준 EXP. */
private fun tripRowOf(r: RecordEntity, paidMilli: Long): TripRow {
    val s = r.tripPayload()?.settlement
    return TripRow(
        formatDate(r.occurredAt),
        s?.distanceMm?.let { "%.1f km".format(Locale.KOREA, it / 1_000_000.0) } ?: "미측정",
        s?.safetyScore?.let { "${it}점" } ?: "미측정",
        "+${formatExp(paidMilli)}",
    )
}

private fun interruptedRowOf(t: InterruptedTripEntity) = TripRow(
    formatDate(t.startedAt), t.distanceKm?.let { "%.1f km".format(Locale.KOREA, it) } ?: "", "중단됨", null, interrupted = true,
)

/**
 * TRIP 기록과 중단된 트립 → 시간 최신순 줄. EXP는 원장 규칙 그대로([tripPaidMilli]): UNVERIFIED·겹친 구간·1km 미만 보너스는 0.
 * 중단된 트립은 EXP 없이 "중단됨"(거리는 알 때만). 정렬 기준은 완료 트립은 종료 시각, 중단 트립은 시작 시각.
 */
fun tripRows(records: List<RecordEntity>, interrupted: List<InterruptedTripEntity> = emptyList()): List<TripRow> {
    val trips = records.filter { it.kind == "TRIP" }.sortedWith(compareBy({ it.occurredAt }, { it.recordId }))
    val paid = tripPaidMilli(trips.map { it.toLedger() as TripLedger })
    val done = trips.mapIndexed { i, r -> r.occurredAt to tripRowOf(r, paid[i]) }
    return (done + interrupted.map { it.startedAt to interruptedRowOf(it) }).sortedByDescending { it.first }.map { it.second }
}

/** 22 주행 기록. 시간 최신순, 중단된 트립은 "중단됨". DEMO_TOOLS 빌드에는 리플레이 버튼이 있다. */
@Composable
fun TripsScreen(container: AppContainer) {
    val profile by container.coordinator.activeProfile.collectAsStateWithLifecycle()
    val pid = profile?.profileId ?: return
    val records by remember(pid) { container.store.records(pid) }.collectAsStateWithLifecycle(emptyList())
    val interrupted by remember(pid) { container.store.interruptedTrips(pid) }.collectAsStateWithLifecycle(emptyList())
    val rows = remember(records, interrupted) { tripRows(records, interrupted) }
    var chooser by remember { mutableStateOf(false) }
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) chooser = false }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("주행 기록", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.weight(1f))
            if (BuildConfig.DEMO_TOOLS) FilledButton("주행 리플레이", { chooser = true }, Modifier.width(300.dp))
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("아직 주행 기록이 없어요", fontSize = 32.sp, color = DpColors.TextTertiary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows) { Line(it) }
            }
        }
    }

    if (chooser && !driving) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text("리플레이할 시나리오", fontSize = 30.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCENARIOS.forEach { name ->
                        TextButton({ chooser = false; container.startScenario(name) }, Modifier.fillMaxWidth()) {
                            Text(name, fontSize = 28.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ chooser = false }) { Text("닫기", fontSize = 26.sp) } },
        )
    }
}

@Composable
private fun Line(row: TripRow) {
    val narrow = isNarrowWindow()
    val date: @Composable (Modifier) -> Unit = { m -> Text(row.date, m, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
    val values: @Composable RowScope.() -> Unit = {
        Text(row.distance, Modifier.weight(1.5f), fontSize = 30.sp)
        val tone = if (row.interrupted) DpColors.Danger else DpColors.Text
        Text(row.safety, Modifier.weight(1.5f), fontSize = 30.sp, color = tone, fontWeight = if (row.interrupted) FontWeight.Bold else FontWeight.Normal)
        Text(row.exp ?: "", Modifier.weight(1.5f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
    val box = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)).padding(horizontal = 32.dp, vertical = 24.dp)
    // 좁은 창: 날짜 한 줄, 그 아래 거리·점수·EXP
    if (narrow) {
        Column(box, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            date(Modifier)
            Row(verticalAlignment = Alignment.CenterVertically, content = values)
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically) {
            date(Modifier.weight(2f))
            values()
        }
    }
}
