package com.drivepet.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 02: 앱을 주행 중에 처음 열었을 때 12 위에 뜨는 안내. 버튼 없음. */
@Composable
fun RestrictedEntryDialog() {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(1000.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(48.dp),
            Arrangement.spacedBy(20.dp), Alignment.CenterHorizontally,
        ) {
            Text("안전하게 주차한 뒤 시작해주세요", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "계정 연결과 초기 설정은 주차 중에만 이용할 수 있어요\n주행 중에는 화면을 조작하지 마세요",
                fontSize = 24.sp, lineHeight = 34.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
