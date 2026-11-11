package com.drivepet.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

const val SEOUL_CITY_HALL_LAT = 37.5665
const val SEOUL_CITY_HALL_LNG = 126.9780

/** 권한이 있으면 마지막으로 알려진 위치 중 가장 최근 것, 아니면 서울시청. Play 서비스는 쓰지 않는다. */
class LocationSource(private val context: Context) {
    @Suppress("MissingPermission")
    fun latLng(): Pair<Double, Double> {
        val seoul = SEOUL_CITY_HALL_LAT to SEOUL_CITY_HALL_LNG
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return seoul
        val lm = context.getSystemService(LocationManager::class.java) ?: return seoul
        val best = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        return best?.let { it.latitude to it.longitude } ?: seoul
    }
}
