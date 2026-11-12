package com.drivepet.server.weather

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Grid(val nx: Int, val ny: Int) {
    val key get() = "$nx,$ny"
}

/** 기상청 동네예보 격자 변환 (기상청 공개 LCC 변환식, 5km 격자) */
object KmaGrid {
    private const val RE = 6371.00877
    private const val GRID = 5.0
    private const val SLAT1 = 30.0
    private const val SLAT2 = 60.0
    private const val OLON = 126.0
    private const val OLAT = 38.0
    private const val XO = 43.0
    private const val YO = 136.0

    fun toGrid(lat: Double, lng: Double): Grid {
        val deg = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * deg
        val slat2 = SLAT2 * deg
        val olon = OLON * deg
        val olat = OLAT * deg
        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)
        var ra = tan(PI * 0.25 + lat * deg * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lng * deg - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn
        val x = floor(ra * sin(theta) + XO + 0.5).toInt()
        val y = floor(ro - ra * cos(theta) + YO + 0.5).toInt()
        return Grid(x, y)
    }

    /** 격자 중심의 위경도 (역변환). 같은 격자 요청이 같은 측정소·시·도를 쓰게 하는 대표점 */
    fun center(grid: Grid): Pair<Double, Double> {
        val deg = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * deg
        val slat2 = SLAT2 * deg
        val olon = OLON * deg
        val olat = OLAT * deg
        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)
        val xn = grid.nx - XO
        val yn = ro - (grid.ny - YO)
        var ra = sqrt(xn * xn + yn * yn)
        if (sn < 0) ra = -ra
        var alat = (re * sf / ra).pow(1.0 / sn)
        alat = 2.0 * atan(alat) - PI * 0.5
        val theta = if (kotlin.math.abs(xn) <= 0.0) 0.0 else kotlin.math.atan2(xn, yn)
        val alon = theta / sn + olon
        return alat / deg to alon / deg
    }
}

/**
 * 에어코리아 근접 측정소 조회용 TM 좌표.
 * 중부원점 GRS80(EPSG:5181과 같은 정의)으로 가정했다. 실연동 전에 에어코리아 활용가이드의 기준점으로 확인하고, 다르면 이 정의만 바꾼다.
 */
object AirKoreaTm {
    private val transform = run {
        val crs = CRSFactory()
        val wgs84 = crs.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs")
        val tm = crs.createFromParameters("TM", "+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=500000 +ellps=GRS80 +units=m +no_defs")
        CoordinateTransformFactory().createTransform(wgs84, tm)
    }

    /** 입력: 위도·경도(도). 출력: (tmX 동서, tmY 남북) 미터 */
    fun toTm(lat: Double, lng: Double): Pair<Double, Double> {
        val out = ProjCoordinate()
        synchronized(transform) { transform.transform(ProjCoordinate(lng, lat), out) }
        return out.x to out.y
    }
}

/**
 * 시·도. 판정은 기상청 격자 자료(읍면동별 격자·위경도·시도)로 한다: 같은 격자의 읍면동 중 가장 많은 시·도, 격자에 읍면동이 없으면 가장 가까운 읍면동의 시·도.
 * 대표점(lat, lng)은 자료를 못 읽었을 때의 마지막 대체용이다. aliases는 기상특보 현황 문구에서 그 시·도를 찾을 때 쓰는 이름들이다.
 */
enum class Sido(val label: String, val lat: Double, val lng: Double, val aliases: List<String>) {
    SEOUL("서울특별시", 37.5665, 126.9780, listOf("서울")),
    BUSAN("부산광역시", 35.1796, 129.0756, listOf("부산")),
    DAEGU("대구광역시", 35.8714, 128.6014, listOf("대구")),
    INCHEON("인천광역시", 37.4563, 126.7052, listOf("인천")),
    DAEJEON("대전광역시", 36.3504, 127.3845, listOf("대전")),
    ULSAN("울산광역시", 35.5384, 129.3114, listOf("울산")),
    SEJONG("세종특별자치시", 36.4800, 127.2890, listOf("세종")),
    GYEONGGI("경기도", 37.4138, 127.5183, listOf("경기")),
    GANGWON("강원특별자치도", 37.8228, 128.1555, listOf("강원")),
    CHUNGBUK("충청북도", 36.6357, 127.4917, listOf("충청북도", "충북")),
    CHUNGNAM("충청남도", 36.5184, 126.8000, listOf("충청남도", "충남")),
    JEONBUK("전북특별자치도", 35.7175, 127.1530, listOf("전북", "전라북도")),
    // 2026년 전라남도·광주광역시 통합 (기상청 격자 자료 2610판 기준 이름). 특보 문구는 옛 이름으로 올 수도 있어 모두 찾는다
    JEONNAM_GWANGJU("전남광주통합특별시", 34.8679, 126.9910, listOf("전남광주", "광주", "전라남도", "전남")),
    GYEONGBUK("경상북도", 36.4919, 128.8889, listOf("경상북도", "경북")),
    GYEONGNAM("경상남도", 35.4606, 128.2132, listOf("경상남도", "경남")),
    JEJU("제주특별자치도", 33.4890, 126.4983, listOf("제주")),
    ;

    companion object {
        private data class Dong(val lat: Double, val lng: Double, val sido: Sido)

        private val dongs: List<Dong> by lazy {
            val stream = Sido::class.java.getResourceAsStream("/weather/dong-grid.csv") ?: return@lazy emptyList()
            stream.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                    val c = line.split(",")
                    val sido = entries.firstOrNull { it.label == c[4] } ?: return@mapNotNull null
                    Triple(Grid(c[0].toInt(), c[1].toInt()), Dong(c[2].toDouble(), c[3].toDouble(), sido), sido)
                }.toList()
            }.also { all -> byGrid = all.groupBy({ it.first }, { it.third }).mapValues { (_, v) -> v.groupingBy { it }.eachCount().maxBy { it.value }.key } }
                .map { it.second }
        }

        @Volatile private var byGrid: Map<Grid, Sido> = emptyMap()

        /** 격자(와 그 대표점)가 속한 시·도 */
        fun of(grid: Grid, lat: Double, lng: Double): Sido {
            val points = dongs
            byGrid[grid]?.let { return it }
            if (points.isEmpty()) return nearest(lat, lng)
            return points.minBy { d ->
                val dLat = d.lat - lat
                val dLng = (d.lng - lng) * cos(lat * PI / 180.0)
                dLat * dLat + dLng * dLng
            }.sido
        }

        fun nearest(lat: Double, lng: Double): Sido = entries.minBy {
            val dLat = it.lat - lat
            val dLng = (it.lng - lng) * cos(lat * PI / 180.0)
            dLat * dLat + dLng * dLng
        }
    }
}
