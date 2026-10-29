package com.airnd.dda.metrics

import org.json.JSONObject

/** How the player did on one level attempt. This is all a difficulty provider sees. */
data class LevelResult(
    val level: Int,
    val attempt: Int,
    val difficulty: Float,
    val won: Boolean,
    /** Fraction of the move limit still unused (0 when lost). */
    val movesLeftFraction: Float,
    /** Fraction of the goal collected, capped at 1. */
    val goalProgress: Float,
    val avgSecondsPerMove: Float,
    val invalidSwaps: Int,
    val hintsUsed: Int,
    /** The player restarted or left before the level ended. */
    val quit: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("level", level).put("attempt", attempt).put("difficulty", difficulty.toDouble())
        .put("won", won).put("moves_left_fraction", movesLeftFraction.toDouble())
        .put("goal_progress", goalProgress.toDouble())
        .put("avg_seconds_per_move", avgSecondsPerMove.toDouble())
        .put("invalid_swaps", invalidSwaps).put("hints_used", hintsUsed).put("quit", quit)
}
