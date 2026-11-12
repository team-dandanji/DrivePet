package com.drivepet.app.ui.lounge

/** 라운지 배치. DEFAULT = 기본 창(1682×1210dp), NARROW = 17 Connect-S 분할 세로 창, WIDE = 18 Connect-W 낮고 넓은 창. */
enum class LoungeLayout { DEFAULT, NARROW, WIDE }

/** 에뮬레이터 실측(11/11, `cmd activity task resize`): 기본 1682×1210, 반 분할 842×1210. 18의 W 기준은 와이어프레임 1640×810. */
fun loungeLayout(widthDp: Int, heightDp: Int): LoungeLayout = when {
    widthDp < 1200 -> LoungeLayout.NARROW
    heightDp < 1000 -> LoungeLayout.WIDE
    else -> LoungeLayout.DEFAULT
}
