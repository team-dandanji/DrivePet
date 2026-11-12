package com.drivepet.app.drive

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.drivepet.app.data.remote.PlaceDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DrivePetNavi"
private const val ANSWER_MS = 3_000L

/**
 * 내장 내비(NaviHelper 3.0.5) 연결. 만들 때 한 번 연결을 맺는다. 실패(내비 서비스 없음 등)는 null/false로 돌려준다.
 * 응답은 리스너로 오므로 한 번에 질문 하나만 보낸다(같은 콜백에 다른 질문의 답이 섞이지 않게).
 */
class Navi(context: Context) {
    private val helper = NaviHelper(context.applicationContext)
    private val ready = CompletableDeferred<Boolean>()
    private val asking = Mutex()

    init {
        runCatching { helper.initialize({ ready.complete(true) }, { Log.w(TAG, "NaviHelper 연결 실패", it); ready.complete(false) }) }
            .onFailure { Log.w(TAG, "NaviHelper 초기화 실패", it); ready.complete(false) }
    }

    /** 내비가 아는 현재 위치(위도, 경도). 3초 안에 답이 없으면 null. */
    suspend fun currentLocation(): Pair<Double, Double>? = ask({ helper.getCurrentLocationInfo() }) { r ->
        object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) { r.complete(info.latitude to info.longitude) }
        }
    }

    /** 현재 위치에서 [place]까지 예상 소요(초). 3초 안에 답이 없으면 null. */
    suspend fun eta(place: PlaceDto): Int? = ask({ helper.requestTrip(TripRequest(place.lat, place.lng, "", place.name, "")) }) { r ->
        object : NaviHelperEventListener {
            override fun onTripInfo(info: TripInfo) { r.complete(info.eta) }
        }
    }

    /** [place]를 목적지로 안내 시작. 안 되면 장소 이름으로 내비 검색 화면, 그것도 안 되면 false. */
    fun startGuidance(place: PlaceDto): Boolean {
        if (!connected()) return false
        val route = RouteInfo(
            place.lat, place.lng, null, null, poiName = place.name, poiId = "", address = place.address, poiSubId = "",
            routeOption = RouteOption.RECOMMENDED, poiProvider = "", isMobileRequest = false, // 둘 다 null 불가, 라이브러리 기본값
        )
        return send("requestRoute") { helper.requestRoute(route) } ||
            send("SearchPlace") { helper.requestNavigationScreen(NavigationScreenRequest.SearchPlace(place.name)) }
    }

    /** 내비 화면을 앞으로. 연결이 아직이면 3초까지 기다린다. 안 되면 로그만 남기고 false. */
    suspend fun bringToFront(): Boolean {
        if (withTimeoutOrNull(ANSWER_MS) { ready.await() } != true) { Log.w(TAG, "Foreground: 내비 연결이 아직 안 됐다"); return false }
        return send("Foreground") { helper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
    }

    fun release() { runCatching { helper.release() } }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun connected() = ready.isCompleted && ready.getCompleted()

    private fun send(what: String, call: () -> Unit) = runCatching(call).onFailure { Log.w(TAG, "$what 실패", it) }.isSuccess

    private suspend fun <T> ask(request: () -> Unit, listener: (CompletableDeferred<T>) -> NaviHelperEventListener): T? =
        asking.withLock {
            withTimeoutOrNull(ANSWER_MS) {
                if (!ready.await()) return@withTimeoutOrNull null
                val answer = CompletableDeferred<T>()
                val l = listener(answer)
                helper.addListener(l)
                try {
                    if (!send("ask", request)) return@withTimeoutOrNull null
                    answer.await()
                } finally {
                    helper.removeListener(l)
                }
            }
        }
}
