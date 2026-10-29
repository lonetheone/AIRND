package com.airnd.dda.dda

import com.airnd.dda.metrics.LevelResult
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Estimated player skill on the same 0..1 scale as difficulty, and the flow
 * channel around it.
 *
 * Elo-style: before each attempt the estimate predicts a performance at that
 * attempt's difficulty (0 = hopeless, 0.5 = just made it, 1 = easy win); the
 * estimate moves by [K] times the surprise. It moves only when the result is
 * better or worse than expected *at that difficulty*, so it does not simply
 * follow the difficulty line: when difficulty is wrong for the player, the
 * estimate drifts away from it and the chart shows the gap.
 */
object Skill {
    /** Half-width of the flow channel: difficulty within skill ± this is "in flow". */
    const val FLOW_HALF_WIDTH = 0.1f
    private const val K = 0.15f
    /** How sharply performance changes with (skill - difficulty). */
    private const val SLOPE = 12f

    fun performance(r: LevelResult): Float =
        if (r.won) 0.5f + 0.5f * r.movesLeftFraction else 0.5f * r.goalProgress

    fun expected(skill: Float, difficulty: Float): Float = 1f / (1f + exp(-(skill - difficulty) * SLOPE))

    /** Skill estimate after each attempt in [history] (same length, oldest first). */
    fun estimates(history: List<LevelResult>, start: Float = Difficulty.START): List<Float> {
        var s = start
        return history.map { r ->
            s = (s + K * (performance(r) - expected(s, r.difficulty))).coerceIn(0f, 1f)
            s
        }
    }

    fun inFlow(difficulty: Float, skill: Float) = abs(difficulty - skill) <= FLOW_HALF_WIDTH
}

/**
 * A synthetic player of fixed true skill, for watching a difficulty provider
 * steer toward the flow channel without playing by hand. Debug use only.
 */
class PlayerSimulator(private val trueSkill: Float, private val random: Random) {
    fun play(level: Int, attempt: Int, difficulty: Float): LevelResult {
        val margin = trueSkill - difficulty + (random.nextFloat() - 0.5f) * 0.16f
        val won = random.nextFloat() < 1f / (1f + exp(-margin * 12f))
        val quit = !won && margin < -0.25f && random.nextFloat() < 0.3f
        return LevelResult(
            level = level, attempt = attempt, difficulty = difficulty, won = won,
            movesLeftFraction = if (won) (0.1f + margin * 1.5f).coerceIn(0f, 0.8f) else 0f,
            goalProgress = if (won) 1f else (0.75f + margin * 1.5f).coerceIn(0.05f, 0.99f),
            avgSecondsPerMove = 3f, invalidSwaps = 0, hintsUsed = 0, quit = quit,
            trueSkill = trueSkill,
        )
    }
}
