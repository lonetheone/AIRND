package com.airnd.dda.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.airnd.dda.dda.DecisionResult
import com.airnd.dda.dda.Difficulty
import com.airnd.dda.dda.DifficultyProvider
import com.airnd.dda.metrics.LevelResult
import com.airnd.dda.metrics.MetricsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Phase { PLAYING, DECIDING, ENDED }

/**
 * Level flow: build a level from the difficulty, take swaps, end the level,
 * ask the provider for the next difficulty (off the main thread, between
 * levels only), log the attempt. The game stays authoritative: a provider's
 * decision only moves the difficulty by one clamped step.
 */
class GameController(
    private val scope: CoroutineScope,
    private val log: MetricsLog,
    var provider: DifficultyProvider,
) {
    var level by mutableIntStateOf(1); private set
    var attempt by mutableIntStateOf(1); private set
    var difficulty by mutableStateOf(Difficulty.START); private set
    var knobs by mutableStateOf(Difficulty.knobs(Difficulty.START)); private set
    var board by mutableStateOf(Board(BOARD_SIZE, knobs.colors, Random(0))); private set
    var goalColor by mutableIntStateOf(0); private set
    var collected by mutableIntStateOf(0); private set
    var movesUsed by mutableIntStateOf(0); private set
    var phase by mutableStateOf(Phase.PLAYING); private set
    var lastResult by mutableStateOf<LevelResult?>(null); private set
    var lastDecision by mutableStateOf<DecisionResult?>(null); private set
    var hint by mutableStateOf<Pair<Cell, Cell>?>(null); private set
    /** Bumped on every board change so the canvas redraws. */
    var boardVersion by mutableIntStateOf(0); private set
    val history = mutableStateListOf<LevelResult>()

    private var invalidSwaps = 0
    private var hintsUsed = 0
    private var levelStartMs = 0L
    private var nextDifficulty = Difficulty.START

    val movesLeft get() = knobs.moves - movesUsed

    init { startLevel() }

    private fun startLevel() {
        knobs = Difficulty.knobs(difficulty)
        val random = Random(level * 1000 + attempt)
        board = Board(BOARD_SIZE, knobs.colors, random)
        goalColor = random.nextInt(knobs.colors)
        collected = 0
        movesUsed = 0
        invalidSwaps = 0
        hintsUsed = 0
        hint = null
        levelStartMs = System.currentTimeMillis()
        phase = Phase.PLAYING
        boardVersion++
    }

    fun swap(a: Cell, b: Cell) {
        if (phase != Phase.PLAYING) return
        val result = board.swap(a, b)
        hint = null
        if (!result.accepted) {
            invalidSwaps++
            return
        }
        movesUsed++
        collected += result.cleared[goalColor]
        boardVersion++
        when {
            collected >= knobs.goal -> endLevel(won = true, quit = false)
            movesLeft <= 0 -> endLevel(won = false, quit = false)
        }
    }

    fun showHint() {
        if (phase != Phase.PLAYING) return
        hintsUsed++
        hint = board.findMove()
    }

    /** Restart mid-level counts as a quit: a strong frustration signal. */
    fun restart() {
        if (phase == Phase.PLAYING) endLevel(won = false, quit = true)
    }

    private fun endLevel(won: Boolean, quit: Boolean) {
        val seconds = (System.currentTimeMillis() - levelStartMs) / 1000f
        val result = LevelResult(
            level = level, attempt = attempt, difficulty = difficulty, won = won,
            movesLeftFraction = if (won) movesLeft.toFloat() / knobs.moves else 0f,
            goalProgress = (collected.toFloat() / knobs.goal).coerceAtMost(1f),
            avgSecondsPerMove = if (movesUsed > 0) seconds / movesUsed else seconds,
            invalidSwaps = invalidSwaps, hintsUsed = hintsUsed, quit = quit,
        )
        history += result
        lastResult = result
        phase = Phase.DECIDING
        val recent = history.takeLast(HISTORY_WINDOW)
        scope.launch {
            val decision = withContext(Dispatchers.Default) { provider.decide(recent) }
            nextDifficulty = Difficulty.apply(difficulty, decision.decision)
            withContext(Dispatchers.IO) { log.append(result, decision, nextDifficulty) }
            lastDecision = decision
            phase = Phase.ENDED
        }
    }

    /** Next level after a win, the same level again after a loss. */
    fun continueAfterLevel() {
        if (phase != Phase.ENDED) return
        if (lastResult?.won == true) { level++; attempt = 1 } else attempt++
        difficulty = nextDifficulty
        startLevel()
    }

    companion object {
        const val BOARD_SIZE = 8
        const val HISTORY_WINDOW = 5
    }
}
