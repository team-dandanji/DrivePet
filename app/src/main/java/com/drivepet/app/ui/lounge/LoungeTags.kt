package com.drivepet.app.ui.lounge

import com.drivepet.app.ui.components.TagStyle

/** 라운지 펫 컨디션 태그(419:50656). 값이 없으면 태그도 없다. */
fun batteryTag(pct: Int?): Pair<String, TagStyle>? = when {
    pct == null -> null
    pct >= 60 -> "넉넉해요" to TagStyle.Positive
    pct >= 20 -> "보통" to TagStyle.Caution
    else -> "부족해요" to TagStyle.Negative
}

fun healthTag(score: Int?): Pair<String, TagStyle>? = when {
    score == null -> null
    score >= 90 -> "건강해요" to TagStyle.Positive
    score >= 60 -> "주의" to TagStyle.Caution
    else -> "아파요" to TagStyle.Negative
}

data class WarningCard(val title: String, val detail: String)

fun warningCard(name: String): WarningCard =
    if ("TPMS" in name) WarningCard("타이어 공기압 확인 필요", "TPMS 경고등이 켜져 있어요")
    else WarningCard("$name 확인 필요", "차량 경고등이 켜져 있어요")

/** 급한 순(TPMS 먼저, 나머지는 들어온 순)으로 최대 2개. */
fun visibleWarnings(names: List<String>): List<String> =
    (names.filter { "TPMS" in it } + names.filter { "TPMS" !in it }).take(2)
