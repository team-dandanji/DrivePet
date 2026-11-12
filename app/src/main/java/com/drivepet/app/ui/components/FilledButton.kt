package com.drivepet.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 정차 화면의 주 버튼. 모양은 [DpCta]와 같다(높이 100, radius 16, 차콜). */
@Composable
fun FilledButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = DpCta(text, onClick, modifier)
