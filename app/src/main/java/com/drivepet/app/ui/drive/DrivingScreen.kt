package com.drivepet.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.vehicle.model.TripSummary
import java.util.Locale

/** 주행 중 12: 움직임·조작·말풍선 없는 정적 화면. 거리 글자만 0.1km 단위로 바뀐다. */
@Composable
fun DrivingScreen(trip: TripSummary?, starter: String, stage: Int) {
    val distance = trip?.distanceKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "미측정"
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(20.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Character(starter, stage, Mood.NORMAL)
        Text("운전에 집중해주세요", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "주행 중에는 DrivePet 화면을 사용할 수 없어요.\n주행 기록은 계속 남기고 있어요.",
            fontSize = 28.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, color = DpColors.TextTertiary,
        )
        Text("주행 기록 중 · $distance", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}
