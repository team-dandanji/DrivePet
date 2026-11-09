package com.drivepet.vehicle.source

import android.content.Context
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 시나리오 파일을 시간순으로 재생하는 차.
 * 에뮬레이터에서 차속을 바꿀 수 없는 문제를 피해, 발표 때 '출발 → 주행 → 도착 → 정산'을 보여 준다.
 */
class ReplayVehicleSource(
    private val scenario: Scenario,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFinished: () -> Unit = {},
) : VehicleSource {

    override val name = "시나리오 재생: ${scenario.name}"
    private var job: Job? = null

    override fun start(sink: VehicleSink) {
        sink.update { Scenario.apply(VehicleState(), scenario.start, clock()) }
        job = scope.launch {
            var elapsed = 0.0
            for (step in scenario.steps.sortedBy { it.t }) {
                val waitMs = ((step.t - elapsed) * 1000 / scenario.speed).toLong()
                if (waitMs > 0) delay(waitMs)
                elapsed = step.t
                sink.update { Scenario.apply(it, step.set, clock()) }
            }
            onFinished()
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        /** assets 안의 JSON 파일을 읽는다. 예: "scenarios/commute_12km.json" */
        fun fromAsset(context: Context, path: String): Scenario =
            parse(context.assets.open(path).bufferedReader().use { it.readText() })

        fun parse(json: String): Scenario {
            val root = JSONObject(json)
            val stepsJson = root.optJSONArray("steps") ?: JSONArray()
            val steps = (0 until stepsJson.length()).map { i ->
                val o = stepsJson.getJSONObject(i)
                Scenario.Step(o.getDouble("t"), o.getJSONObject("set").toMap())
            }
            return Scenario(
                name = root.optString("name", "시나리오"),
                start = root.optJSONObject("start")?.toMap() ?: emptyMap(),
                steps = steps,
                speed = root.optDouble("speed", 1.0).takeIf { it > 0 } ?: 1.0,
            )
        }

        private fun JSONObject.toMap(): Map<String, Any?> =
            keys().asSequence().associateWith { key -> unwrap(get(key)) }

        private fun unwrap(v: Any?): Any? = when (v) {
            JSONObject.NULL -> null
            is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
            is JSONObject -> v.toMap()
            else -> v
        }
    }
}
