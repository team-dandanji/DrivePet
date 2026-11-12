package com.drivepet.app.ui.care

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivepet.app.AppContainer
import com.drivepet.app.data.PendingCare
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.shortName
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.vehicle.VehicleModule
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 정비소로 안내를 시작한 뒤(pending) 정차해서 라운지로 돌아왔을 때만 묻는다. */
fun shouldAskCareRecord(pending: PendingCare?, parked: Boolean, onLounge: Boolean): Boolean = pending != null && parked && onLounge

/**
 * 정비 기록 팝업(D5 419:53613). 라운지 화면 위에 같은 창 안에서 뜬다(라운지 루트 Box의 마지막 자식으로 둔다).
 * 라운지가 RESUMED이고 정차 중일 때만 보인다. 주행이 시작되면 사라지고, pending은 그대로 남는다.
 * [네]는 교체 기록을 남기고 pending을 지운 뒤 [onRecorded]로 새 recordId를 넘긴다.
 */
@Composable
fun CareRecordPopup(container: AppContainer, onRecorded: (String) -> Unit) {
    val pending by remember { container.settings.pendingCare }.collectAsStateWithLifecycle(null)
    val state by VehicleModule.repository.state.collectAsStateWithLifecycle()
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    // 뒤로 가기·바깥 탭은 pending을 지우지 않고 이번 화면에서만 숨긴다(다시 라운지에 들어오면 다시 묻는다).
    var hidden by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val vm: GarageViewModel = viewModel(factory = GarageViewModel.factory(container))

    val p = pending ?: return
    val item = runCatching { CareItem.valueOf(p.itemId) }.getOrNull() ?: return
    if (hidden || LocalDriving.current || !shouldAskCareRecord(p, state.isParked, resumed)) return

    DpPopup(
        "오늘 ${item.shortName} 관리를 받으셨나요?",
        "${item.title} 기록을 확인해주세요\n관리 상태 기록에 따라 캐릭터가 성장해요",
        onDismissRequest = { hidden = true },
    ) {
        PopupButton("나중에 받을거에요", { scope.launch { container.settings.setPendingCare(null) } }, primary = false, enabled = !busy)
        PopupButton(
            "네, ${item.shortName} 관리를 받았어요",
            {
                busy = true
                scope.launch {
                    // 정비 항목 행은 프로필마다 따로 있어, CareItem 이름으로 찾아 그 itemId로 기록한다.
                    val rowId = vm.ui.filterNotNull().first().rows.firstOrNull { it.care == item }?.item?.itemId
                    val id = rowId?.let { vm.replace(it) }
                    container.settings.setPendingCare(null)
                    busy = false
                    id?.let(onRecorded)
                }
            },
            primary = true, enabled = !busy,
        )
    }
}
