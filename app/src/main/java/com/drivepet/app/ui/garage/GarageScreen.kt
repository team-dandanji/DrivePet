package com.drivepet.app.ui.garage

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.AppContainer
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.HeaderMenu
import com.drivepet.app.ui.components.MenuItem
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 헤더 ⋯ 메뉴의 카카오 계정(연결·해제·탈퇴). 탈퇴하면 서버 데이터와 앱 안의 기록이 지워지고, 프로필이 없어져 앱이 처음 화면(03)으로 돌아간다.
 * 화면 루트 Box의 자식으로 둔다(탈퇴 확인 팝업이 같은 창 맨 위에 그려진다). 주행이 시작되면 스스로 닫힌다.
 */
@Composable
fun AccountMenu(container: AppContainer, onLink: () -> Unit, onDismiss: () -> Unit) {
    val auth by container.auth.state.collectAsStateWithLifecycle(null)
    val linked = auth?.accountId != null
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) { confirmDelete = false; onDismiss() } }

    // 화면이 처음 화면으로 바뀌어도 끝까지 하도록 앱 scope에서 돈다. 실패하면 연결 상태를 그대로 둔다.
    fun run(failMessage: String, action: suspend () -> Unit) {
        busy = true; error = null
        container.scope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = failMessage }
            busy = false
        }
    }

    HeaderMenu(Alignment.TopEnd, onDismiss) {
        Text(
            if (linked) "${auth?.nickname ?: "카카오 사용자"} 님과 연결돼 있어요" else "카카오와 연결되지 않았어요",
            Modifier.padding(horizontal = 32.dp, vertical = 16.dp), style = DpType.labelM, color = DpColors.TextSecondary,
        )
        if (linked) {
            MenuItem("카카오 연결 해제", { run("연결을 해제하지 못했어요. 인터넷 연결을 확인해 주세요") { container.account.unlink() } }, enabled = !busy)
            MenuItem("계정 탈퇴", { confirmDelete = true }, color = DpColors.Danger, enabled = !busy)
        } else {
            MenuItem("카카오 연결하기", onLink)
        }
        error?.let { Text(it, Modifier.padding(horizontal = 32.dp, vertical = 8.dp), style = DpType.labelM, color = DpColors.Danger) }
    }

    if (confirmDelete && !driving) {
        DpPopup(
            title = "탈퇴할까요?",
            body = "서버에 저장된 카카오 계정 정보와 모든 펫·주행 기록을 바로 삭제하고, 이 차 안의 기록도 지워요. 되돌릴 수 없어요.",
            onDismissRequest = { confirmDelete = false },
        ) {
            PopupButton("취소", { confirmDelete = false }, primary = false)
            PopupButton("탈퇴", {
                confirmDelete = false
                run("탈퇴하지 못했어요. 인터넷 연결을 확인해 주세요") { container.account.deleteAccount() }
            }, primary = true)
        }
    }
}
