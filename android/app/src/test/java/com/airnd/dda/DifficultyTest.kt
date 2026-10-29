package com.airnd.dda

import com.airnd.dda.dda.Decision
import com.airnd.dda.dda.Difficulty
import com.airnd.dda.dda.RulesProvider
import com.airnd.dda.metrics.LevelResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    private fun result(won: Boolean, left: Float = 0f, progress: Float = 1f, quit: Boolean = false) =
        LevelResult(1, 1, 0.3f, won, left, progress, 2f, 0, 0, quit)

    @Test
    fun knobsCoverTheirRangesAndStayInside() {
        assertEquals(Difficulty.MIN_COLORS, Difficulty.knobs(0f).colors)
        assertEquals(Difficulty.MAX_COLORS, Difficulty.knobs(1f).colors)
        assertEquals(Difficulty.MAX_MOVES, Difficulty.knobs(0f).moves)
        assertEquals(Difficulty.MIN_MOVES, Difficulty.knobs(1f).moves)
        assertEquals(Difficulty.knobs(1f), Difficulty.knobs(5f))
        assertEquals(Difficulty.knobs(0f), Difficulty.knobs(-1f))
    }

    @Test
    fun oneDecisionMovesAtMostOneStepAndStaysClamped() {
        assertEquals(0.4f, Difficulty.apply(0.3f, Decision.HARDER), 1e-6f)
        assertEquals(0.2f, Difficulty.apply(0.3f, Decision.EASIER), 1e-6f)
        assertEquals(0f, Difficulty.apply(0.05f, Decision.EASIER), 1e-6f)
        assertEquals(1f, Difficulty.apply(0.95f, Decision.HARDER), 1e-6f)
    }

    @Test
    fun rules() {
        val rules = RulesProvider()
        assertEquals(Decision.SAME, rules.rule(emptyList()).first)
        assertEquals(Decision.EASIER, rules.rule(listOf(result(false, quit = true, progress = 0.9f))).first)
        assertEquals(Decision.EASIER, rules.rule(listOf(result(false, progress = 0.8f), result(false, progress = 0.8f))).first)
        assertEquals(Decision.EASIER, rules.rule(listOf(result(false, progress = 0.3f))).first)
        assertEquals(Decision.HARDER, rules.rule(listOf(result(true, left = 0.6f))).first)
        assertEquals(Decision.HARDER, rules.rule(listOf(result(true, left = 0.3f), result(true, left = 0.3f))).first)
        assertEquals(Decision.SAME, rules.rule(listOf(result(false, progress = 0.8f))).first)
        assertEquals(Decision.SAME, rules.rule(listOf(result(true, left = 0.1f))).first)
    }

    @Test
    fun levelResultJsonHasEveryField() {
        val json = result(true, 0.5f).toJson()
        listOf("level", "attempt", "difficulty", "won", "moves_left_fraction", "goal_progress",
            "avg_seconds_per_move", "invalid_swaps", "hints_used", "quit").forEach {
            assertTrue("missing $it", json.has(it))
        }
    }
}
