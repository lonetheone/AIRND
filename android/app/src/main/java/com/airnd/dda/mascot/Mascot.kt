package com.airnd.dda.mascot

import com.airnd.dda.dda.Decision
import com.airnd.dda.dda.FlowZone
import com.airnd.dda.llm.GenStats
import com.airnd.dda.llm.LlmEngine
import com.airnd.dda.metrics.LevelResult
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/** One mascot line, where it came from, and what it cost. */
data class MascotLine(val text: String, val fromModel: Boolean, val stats: GenStats?, val note: String = "")

/**
 * Pip, the mascot: one short comment after each level, written by the
 * on-device model from the level's facts. The game stays in charge: output is
 * cleaned and checked, and anything unusable (or too slow) falls back to a
 * canned line for the same situation.
 */
class Mascot(private val engine: LlmEngine) {

    suspend fun comment(r: LevelResult, next: Decision, zone: FlowZone, seed: Int): MascotLine {
        if (!engine.isLoaded) return canned(r, "model not loaded")
        val out = withTimeoutOrNull(TIMEOUT_MS) {
            engine.generate(MascotText.prompt(r, next, zone), MAX_TOKENS, TEMPERATURE, seed)
        } ?: return canned(r, "timeout or failure")
        val cleaned = MascotText.clean(out.first)
            ?: return canned(r, "rejected: \"${out.first.take(60)}\"").copy(stats = out.second)
        return MascotLine(cleaned, fromModel = true, stats = out.second)
    }

    private fun canned(r: LevelResult, note: String) = MascotLine(MascotText.canned(r), fromModel = false, stats = null, note = note)

    companion object {
        const val TIMEOUT_MS = 4000L
        const val MAX_TOKENS = 32
        const val TEMPERATURE = 0.7f
    }
}

/** Prompt, output checking and fallback lines: pure text, no model, unit-tested. */
object MascotText {
    const val SYSTEM = "You are Pip, a cheerful fox mascot in a match-3 puzzle game. " +
        "After each level you say one short, friendly sentence to the player (under 15 words). " +
        "Be encouraging, specific to what happened, and never mean. No emojis."
    private val BANNED = listOf("as an ai", "assistant", "i'm sorry", "i cannot", "language model", "http")

    fun facts(r: LevelResult, next: Decision, zone: FlowZone): String = buildString {
        append(
            when {
                r.won -> "The player won the level with ${(r.movesLeftFraction * 100).roundToInt()}% of the moves left."
                r.quit -> "The player gave up and restarted the level."
                else -> "The player ran out of moves with ${(r.goalProgress * 100).roundToInt()}% of the goal done."
            },
        )
        if (r.attempt > 1) append(" It was try number ${r.attempt}.")
        append(
            when (next) {
                Decision.EASIER -> " The next level will be a bit easier."
                Decision.SAME -> " The next level stays about the same."
                Decision.HARDER -> " The next level will be a bit harder."
            },
        )
        if (zone == FlowZone.TOO_HARD) append(" The player seems to be struggling.")
        if (zone == FlowZone.TOO_EASY) append(" The player finds it too easy.")
    }

    fun prompt(r: LevelResult, next: Decision, zone: FlowZone): String =
        "<|im_start|>system\n$SYSTEM<|im_end|>\n" +
            "<|im_start|>user\n${facts(r, next, zone)} Write Pip's comment.<|im_end|>\n" +
            "<|im_start|>assistant\n"

    /** First sentence, no quotes or emoji, sensible length; null if unusable. */
    fun clean(raw: String): String? {
        var s = raw.trim().lineSequence().firstOrNull()?.trim() ?: return null
        s = s.trim('"', '\'', '“', '”', ' ')
        s = s.filter { it.code < 0x2000 || it in "’…—–" }
        // Keep the first sentence if it stands alone (5+ words), else the first two:
        // "So close! You almost had it." is one comment, not a 2-word line.
        val ends = s.indices.filter { s[it] == '.' || s[it] == '!' || s[it] == '?' }
        val first = ends.firstOrNull()
        if (first != null) {
            val cut = if (s.substring(0, first + 1).split(' ').count { it.isNotBlank() } >= 5) first
            else ends.getOrNull(1) ?: s.length - 1
            s = s.substring(0, cut + 1).trim()
        }
        val words = s.split(' ').count { it.isNotBlank() }
        if (words < 3 || words > 22 || s.count { it.isLetter() } < 8) return null
        if (BANNED.any { s.contains(it, ignoreCase = true) }) return null
        return s
    }

    fun canned(r: LevelResult): String = when {
        r.won && r.movesLeftFraction >= 0.4f -> "Wow, that was smooth! Ready for a challenge?"
        r.won -> "You did it! Nice matching."
        r.quit -> "No worries, a fresh board might be luckier."
        r.goalProgress >= 0.8f -> "So close! You almost had it."
        else -> "Tricky one! Let's try again together."
    }
}
