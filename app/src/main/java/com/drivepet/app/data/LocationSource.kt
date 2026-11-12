package com.drivepet.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

const val SEOUL_CITY_HALL_LAT = 37.5665
const val SEOUL_CITY_HALL_LNG = 126.9780

// 기상청 격자는 한국 안에서만 유효하다. 밖이면 서울시청으로 대신한다(에뮬레이터 기본 위치 등).
const val KOREA_LAT_MIN = 33.0
const val KOREA_LAT_MAX = 38.7
const val KOREA_LNG_MIN = 124.5
const val KOREA_LNG_MAX = 132.0

/** 한국 안 좌표면 그대로, 밖이면 null(기상청 격자·주변 정비소 검색은 한국 안에서만 뜻이 있다). */
internal fun pickInKorea(lat: Double, lng: Double): Pair<Double, Double>? =
    if (lat in KOREA_LAT_MIN..KOREA_LAT_MAX && lng in KOREA_LNG_MIN..KOREA_LNG_MAX) lat to lng else null

internal fun pickOrSeoul(lat: Double, lng: Double): Pair<Double, Double> =
    pickInKorea(lat, lng) ?: (SEOUL_CITY_HALL_LAT to SEOUL_CITY_HALL_LNG)

/** 권한이 있으면 마지막으로 알려진 위치 중 가장 최근 것. Play 서비스는 쓰지 않는다. */
class LocationSource(private val context: Context) {
    /** 마지막으로 알려진 실제 위치. 권한·기록이 없거나 한국 밖이면 null. */
    @Suppress("MissingPermission")
    fun latLngOrNull(): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val best = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        return best?.let { pickInKorea(it.latitude, it.longitude) }
    }

    /** 날씨용: [latLngOrNull]이 없으면 서울시청. */
    fun latLng(): Pair<Double, Double> = latLngOrNull() ?: (SEOUL_CITY_HALL_LAT to SEOUL_CITY_HALL_LNG)
}
