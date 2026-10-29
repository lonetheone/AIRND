package com.airnd.dda.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airnd.dda.dda.Skill
import com.airnd.dda.metrics.LevelResult
import kotlin.math.abs
import kotlin.math.roundToInt

// Categorical slots 1 and 2 of the reference palette, dark steps; validated on
// this panel's surface (#0e1013): CVD ΔE 26.8, contrast >= 3:1.
private val DIFFICULTY = Color(0xFF3987E5)
private val SKILL = Color(0xFFD95926)
private val SURFACE = Color(0xFF0E1013)
private val BAND = Color(0x33C3C2B7)
private val GRID = Color(0xFF2A2D33)
private val TEXT = Color(0xFFF0EFEC)
private val MUTED = Color(0xFF9A9990)
private val TRUE_SKILL = Color(0xFFC3C2B7)
private const val WINDOW = 40

/**
 * Difficulty played vs estimated skill per attempt, on one 0..1 axis, with the
 * flow channel (skill ± [Skill.FLOW_HALF_WIDTH]) as a band. Above the band is
 * too hard (anxiety), below it too easy (boredom). Tap a point for its values;
 * the history table below is the table view of the same data.
 */
@Composable
fun FlowChart(fullHistory: List<LevelResult>, modifier: Modifier = Modifier, chartHeight: Dp = 190.dp) {
    // Estimate over everything, draw only the newest WINDOW attempts so points stay readable.
    val allSkills = remember(fullHistory.size) { Skill.estimates(fullHistory) }
    val history = fullHistory.takeLast(WINDOW)
    val skills = allSkills.takeLast(WINDOW)
    val hasTrue = history.any { it.trueSkill != null }
    val inFlow = history.indices.count { Skill.inFlow(history[it].difficulty, skills[it]) }
    var selected by remember(history.size) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontSize = 10.sp, color = MUTED)
    val label = TextStyle(fontSize = 11.sp, color = TEXT)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(9.dp).background(DIFFICULTY, CircleShape))
            Text("Difficulty", fontSize = 12.sp, color = TEXT)
            Box(Modifier.size(9.dp).background(SKILL, RoundedCornerShape(1.dp)))
            Text("Skill estimate", fontSize = 12.sp, color = TEXT)
            Box(Modifier.size(width = 14.dp, height = 9.dp).background(BAND))
            Text("Flow channel", fontSize = 12.sp, color = TEXT)
            if (hasTrue) {
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(TRUE_SKILL))
                Text("True skill", fontSize = 12.sp, color = TEXT)
            }
        }
        Text("Above the band: too hard (anxiety) · below: too easy (boredom)",
            fontSize = 11.sp, color = MUTED)
        if (history.isEmpty()) {
            Text("Play or simulate a few levels to see the chart.", fontSize = 12.sp, color = MUTED)
            return
        }
        Text("In flow: $inFlow of ${history.size} attempts (difficulty within skill ±${Skill.FLOW_HALF_WIDTH})" +
            if (fullHistory.size > WINDOW) ", last $WINDOW of ${fullHistory.size}" else "",
            fontSize = 12.sp, color = TEXT)

        Canvas(
            Modifier.fillMaxWidth().height(chartHeight).background(SURFACE).pointerInput(history.size) {
                detectTapGestures { p ->
                    val left = 30.dp.toPx()
                    val right = size.width - 78.dp.toPx()
                    val step = if (history.size > 1) (right - left) / (history.size - 1) else 0f
                    selected = if (step == 0f) 0 else ((p.x - left) / step).roundToInt().coerceIn(0, history.size - 1)
                }
            },
        ) {
            val left = 30.dp.toPx()
            val right = size.width - 78.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val n = history.size
            fun x(i: Int) = if (n == 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
            fun y(v: Float) = bottom - (bottom - top) * v.coerceIn(0f, 1f)

            for (v in listOf(0f, 0.5f, 1f)) {
                drawLine(GRID, Offset(left, y(v)), Offset(right, y(v)), 1.dp.toPx())
                drawText(measurer, "%.1f".format(v), Offset(2.dp.toPx(), y(v) - 7.dp.toPx()), small)
            }
            drawText(measurer, "attempt →", Offset(right - 44.dp.toPx(), bottom + 1.dp.toPx()), small)

            val band = Path()
            for (i in 0 until n) {
                val p = Offset(x(i), y(skills[i] + Skill.FLOW_HALF_WIDTH))
                if (i == 0) band.moveTo(p.x, p.y) else band.lineTo(p.x, p.y)
            }
            for (i in n - 1 downTo 0) band.lineTo(x(i), y(skills[i] - Skill.FLOW_HALF_WIDTH))
            band.close()
            drawPath(band, BAND)

            selected?.let { drawLine(MUTED, Offset(x(it), top), Offset(x(it), bottom), 1.dp.toPx()) }

            // True skill of a simulated player: thin dashed reference line, no markers.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            for (i in 1 until n) {
                val a = history[i - 1].trueSkill ?: continue
                val b = history[i].trueSkill ?: continue
                drawLine(TRUE_SKILL, Offset(x(i - 1), y(a)), Offset(x(i), y(b)), 1.5.dp.toPx(), pathEffect = dash)
            }

            series(n, ::x, { y(skills[it]) }, SKILL, square = true)
            series(n, ::x, { y(history[it].difficulty) }, DIFFICULTY, square = false)

            // Direct labels at the newest point, nudged apart if they would overlap.
            val dy = y(history.last().difficulty)
            val sy = y(skills.last())
            val gap = 14.dp.toPx()
            val (dLabel, sLabel) = if (abs(dy - sy) >= gap) dy to sy
            else if (dy <= sy) (dy - gap / 2) to (sy + gap / 2) else (dy + gap / 2) to (sy - gap / 2)
            drawText(measurer, "difficulty %.2f".format(history.last().difficulty),
                Offset(right + 8.dp.toPx(), dLabel - 7.dp.toPx()), label)
            drawText(measurer, "skill %.2f".format(skills.last()),
                Offset(right + 8.dp.toPx(), sLabel - 7.dp.toPx()), label)
        }

        selected?.let { i ->
            val r = history[i]
            val s = skills[i]
            val zone = when {
                Skill.inFlow(r.difficulty, s) -> "in flow"
                r.difficulty > s -> "too hard"
                else -> "too easy"
            }
            val number = i + 1 + fullHistory.size - history.size
            val truth = r.trueSkill?.let { " (true %.2f)".format(it) } ?: ""
            Text("Attempt $number (level ${r.level}, try ${r.attempt}): difficulty %.2f, skill %.2f%s, %s — %s"
                .format(r.difficulty, s, truth, if (r.won) "won" else if (r.quit) "quit" else "lost", zone),
                fontSize = 12.sp, color = TEXT)
        }
    }
}

/** 2px line plus >= 8px markers with a 2px surface ring; skill uses squares as a non-color cue. */
private fun DrawScope.series(n: Int, x: (Int) -> Float, y: (Int) -> Float, color: Color, square: Boolean) {
    for (i in 1 until n) drawLine(color, Offset(x(i - 1), y(i - 1)), Offset(x(i), y(i)), 2.dp.toPx())
    val r = 4.dp.toPx()
    val ring = 2.dp.toPx()
    for (i in 0 until n) {
        val c = Offset(x(i), y(i))
        if (square) {
            drawRect(SURFACE, Offset(c.x - r - ring, c.y - r - ring), Size(2 * (r + ring), 2 * (r + ring)))
            drawRect(color, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
        } else {
            drawCircle(SURFACE, r + ring, c)
            drawCircle(color, r, c)
        }
    }
}
