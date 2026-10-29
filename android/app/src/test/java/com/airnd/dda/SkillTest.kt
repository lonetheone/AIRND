package com.airnd.dda

import com.airnd.dda.dda.Difficulty
import com.airnd.dda.dda.PlayerSimulator
import com.airnd.dda.dda.RulesProvider
import com.airnd.dda.dda.Skill
import com.airnd.dda.metrics.LevelResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SkillTest {

    private fun result(d: Float, won: Boolean, left: Float = 0f, progress: Float = 1f) =
        LevelResult(1, 1, d, won, left, progress, 2f, 0, 0, false)

    @Test
    fun justMakingItMeansSkillEqualsDifficulty() {
        assertEquals(0.5f, Skill.performance(result(0.4f, won = true, left = 0f)), 1e-6f)
        val s = Skill.estimates(List(10) { result(0.4f, won = true, left = 0f) }, start = 0.4f)
        assertEquals(0.4f, s.last(), 1e-4f)
    }

    @Test
    fun easyWinsRaiseSkillAndLossesLowerIt() {
        val up = Skill.estimates(listOf(result(0.3f, won = true, left = 0.8f)), start = 0.3f).last()
        val down = Skill.estimates(listOf(result(0.3f, won = false, progress = 0.2f)), start = 0.3f).last()
        assertTrue(up > 0.3f)
        assertTrue(down < 0.3f)
    }

    /**
     * At a fixed, wrong difficulty the estimate must still move the right way and
     * keep players in order. It cannot reach a far-away true skill there: one level
     * only shows "easy win" or "hopeless", not how easy (a ceiling effect).
     */
    @Test
    fun estimateMovesTheRightWayAtAFixedDifficulty() {
        val est = listOf(0.2f, 0.5f, 0.85f).map { trueSkill ->
            val sim = PlayerSimulator(trueSkill, Random(5))
            Skill.estimates(List(60) { sim.play(1, 1, 0.5f) }, start = 0.5f).takeLast(20).average().toFloat()
        }
        assertTrue("estimates $est", est[0] < 0.45f && est[2] > 0.55f && est[0] < est[1] && est[1] < est[2])
    }

    /** When difficulty follows the player (DDA), the estimate converges near true skill. */
    @Test
    fun estimateConvergesUnderDifficultyAdjustment() = runBlocking {
        for (trueSkill in listOf(0.2f, 0.5f, 0.85f)) {
            val sim = PlayerSimulator(trueSkill, Random(9))
            val rules = RulesProvider()
            val history = mutableListOf<LevelResult>()
            var d = Difficulty.START
            repeat(40) {
                history += sim.play(1, 1, d)
                d = Difficulty.apply(d, rules.decide(history.takeLast(5)).decision)
            }
            val est = Skill.estimates(history).takeLast(10).average().toFloat()
            assertTrue("true $trueSkill estimated $est", abs(est - trueSkill) <= 0.15f)
        }
    }

    /** The rules baseline should steer a simulated player into the flow channel. */
    @Test
    fun rulesConvergeTowardSimulatedSkill() = runBlocking {
        for (trueSkill in listOf(0.2f, 0.5f, 0.85f)) {
            val sim = PlayerSimulator(trueSkill, Random(3))
            val rules = RulesProvider()
            val history = mutableListOf<LevelResult>()
            var d = Difficulty.START
            repeat(30) {
                history += sim.play(1, 1, d)
                d = Difficulty.apply(d, rules.decide(history.takeLast(5)).decision)
            }
            val lastTen = history.takeLast(10).map { it.difficulty }
            val mean = lastTen.average().toFloat()
            assertTrue("skill $trueSkill settled at difficulty $mean", abs(mean - trueSkill) <= 0.2f)
        }
    }
}
