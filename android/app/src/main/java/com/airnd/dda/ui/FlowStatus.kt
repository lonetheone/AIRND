package com.airnd.dda.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airnd.dda.dda.FlowCheck
import com.airnd.dda.dda.FlowZone
import com.airnd.dda.dda.Skill

// Status palette (fixed, never reused for series): good / warning / serious.
// Always paired with an icon and a label, never color alone.
private val GOOD = Color(0xFF0CA30C)
private val WARNING = Color(0xFFFAB219)
private val SERIOUS = Color(0xFFEC835A)
private val TEXT = Color(0xFFF0EFEC)
private val MUTED = Color(0xFF9A9990)
private val TRACK = Color(0xFF2A2D33)
private val BAND = Color(0x33C3C2B7)

private fun FlowZone.color() = when (this) {
    FlowZone.IN_FLOW -> GOOD
    FlowZone.TOO_EASY -> WARNING
    FlowZone.TOO_HARD -> SERIOUS
}

private fun FlowZone.label() = when (this) {
    FlowZone.IN_FLOW -> "✓ In flow"
    FlowZone.TOO_EASY -> "▼ Too easy"
    FlowZone.TOO_HARD -> "▲ Too hard"
}

/** Compact badge for the game screen: is the next level in the player's flow channel? */
@Composable
fun FlowBadge(check: FlowCheck, hasHistory: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.border(1.dp, if (hasHistory) check.zone.color() else MUTED, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hasHistory) {
            Text("Flow: play a level to start", fontSize = 13.sp, color = MUTED)
            return@Row
        }
        Text(check.zone.label(), fontSize = 13.sp, color = TEXT, fontWeight = FontWeight.SemiBold)
        Text("gap %+.2f · skill %.2f · streak %d".format(check.gap, check.skill, check.streak),
            fontSize = 12.sp, color = MUTED)
    }
}

/**
 * Flow meter: where the next level's difficulty sits relative to skill, on a
 * -0.4..+0.4 gap scale. The middle band is the flow channel; left is too easy,
 * right is too hard. The marker carries the zone's status color plus its label.
 */
@Composable
fun FlowMeter(check: FlowCheck, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontSize = 10.sp, color = MUTED)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Flow check (next level): ${check.zone.label()}  gap %+.2f".format(check.gap),
            fontSize = 12.sp, color = TEXT)
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val range = 0.4f
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            fun x(gap: Float) = pad + w * ((gap.coerceIn(-range, range) + range) / (2 * range))
            val trackY = 8.dp.toPx()
            val trackH = 8.dp.toPx()
            drawRoundRect(TRACK, Offset(pad, trackY), Size(w, trackH), CornerRadius(4.dp.toPx()))
            val bandLeft = x(-Skill.FLOW_HALF_WIDTH)
            drawRect(BAND, Offset(bandLeft, trackY - 3.dp.toPx()),
                Size(x(Skill.FLOW_HALF_WIDTH) - bandLeft, trackH + 6.dp.toPx()))
            val cx = x(check.gap)
            drawCircle(Color(0xFF0E1013), 9.dp.toPx(), Offset(cx, trackY + trackH / 2))
            drawCircle(check.zone.color(), 7.dp.toPx(), Offset(cx, trackY + trackH / 2))
            val labelY = trackY + trackH + 4.dp.toPx()
            drawText(measurer, "too easy", Offset(pad, labelY), small)
            val flow = measurer.measure("flow", small)
            drawText(flow, topLeft = Offset(x(0f) - flow.size.width / 2f, labelY))
            val hard = measurer.measure("too hard", small)
            drawText(hard, topLeft = Offset(pad + w - hard.size.width, labelY))
        }
    }
}
