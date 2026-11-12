package com.drivepet.server.weather

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")
private val HM = DateTimeFormatter.ofPattern("HHmm")

/** 공공데이터포털 공통 호출. serviceKey는 일반 인증키(Decoding) 원문을 넣으면 여기서 인코딩한다 */
class DataGoKrHttp(baseUrl: String, private val serviceKey: String, private val props: WeatherProperties, private val json: JsonMapper) {
    private val client = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(props.connectTimeout)
            setReadTimeout(props.readTimeout)
        })
        .uriBuilderFactory(DefaultUriBuilderFactory(baseUrl).apply { encodingMode = DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY })
        .build()

    /** 응답 JSON의 response.body (resultCode 03 NO_DATA면 null). 실패는 ExternalWeatherException — 메시지에 URL·키를 넣지 않는다 */
    fun body(service: String, path: String, params: Map<String, Any>): JsonNode? {
        val all = params + ("serviceKey" to serviceKey)
        val template = path + "?" + all.keys.joinToString("&") { "$it={$it}" }
        fun fetch(attempt: Int): String = try {
            // 응답에 charset이 없을 수 있어 UTF-8로 직접 읽는다
            client.get().uri(template, all).retrieve().body(ByteArray::class.java)?.toString(Charsets.UTF_8) ?: ""
        } catch (e: Exception) {
            // 본문 수신 중 시간 초과는 RestClientException 안의 IOException으로 온다
            val transient = e is ResourceAccessException || e is HttpServerErrorException ||
                generateSequence(e.cause) { it.cause }.any { it is java.io.IOException }
            if (!transient || attempt >= props.retries) throw ExternalWeatherException("$service 호출 실패(${e.javaClass.simpleName})")
            fetch(attempt + 1)
        }
        val raw = fetch(0)
        val root = try {
            json.readTree(raw)
        } catch (e: Exception) {
            // 키 오류 등은 XML로 오는 경우가 있다
            throw ExternalWeatherException("$service 응답 형식 오류")
        }
        val response = root.path("response")
        val code = response.path("header").path("resultCode").asString("")
        return when (code) {
            "00" -> response.path("body")
            "03" -> null
            else -> throw ExternalWeatherException("$service 결과 코드 $code")
        }
    }
}

fun JsonNode.items(): List<JsonNode> {
    val items = path("items")
    val arr = if (items.isArray) items else items.path("item")
    return if (arr.isArray) arr.toList() else emptyList()
}

private fun JsonNode.intOrNull(field: String): Int? = path(field).asString("").trim().toIntOrNull()

class HttpKmaClient(private val http: DataGoKrHttp) : KmaClient {
    override fun now(grid: Grid, at: OffsetDateTime): KmaNow {
        // 초단기실황은 매시 정각 자료가 약 40분 뒤에 나온다
        val base = at.atZoneSameInstant(SEOUL).minusMinutes(45).truncatedTo(ChronoUnit.HOURS)
        val body = http.body(
            "기상청 초단기실황", "/VilageFcstInfoService_2.0/getUltraSrtNcst",
            mapOf("pageNo" to 1, "numOfRows" to 20, "dataType" to "JSON", "base_date" to base.format(YMD), "base_time" to base.format(HM), "nx" to grid.nx, "ny" to grid.ny),
        ) ?: throw ExternalWeatherException("기상청 초단기실황 자료 없음")
        val values = body.items().associate { it.path("category").asString("") to it.path("obsrValue").asString("") }
        val temp = values["T1H"]?.toDoubleOrNull() ?: throw ExternalWeatherException("기상청 초단기실황 기온 없음")
        val pty = values["PTY"]?.toIntOrNull() ?: 0
        return KmaNow(temp, pty, base.toOffsetDateTime())
    }

    override fun forecast(grid: Grid, at: OffsetDateTime): KmaForecast {
        // 단기예보 발표: 02·05·08·11·14·17·20·23시, 약 10분 뒤 제공
        val t = at.atZoneSameInstant(SEOUL).minusMinutes(15)
        val hour = listOf(23, 20, 17, 14, 11, 8, 5, 2).firstOrNull { it <= t.hour }
        val base = if (hour == null) t.minusDays(1).withHour(23) else t.withHour(hour)
        val body = http.body(
            "기상청 단기예보", "/VilageFcstInfoService_2.0/getVilageFcst",
            mapOf("pageNo" to 1, "numOfRows" to 200, "dataType" to "JSON", "base_date" to base.format(YMD), "base_time" to base.withMinute(0).format(HM), "nx" to grid.nx, "ny" to grid.ny),
        ) ?: throw ExternalWeatherException("기상청 단기예보 자료 없음")
        val target = at.atZoneSameInstant(SEOUL).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()
        fun pick(category: String): String? = body.items()
            .filter { it.path("category").asString("") == category }
            .mapNotNull { item ->
                val dt = runCatching {
                    LocalDateTime.of(LocalDate.parse(item.path("fcstDate").asString(""), YMD), java.time.LocalTime.parse(item.path("fcstTime").asString(""), HM))
                }.getOrNull() ?: return@mapNotNull null
                dt to item.path("fcstValue").asString("")
            }
            .filter { !it.first.isBefore(target) }
            .minByOrNull { it.first }?.second
        val sky = pick("SKY")?.toIntOrNull() ?: throw ExternalWeatherException("기상청 단기예보 하늘상태 없음")
        return KmaForecast(sky, pick("POP")?.toIntOrNull())
    }

    override fun activeWarnings(at: OffsetDateTime): List<WarningItem> {
        val body = http.body(
            "기상청 특보현황", "/WthrWrnInfoService/getPwnStatus",
            mapOf("pageNo" to 1, "numOfRows" to 10, "dataType" to "JSON"),
        ) ?: return emptyList() // NO_DATA(03) = 발효 중인 특보 없음
        // 정상 코드인데 자료·문구가 없거나 해석이 안 되면 '특보 없음'이 아니라 실패로 본다
        val text = body.items().firstOrNull()?.path("t6")?.takeIf { it.isString }?.asString()
            ?: throw ExternalWeatherException("기상청 특보현황 자료 형식 오류")
        return parseWarnings(text) ?: throw ExternalWeatherException("기상청 특보현황 문구 해석 실패")
    }

    companion object {
        /**
         * "o 강풍주의보 : 강원도(강원북부산지), 경상북도(...)" 형식의 특보 현황 문구를 나눈다.
         * "o 없음"(공백이 끼어 "o 없 음"이어도)이면 빈 목록. 해석할 줄이 하나도 없거나, "o 이름 : 지역" 줄 중 이름·지역이 빈 줄이 있으면 null(형식 오류).
         * null이면 호출한 쪽(WeatherService)이 특보만 빼고 날씨를 응답한다.
         * "o"로 시작하지 않는 줄(참고 문구 등)은 건너뛴다. 실제 응답에서 다른 형식이 나오면 규칙을 늘린다.
         */
        fun parseWarnings(text: String): List<WarningItem>? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val items = lines
                .filter { it.startsWith("o") && ':' in it }
                .map { line ->
                    val (name, regions) = line.removePrefix("o").split(":", limit = 2).map { it.trim() }
                    if (name.isEmpty() || regions.isEmpty()) return null
                    WarningItem(name, regions)
                }
            if (items.isNotEmpty()) return items
            return if (lines.any { it.removePrefix("o").filterNot(Char::isWhitespace) == "없음" }) emptyList() else null
        }
    }
}

class HttpAirKoreaClient(private val http: DataGoKrHttp) : AirKoreaClient {
    override fun nearbyStations(tmX: Double, tmY: Double): List<Station> {
        val body = http.body(
            "에어코리아 측정소정보", "/MsrstnInfoInqireSvc/getNearbyMsrstnList",
            mapOf("returnType" to "json", "tmX" to "%.3f".format(tmX), "tmY" to "%.3f".format(tmY), "ver" to "1.1"),
        ) ?: return emptyList()
        return body.items().mapNotNull { s ->
            val name = s.path("stationName").asString("").ifBlank { return@mapNotNull null }
            Station(name, s.path("tm").asString("").toDoubleOrNull())
        }
    }

    override fun measure(stationName: String): AirMeasure? {
        val body = http.body(
            "에어코리아 대기오염정보", "/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty",
            mapOf("returnType" to "json", "numOfRows" to 1, "pageNo" to 1, "stationName" to stationName, "dataTerm" to "DAILY", "ver" to "1.3"),
        ) ?: return null
        val item = body.items().firstOrNull() ?: return null
        return AirMeasure(
            pm10 = item.intOrNull("pm10Value"),
            pm25 = item.intOrNull("pm25Value"),
            pm10Grade = item.intOrNull("pm10Grade1h") ?: item.intOrNull("pm10Grade"),
            pm25Grade = item.intOrNull("pm25Grade1h") ?: item.intOrNull("pm25Grade"),
            observedAt = parseDataTime(item.path("dataTime").asString("")),
        )
    }

    companion object {
        /** "2026-11-09 08:00" (24:00은 다음날 00:00) */
        fun parseDataTime(s: String): OffsetDateTime? = runCatching {
            val (d, t) = s.trim().split(" ")
            val (h, m) = t.split(":").map { it.toInt() }
            LocalDate.parse(d).atStartOfDay(SEOUL).plusHours(h.toLong()).plusMinutes(m.toLong()).toOffsetDateTime()
        }.getOrNull()
    }
}
