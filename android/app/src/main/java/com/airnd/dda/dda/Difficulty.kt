package com.airnd.dda.dda

import kotlin.math.roundToInt

/**
 * The single source for difficulty. One number in 0..1 drives every knob, so a
 * provider only moves that number and can never set an unsafe combination.
 */
object Difficulty {
    const val START = 0.3f
    /** Largest change one decision may make: no sudden jumps. */
    const val STEP = 0.1f

    const val MIN_COLORS = 4
    const val MAX_COLORS = 6
    const val MAX_MOVES = 30
    const val MIN_MOVES = 15
    const val MIN_GOAL = 12
    const val MAX_GOAL = 30

    fun knobs(d: Float): Knobs {
        val x = d.coerceIn(0f, 1f)
        return Knobs(
            colors = (MIN_COLORS + (MAX_COLORS - MIN_COLORS) * x).roundToInt(),
            moves = (MAX_MOVES - (MAX_MOVES - MIN_MOVES) * x).roundToInt(),
            goal = (MIN_GOAL + (MAX_GOAL - MIN_GOAL) * x).roundToInt(),
        )
    }

    fun apply(d: Float, decision: Decision): Float = when (decision) {
        Decision.EASIER -> d - STEP
        Decision.SAME -> d
        Decision.HARDER -> d + STEP
    }.coerceIn(0f, 1f)
}

data class Knobs(val colors: Int, val moves: Int, val goal: Int)

enum class Decision { EASIER, SAME, HARDER }
