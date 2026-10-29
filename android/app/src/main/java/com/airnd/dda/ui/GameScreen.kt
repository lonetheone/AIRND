package com.airnd.dda.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airnd.dda.dda.Difficulty
import com.airnd.dda.dda.Skill
import com.airnd.dda.game.Cell
import com.airnd.dda.game.GameController
import com.airnd.dda.game.Phase
import com.airnd.dda.llm.LlmStatus
import com.airnd.dda.llm.Memory
import kotlin.math.abs

private val TILE_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFFDD835), Color(0xFF8E24AA), Color(0xFFFB8C00),
)

@Composable
fun GameScreen(game: GameController, logPath: String) {
    var showDebug by remember { mutableStateOf(true) }
    var showGraph by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF15171C)).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Hud(game)
        val historySize = game.history.size
        val flow = remember(historySize, game.difficulty) { Skill.check(game.history.toList(), game.difficulty) }
        FlowBadge(flow, hasHistory = historySize > 0)
        if (showGraph) {
            // Graph mode takes the board's place and the debug panel's space.
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowChart(game.history.toList(), Modifier.fillMaxWidth(), chartHeight = 260.dp)
                if (historySize > 0) FlowMeter(flow, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, skill) in listOf("Sim weak" to 0.2f, "Sim average" to 0.5f, "Sim expert" to 0.85f)) {
                        OutlinedButton(onClick = { game.simulate(skill, 12) }, enabled = game.phase == Phase.PLAYING) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            BoardView(game, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = game::showHint, enabled = !showGraph) { Text("Hint") }
            OutlinedButton(onClick = game::restart, enabled = !showGraph) { Text("Restart") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showGraph = !showGraph }) { Text(if (showGraph) "Board" else "Graph") }
            TextButton(onClick = { showDebug = !showDebug }) { Text(if (showDebug) "Hide debug" else "Debug") }
        }
        if (showDebug && !showGraph) DebugPanel(game, logPath, Modifier.weight(1f))
    }
    if (game.phase != Phase.PLAYING) LevelEndDialog(game)
}

@Composable
private fun Hud(game: GameController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Level ${game.level}" + if (game.attempt > 1) "  (try ${game.attempt})" else "",
            color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(20.dp).background(TILE_COLORS[game.goalColor], CircleShape))
        Text("  ${game.collected.coerceAtMost(game.knobs.goal)} / ${game.knobs.goal}",
            color = Color.White, fontSize = 18.sp)
        Text("   Moves ${game.movesLeft}", color = Color(0xFFB0BEC5), fontSize = 18.sp)
    }
}

@Composable
private fun BoardView(game: GameController, modifier: Modifier) {
    var selected by remember { mutableStateOf<Cell?>(null) }
    val size = GameController.BOARD_SIZE
    Canvas(
        modifier
            .background(Color(0xFF22252D))
            .pointerInput(game.board) {
                detectTapGestures { p ->
                    val cell = Cell((p.y / (this.size.height / size)).toInt(), (p.x / (this.size.width / size)).toInt())
                    val current = selected
                    selected = when {
                        current == null -> cell
                        current.isNeighbour(cell) -> { game.swap(current, cell); null }
                        else -> cell
                    }
                }
            }
            .pointerInput(game.board) {
                var origin: Cell? = null
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { p ->
                        origin = Cell((p.y / (this.size.height / size)).toInt(), (p.x / (this.size.width / size)).toInt())
                        drag = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val from = origin ?: return@detectDragGestures
                        drag += amount
                        val threshold = this.size.width / size * 0.4f
                        if (abs(drag.x) > threshold || abs(drag.y) > threshold) {
                            val to = if (abs(drag.x) > abs(drag.y)) Cell(from.row, from.col + if (drag.x > 0) 1 else -1)
                            else Cell(from.row + if (drag.y > 0) 1 else -1, from.col)
                            game.swap(from, to)
                            selected = null
                            origin = null
                        }
                    },
                )
            },
    ) {
        game.boardVersion // read so the canvas redraws after each change
        val cell = this.size.width / size
        for (r in 0 until size) for (c in 0 until size) {
            val v = game.board[r, c]
            if (v < 0) continue
            val center = Offset(c * cell + cell / 2, r * cell + cell / 2)
            drawCircle(TILE_COLORS[v], radius = cell * 0.38f, center = center)
        }
        selected?.let {
            drawRoundRect(Color.White, Offset(it.col * cell + 2, it.row * cell + 2),
                Size(cell - 4, cell - 4), CornerRadius(8f), style = Stroke(4f))
        }
        game.hint?.toList()?.forEach {
            drawRoundRect(Color(0xFF00E5FF), Offset(it.col * cell + 2, it.row * cell + 2),
                Size(cell - 4, cell - 4), CornerRadius(8f), style = Stroke(5f))
        }
    }
}

@Composable
private fun LevelEndDialog(game: GameController) {
    val result = game.lastResult ?: return
    val decision = game.lastDecision.takeIf { game.phase == Phase.ENDED }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (result.won) "Level complete" else if (result.quit) "Restarted" else "Out of moves") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val line = game.mascotLine
                Text(
                    if (decision == null) "Pip is watching..." else "Pip: " + (line?.text ?: "…"),
                    fontSize = 18.sp,
                )
                if (line != null) {
                    Text(
                        if (line.fromModel) "on-device model · %.0f ms · %d tokens".format(line.stats?.totalMs ?: 0.0, line.stats?.genTokens ?: 0)
                        else "canned line (${line.note})",
                        fontSize = 11.sp, color = Color(0xFF9A9990),
                    )
                }
                if (decision != null) {
                    Text("Next level: ${decision.decision} (${decision.source}: ${decision.reason}, ${decision.latencyMs} ms)",
                        fontSize = 12.sp, color = Color(0xFF9A9990))
                }
            }
        },
        confirmButton = {
            Button(onClick = game::continueAfterLevel, enabled = decision != null) {
                Text(if (result.won) "Next level" else "Try again")
            }
        },
    )
}

/** Model state plus current/peak process memory (read from /proc on each redraw). */
private fun llmStatus(): String {
    val m = Memory.read()
    return "${LlmStatus.state}\nprocess RAM ${m.rssMiB} MiB (peak ${m.peakMiB} MiB)"
}

@Composable
private fun DebugPanel(game: GameController, logPath: String, modifier: Modifier) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFCFD8DC))
    Column(
        modifier.fillMaxWidth().background(Color(0xFF0E1013)).padding(8.dp).verticalScroll(rememberScrollState()),
    ) {
        val k = game.knobs
        Text("provider: ${game.provider.name} (difficulty stays on rules)", style = mono)
        Text(llmStatus(), style = mono)
        game.mascotLine?.let { l ->
            Text(
                if (l.fromModel && l.stats != null) "pip: model, prompt %d tok %.0f ms, gen %d tok %.0f ms (%.1f tok/s)"
                    .format(l.stats.promptTokens, l.stats.promptMs, l.stats.genTokens, l.stats.genMs, l.stats.tokensPerSecond)
                else "pip: canned (${l.note})",
                style = mono,
            )
        }
        Text("difficulty: %.2f  step ±%.1f".format(game.difficulty, Difficulty.STEP), style = mono)
        Text("knobs: ${k.colors} colors, ${k.moves} moves, goal ${k.goal}", style = mono)
        game.lastDecision?.let {
            Text("last: ${it.decision} by ${it.source} in ${it.latencyMs} ms — ${it.reason}" +
                (it.fallbackReason?.let { f -> " [fallback: $f]" } ?: ""), style = mono)
        }
        Text("", style = mono)
        Text("lvl try  diff  won  left  goal  s/move inv hint quit", style = mono)
        game.history.takeLast(8).forEach { h ->
            Text("%3d %3d  %.2f  %-4s %.2f  %.2f  %5.1f %3d %4d %-4s".format(
                h.level, h.attempt, h.difficulty, if (h.won) "yes" else "no", h.movesLeftFraction,
                h.goalProgress, h.avgSecondsPerMove, h.invalidSwaps, h.hintsUsed, if (h.quit) "yes" else ""), style = mono)
        }
        Text("", style = mono)
        Text("log: $logPath", style = mono)
    }
}
