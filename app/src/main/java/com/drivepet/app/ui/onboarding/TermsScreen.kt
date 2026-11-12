package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivepet.app.R
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

private val POLICIES = listOf(
    Term.SERVICE to "[필수] 서비스 이용약관 및 개인정보",
    Term.VEHICLE_DATA to "[필수] 차량 데이터 조회 및 정비 관리",
    Term.LOCATION to "[선택] 위치 정보 및 날씨, 주변장소",
)

/** 약관 동의(403:36018 / 403:40271). 필수 2개가 모두 골라지면 [다음]. 저장하지 않고, 자세히 보기(›)는 동작이 없다(스코프 밖). */
@Composable
fun TermsScreen(vm: OnboardingViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val all = vm.terms.size == Term.entries.size
    OnboardStep(
        "회원가입 및 로그인", 0, onBack, "이용 약관에 동의해주세요", "차량별로 지원되는 정보만 조회해요",
        dots = 2 to 0, showPrev = false, cta = StepCta("다음", vm.termsOk, onNext),
    ) {
        Column(Modifier.widthIn(max = 798.dp), verticalArrangement = Arrangement.spacedBy(68.dp)) {
            // 모두 동의(328:39253): 고르면 파란 채움 + 흰 글자.
            Row(
                Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (all) DpColors.ControlOn else DpColors.SurfaceLow)
                    .border(4.dp, if (all) DpColors.ControlOn else DpColors.DividerPrimary, RoundedCornerShape(20.dp))
                    .toggleable(all, role = Role.Checkbox) { vm.terms = if (all) emptySet() else Term.entries.toSet() }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    CheckBox(all, 64.dp, 16.dp, 5.dp, 60.dp, on = DpColors.ControlOnPressed, off = DpColors.ButtonBasicPressed)
                }
                Text("모든 약관 동의하기", style = DpType.titleL, color = if (all) Color.White else DpColors.TextSecondary, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
                for ((term, label) in POLICIES) {
                    val on = term in vm.terms
                    Row(
                        Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { vm.terms = if (on) vm.terms - term else vm.terms + term },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            CheckBox(on, 48.dp, 12.dp, 4.dp, 44.dp, on = DpColors.ControlOn, off = Color.Transparent)
                        }
                        Text(label, Modifier.weight(1f), style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.Text, maxLines = 1)
                        // 자세히 보기 ›(동작 없음)
                        Image(painterResource(R.drawable.ic_back), null, Modifier.size(44.dp).rotate(180f), colorFilter = ColorFilter.tint(DpColors.Text))
                    }
                }
            }
        }
    }
}

/** 체크 칸: 고르면 [on] 채움 + 흰 ✓, 아니면 [off] 바탕에 연한 테두리. */
@Composable
private fun CheckBox(checked: Boolean, side: Dp, radius: Dp, border: Dp, check: Dp, on: Color, off: Color) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(side).clip(shape).background(if (checked) on else off)
            .then(if (checked) Modifier else Modifier.border(border, DpColors.DividerTertiary, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Image(painterResource(R.drawable.ic_check), null, Modifier.size(check))
    }
}
