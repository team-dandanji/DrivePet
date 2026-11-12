package com.drivepet.app.ui.garage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.AppContainer
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.theme.DpColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 차 정비소 탭 맨 아래의 카카오 계정 카드(연결·해제·탈퇴).
 * 탈퇴하면 서버 데이터와 앱 안의 기록이 지워지고, 프로필이 없어져 앱이 처음 화면(03)으로 돌아간다.
 */
@Composable
fun AccountCard(container: AppContainer, onLink: () -> Unit) {
    val auth by container.auth.state.collectAsStateWithLifecycle(null)
    val linked = auth?.accountId != null
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) confirmDelete = false }

    // 화면이 처음 화면으로 바뀌어도 끝까지 하도록 앱 scope에서 돈다. 실패하면 연결 상태를 그대로 둔다.
    fun run(failMessage: String, action: suspend () -> Unit) {
        busy = true; error = null
        container.scope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = failMessage }
            busy = false
        }
    }

    OnboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("카카오 계정", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (linked) "${auth?.nickname ?: "카카오 사용자"} 님과 연결돼 있어요. 펫과 주행 기록을 서버에 백업해요."
                else "연결되지 않았어요. 기록은 이 차에만 저장돼요.",
                fontSize = 26.sp, lineHeight = 38.sp, color = DpColors.TextTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (linked) {
                    OutlinedButton(
                        { run("연결을 해제하지 못했어요. 인터넷 연결을 확인해 주세요") { container.account.unlink() } },
                        Modifier.height(96.dp), enabled = !busy, shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, DpColors.TrackInactive),
                    ) { Text("연결 해제", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DpColors.Text) }
                    TextButton({ confirmDelete = true }, Modifier.height(96.dp), enabled = !busy) {
                        Text("탈퇴", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DpColors.Danger)
                    }
                } else {
                    FilledButton("카카오 연결하기", onLink)
                }
            }
            error?.let { Text(it, fontSize = 24.sp, color = DpColors.Danger) }
        }
    }

    if (confirmDelete && !driving) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("탈퇴할까요?", fontSize = 32.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "서버에 저장된 카카오 계정 정보와 모든 펫·주행 기록을 바로 삭제하고, 이 차 안의 기록도 지워요. 되돌릴 수 없어요.",
                    fontSize = 26.sp, lineHeight = 38.sp,
                )
            },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    run("탈퇴하지 못했어요. 인터넷 연결을 확인해 주세요") { container.account.deleteAccount() }
                }) { Text("탈퇴", fontSize = 26.sp, color = DpColors.Danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("취소", fontSize = 26.sp) } },
        )
    }
}
