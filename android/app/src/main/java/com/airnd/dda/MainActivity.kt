package com.airnd.dda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.lifecycleScope
import com.airnd.dda.dda.RulesProvider
import com.airnd.dda.game.GameController
import com.airnd.dda.llm.LlmEngine
import com.airnd.dda.llm.LlmStatus
import com.airnd.dda.llm.Memory
import com.airnd.dda.mascot.Mascot
import com.airnd.dda.metrics.MetricsLog
import com.airnd.dda.ui.GameScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = MetricsLog(filesDir)
        val engine = LlmEngine(applicationContext)
        val game = GameController(lifecycleScope, log, RulesProvider(), Mascot(engine))

        // Load the model in the background; the game is playable meanwhile and
        // Pip uses canned lines until it is ready.
        lifecycleScope.launch {
            LlmStatus.state = "llm: loading $MODEL_ASSET..."
            val before = Memory.read().rssMiB
            val ok = engine.load(MODEL_ASSET, threads = 4, nCtx = 512)
            val after = Memory.read().rssMiB
            LlmStatus.state = if (ok) "llm: $MODEL_ASSET loaded in ${engine.loadMs} ms, +${after - before} MiB RSS"
            else "llm: FAILED to load $MODEL_ASSET (canned lines only)"
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GameScreen(game, log.file.absolutePath)
            }
        }
    }

    private companion object {
        const val MODEL_ASSET = "smollm2-135m-instruct-q4_k_m.gguf"
    }
}
