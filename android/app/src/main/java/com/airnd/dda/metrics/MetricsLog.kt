package com.airnd.dda.metrics

import com.airnd.dda.dda.DecisionResult
import java.io.File

/**
 * Appends one JSON line per level attempt to app-private storage, for later
 * comparison of providers. Pull with:
 *   adb -s <serial> exec-out run-as com.airnd.dda cat files/metrics.jsonl > metrics.jsonl
 */
class MetricsLog(dir: File) {
    val file = File(dir, "metrics.jsonl")

    fun append(result: LevelResult, decision: DecisionResult, nextDifficulty: Float, simulated: Boolean = false) {
        val line = result.toJson()
            .put("simulated", simulated)
            .put("time", System.currentTimeMillis())
            .put("decision", decision.decision.name)
            .put("provider", decision.source)
            .put("latency_ms", decision.latencyMs)
            .put("fallback", decision.fallbackReason ?: JSONNULL)
            .put("reason", decision.reason)
            .put("next_difficulty", nextDifficulty.toDouble())
        runCatching { file.appendText(line.toString() + "\n") }
    }

    private companion object {
        val JSONNULL: Any = org.json.JSONObject.NULL
    }
}
