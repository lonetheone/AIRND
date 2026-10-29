package com.airnd.dda.dda

import com.airnd.dda.metrics.LevelResult

/** One provider's answer, with where it came from and how long it took. */
data class DecisionResult(
    val decision: Decision,
    val source: String,
    val latencyMs: Long,
    /** Why a fallback was used, or null. */
    val fallbackReason: String? = null,
    val reason: String = "",
)

/**
 * Chooses the next level's difficulty change from recent results (newest last).
 * Called between levels, never during play. Implementations: [RulesProvider]
 * now, an on-device model in M2/M3 with rules as its fallback.
 */
interface DifficultyProvider {
    val name: String
    suspend fun decide(history: List<LevelResult>): DecisionResult
}

/** Threshold baseline. The bar any AI provider has to justify itself against. */
class RulesProvider : DifficultyProvider {
    override val name = "rules"

    override suspend fun decide(history: List<LevelResult>): DecisionResult {
        val start = System.nanoTime()
        val (decision, reason) = rule(history)
        return DecisionResult(decision, name, (System.nanoTime() - start) / 1_000_000, reason = reason)
    }

    internal fun rule(history: List<LevelResult>): Pair<Decision, String> {
        val last = history.lastOrNull() ?: return Decision.SAME to "no history"
        val recent = history.takeLast(3)
        val failsInRow = history.reversed().takeWhile { !it.won }.size
        return when {
            last.quit -> Decision.EASIER to "player quit"
            failsInRow >= 2 -> Decision.EASIER to "$failsInRow losses in a row"
            !last.won && last.goalProgress < 0.5f -> Decision.EASIER to "lost far from goal"
            last.won && last.movesLeftFraction >= 0.5f -> Decision.HARDER to "won with half the moves left"
            recent.size >= 2 && recent.takeLast(2).all { it.won } &&
                recent.takeLast(2).all { it.movesLeftFraction >= 0.25f } ->
                Decision.HARDER to "two comfortable wins"
            else -> Decision.SAME to "in the target band"
        }
    }
}
