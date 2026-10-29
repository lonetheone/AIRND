package com.airnd.dda.game

import kotlin.math.abs
import kotlin.random.Random

/** A cell position on the board. */
data class Cell(val row: Int, val col: Int) {
    fun isNeighbour(other: Cell) = abs(row - other.row) + abs(col - other.col) == 1
}

/** What one swap did: whether it was accepted and how many tiles of each color it cleared. */
data class SwapResult(val accepted: Boolean, val cleared: IntArray, val cascades: Int, val reshuffled: Boolean)

/**
 * Match-3 board, pure Kotlin so it runs in unit tests. Tiles are color indices
 * 0 until [colors]. A swap is accepted only if it makes a match; matches clear,
 * tiles fall, new tiles fill from the top, and this repeats until the board is
 * stable. A stable board always has at least one legal move (reshuffled if not).
 */
class Board(val size: Int, val colors: Int, private val random: Random) {

    private val tiles = IntArray(size * size)

    init {
        require(colors in 3..8) { "colors must be 3..8" }
        fillWithoutMatches()
    }

    operator fun get(row: Int, col: Int) = tiles[row * size + col]
    operator fun get(cell: Cell) = this[cell.row, cell.col]
    private operator fun set(row: Int, col: Int, value: Int) { tiles[row * size + col] = value }

    fun inside(cell: Cell) = cell.row in 0 until size && cell.col in 0 until size

    /** Swaps [a] and [b]; reverts and returns accepted = false if no match results. */
    fun swap(a: Cell, b: Cell): SwapResult {
        val cleared = IntArray(colors)
        if (!inside(a) || !inside(b) || !a.isNeighbour(b)) return SwapResult(false, cleared, 0, false)
        exchange(a, b)
        if (findMatches().isEmpty()) {
            exchange(a, b)
            return SwapResult(false, cleared, 0, false)
        }
        var cascades = 0
        while (true) {
            val matched = findMatches()
            if (matched.isEmpty()) break
            cascades++
            for (index in matched) {
                cleared[tiles[index]]++
                tiles[index] = EMPTY
            }
            collapseAndRefill()
        }
        val reshuffled = if (findMove() == null) { fillWithoutMatches(); true } else false
        return SwapResult(true, cleared, cascades, reshuffled)
    }

    /** A legal move if one exists: used for hints and to detect a stuck board. */
    fun findMove(): Pair<Cell, Cell>? {
        for (r in 0 until size) for (c in 0 until size) {
            for (other in listOf(Cell(r, c + 1), Cell(r + 1, c))) {
                if (!inside(other)) continue
                val here = Cell(r, c)
                exchange(here, other)
                val matches = findMatches().isNotEmpty()
                exchange(here, other)
                if (matches) return here to other
            }
        }
        return null
    }

    /** Indices of every tile in a horizontal or vertical run of 3 or more. */
    internal fun findMatches(): Set<Int> {
        val out = HashSet<Int>()
        for (r in 0 until size) {
            var start = 0
            for (c in 1..size) {
                if (c == size || this[r, c] != this[r, start] || this[r, start] == EMPTY) {
                    if (c - start >= 3 && this[r, start] != EMPTY) for (k in start until c) out += r * size + k
                    start = c
                }
            }
        }
        for (c in 0 until size) {
            var start = 0
            for (r in 1..size) {
                if (r == size || this[r, c] != this[start, c] || this[start, c] == EMPTY) {
                    if (r - start >= 3 && this[start, c] != EMPTY) for (k in start until r) out += k * size + c
                    start = r
                }
            }
        }
        return out
    }

    private fun exchange(a: Cell, b: Cell) {
        val t = this[a]
        this[a.row, a.col] = this[b]
        this[b.row, b.col] = t
    }

    private fun collapseAndRefill() {
        for (c in 0 until size) {
            var write = size - 1
            for (r in size - 1 downTo 0) {
                val v = this[r, c]
                if (v != EMPTY) { this[write, c] = v; write-- }
            }
            for (r in write downTo 0) this[r, c] = random.nextInt(colors)
        }
    }

    /** Random board with no ready-made matches and at least one legal move. */
    private fun fillWithoutMatches() {
        do {
            for (r in 0 until size) for (c in 0 until size) {
                var v: Int
                do {
                    v = random.nextInt(colors)
                } while ((c >= 2 && this[r, c - 1] == v && this[r, c - 2] == v) ||
                    (r >= 2 && this[r - 1, c] == v && this[r - 2, c] == v))
                this[r, c] = v
            }
        } while (findMove() == null)
    }

    /** Test hook: replace the tiles with [rows] (one string of digits per row). */
    internal fun load(rows: List<String>) {
        require(rows.size == size && rows.all { it.length == size })
        for (r in 0 until size) for (c in 0 until size) this[r, c] = rows[r][c].digitToInt()
    }

    companion object {
        const val EMPTY = -1
    }
}
