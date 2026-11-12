package com.drivepet.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.ui.theme.DpColors

/** 정차 화면(13~16)의 주 버튼: 어두운 채움, radius 16. */
@Composable
fun FilledButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.height(100.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
    ) { Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
}
