package com.drivepet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.ui.theme.DpColors

/** percent가 null이면 "미측정"과 빈 트랙(0%로 바꾸지 않는다). */
@Composable
fun StatGauge(label: String, percent: Int?, modifier: Modifier = Modifier) {
    val shown = percent?.coerceIn(0, 100)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 26.sp, color = DpColors.TextTertiary)
            Text(shown?.let { "$it%" } ?: "미측정", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (shown ?: 0) / 100f },
            color = DpColors.Filled,
            trackColor = DpColors.TrackInactive,
            drawStopIndicator = {},
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(12.dp),
        )
    }
}
