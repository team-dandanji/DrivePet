package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.theme.DpColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Hi-fi 메이트 선택(onboarding_알_노란색·초록색)의 이름과 소개. 세 번째는 슬라임. */
private data class Starter(val type: String, val egg: String, val about: String)

private val STARTERS = listOf(
    Starter("STARTER_1", "노란색 알", "짧고 밝게 응원하는 병아리로 자라나요"),
    Starter("STARTER_2", "초록색 알", "힘차게 응원하는 공룡으로 자라나요"),
    Starter("STARTER_3", "하늘색 알", "말랑하게 응원하는 슬라임으로 자라나요"), // Hi-fi의 흰색 알(눈사람) 대신 슬라임(사용자 11/11)
)

/** 06 · 스타터 알 고르기 + 이름 짓기. 완료하면(프로필 저장·활성화 뒤) onDone으로 화면을 옮긴다. */
@Composable
fun MateScreen(vm: OnboardingViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("함께 달릴 메이트를 골라 주세요", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Text("알 3종 중 1종을 고르고 이름을 지어 주세요", fontSize = 26.sp, color = DpColors.TextTertiary)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for ((type, label, about) in STARTERS) {
                val selected = vm.starter == type
                OnboardCard(
                    Modifier.weight(1f).fillMaxSize()
                        .then(if (selected) Modifier.border(BorderStroke(4.dp, DpColors.Filled), RoundedCornerShape(20.dp)) else Modifier)
                        .selectable(selected, onClick = { vm.starter = type }, role = Role.RadioButton),
                ) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.SpaceBetween, Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                            if (selected) {
                                Text(
                                    "선택 중", Modifier.background(DpColors.Filled, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 4.dp),
                                    fontSize = 22.sp, color = Color.White,
                                )
                            }
                        }
                        Character(type, 1, Mood.NORMAL)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                            Text(about, fontSize = 22.sp, color = DpColors.TextTertiary)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            Text("메이트 이름", Modifier.padding(top = 20.dp).width(200.dp), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                vm.nickname, { vm.nickname = it }, Modifier.weight(1f), singleLine = true,
                textStyle = TextStyle(fontSize = 30.sp, color = DpColors.Text),
                placeholder = { Text("이름 입력하기 (2~8자)", fontSize = 28.sp, color = DpColors.TextTertiary) },
                isError = vm.nickname.isNotEmpty() && vm.nicknameError() != null,
                supportingText = {
                    Text(if (vm.nickname.isNotEmpty()) vm.nicknameError() ?: "" else "", fontSize = 22.sp)
                },
            )
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            OutlinedButton(
                onBack, Modifier.width(300.dp).height(100.dp), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, DpColors.TrackInactive),
            ) { Text("이전", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DpColors.Text) }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saveFailed) Text("저장하지 못했어요. 다시 눌러 주세요", fontSize = 24.sp, color = MaterialTheme.colorScheme.error)
                PrimaryButton("완료", vm.canFinish && !saving, {
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            vm.finish()
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                }, Modifier.width(420.dp))
            }
        }
    }
}
