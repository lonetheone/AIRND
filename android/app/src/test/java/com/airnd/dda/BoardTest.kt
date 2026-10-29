package com.airnd.dda

import com.airnd.dda.game.Board
import com.airnd.dda.game.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    // A 5x5 board with no matches; swapping (0,2)<->(1,2) lines up three 0s in row 0.
    private val rows = listOf(
        "00102",
        "12010",
        "20121",
        "01202",
        "12010",
    )

    private fun board() = Board(5, 3, Random(1)).also { it.load(rows) }

    @Test
    fun newBoardHasNoMatchesAndHasAMove() {
        repeat(200) { seed ->
            val b = Board(8, 6, Random(seed))
            assertTrue("seed $seed starts with a match", b.findMatches().isEmpty())
            assertNotNull("seed $seed has no legal move", b.findMove())
        }
    }

    @Test
    fun swapThatMatchesIsAcceptedAndClears() {
        val b = board()
        val result = b.swap(Cell(0, 2), Cell(1, 2))
        assertTrue(result.accepted)
        assertTrue("cleared at least the three 0s", result.cleared[0] >= 3)
        assertTrue(b.findMatches().isEmpty())
    }

    @Test
    fun swapWithoutMatchIsRejectedAndReverted() {
        val b = board()
        val before = (0 until 5).map { r -> (0 until 5).map { c -> b[r, c] } }
        val result = b.swap(Cell(4, 0), Cell(4, 1))
        assertFalse(result.accepted)
        assertEquals(before, (0 until 5).map { r -> (0 until 5).map { c -> b[r, c] } })
    }

    @Test
    fun nonNeighbourSwapIsRejected() {
        assertFalse(board().swap(Cell(0, 0), Cell(2, 0)).accepted)
    }

    @Test
    fun boardStaysFullAfterManyMoves() {
        val b = Board(8, 5, Random(7))
        repeat(300) {
            val move = b.findMove() ?: error("stable board without a move")
            assertTrue(b.swap(move.first, move.second).accepted)
            for (r in 0 until 8) for (c in 0 until 8) assertTrue(b[r, c] in 0 until 5)
        }
    }
}
