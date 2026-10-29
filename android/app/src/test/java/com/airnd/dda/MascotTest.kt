package com.airnd.dda

import com.airnd.dda.dda.Decision
import com.airnd.dda.dda.FlowZone
import com.airnd.dda.mascot.MascotText
import com.airnd.dda.metrics.LevelResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotTest {

    private fun result(won: Boolean, left: Float = 0f, progress: Float = 1f, quit: Boolean = false, attempt: Int = 1) =
        LevelResult(1, attempt, 0.3f, won, left, progress, 2f, 0, 0, quit)

    @Test
    fun cleanKeepsOneGoodSentence() {
        assertEquals("Great job, you cleared it with moves to spare!",
            MascotText.clean("\"Great job, you cleared it with moves to spare!\" And then more text"))
    }

    @Test
    fun cleanRejectsJunk() {
        assertNull(MascotText.clean("Ok"))
        assertNull(MascotText.clean("As an AI language model I cannot comment on games."))
        assertNull(MascotText.clean(""))
    }

    @Test
    fun factsDescribeTheLevel() {
        val f = MascotText.facts(result(false, progress = 0.9f, attempt = 3), Decision.EASIER, FlowZone.TOO_HARD)
        assertTrue(f, f.contains("90%") && f.contains("try number 3") && f.contains("easier") && f.contains("struggling"))
    }

    @Test
    fun everySituationHasACannedLine() {
        listOf(result(true, left = 0.5f), result(true, left = 0.1f), result(false, quit = true),
            result(false, progress = 0.9f), result(false, progress = 0.2f)).forEach {
            assertTrue(MascotText.clean(MascotText.canned(it)) != null)
        }
    }
}
