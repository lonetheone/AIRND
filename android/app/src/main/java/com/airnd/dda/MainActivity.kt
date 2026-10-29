package com.airnd.dda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.lifecycleScope
import com.airnd.dda.dda.RulesProvider
import com.airnd.dda.game.GameController
import com.airnd.dda.metrics.MetricsLog
import com.airnd.dda.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = MetricsLog(filesDir)
        val game = GameController(lifecycleScope, log, RulesProvider())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GameScreen(game, log.file.absolutePath)
            }
        }
    }
}
