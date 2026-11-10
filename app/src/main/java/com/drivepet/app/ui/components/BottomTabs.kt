package com.drivepet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.ui.theme.DpColors

enum class Tab(val label: String) { LOUNGE("라운지"), GARAGE("차 정비소"), TRIPS("주행기록") }

/** 라운지 화면 상단: 앱 이름 + 밑줄 탭. 파일 이름은 계획서 기준(실제로는 위쪽 막대). */
@Composable
fun TopTabs(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Text("Drivepet", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Box(
                    Modifier.clickable { onSelect(tab) }.height(80.dp).drawBehind {
                        if (on) drawRect(DpColors.Text, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 4.dp.toPx()))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label, fontSize = 32.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) DpColors.Text else DpColors.TextTertiary,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DpColors.Divider))
    }
}
