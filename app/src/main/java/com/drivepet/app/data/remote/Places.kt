package com.drivepet.app.data.remote

import kotlinx.serialization.Serializable

// 서버 GET /places(카카오 로컬)의 항목. 가까운 순, 최대 3곳.
@Serializable data class PlaceDto(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val distanceM: Int)

/** 주변 정비소. 실패는 예외 그대로 둔다(화면 상태는 ViewModel이 정한다). Retrofit suspend 호출이라 메인에서 불러도 된다. */
class PlacesRepo(private val api: suspend () -> DrivePetApi) {
    suspend fun nearby(lat: Double, lng: Double): List<PlaceDto> = api().places(lat, lng).data.orEmpty()
}
