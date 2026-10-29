package com.airnd.dda.llm

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Timing of one generation, from the native side. */
data class GenStats(val promptTokens: Long, val genTokens: Long, val promptMs: Double, val genMs: Double) {
    val totalMs get() = promptMs + genMs
    val tokensPerSecond get() = if (genMs > 0) genTokens * 1000.0 / genMs else 0.0
}

/** Process memory from /proc/self/status: current and peak resident set, in MiB. */
data class Memory(val rssMiB: Long, val peakMiB: Long) {
    companion object {
        fun read(): Memory {
            val lines = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
            fun kb(key: String) = lines.firstOrNull { it.startsWith(key) }
                ?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
            return Memory(kb("VmRSS:") / 1024, kb("VmHWM:") / 1024)
        }
    }
}

/**
 * Owns one loaded model. Every native call runs on a single worker thread, so
 * the llama context is only ever touched by that thread; callers suspend.
 * Load once and keep it for the app's life; never call from the UI thread.
 */
class LlmEngine(private val appContext: Context) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "llm-worker") }.asCoroutineDispatcher()
    private var handle = 0L

    val isLoaded get() = handle != 0L
    var loadMs = 0L; private set
    var modelName = ""; private set

    /** Copies the GGUF from APK assets to app storage on first run, then loads it. */
    suspend fun load(assetName: String, threads: Int = 4, nCtx: Int = 512): Boolean = withContext(worker) {
        if (handle != 0L) return@withContext true
        val file = File(appContext.filesDir, assetName)
        val assetSize = appContext.assets.openFd(assetName).use { it.length }
        if (!file.exists() || file.length() != assetSize) {
            appContext.assets.open(assetName).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val start = System.nanoTime()
        handle = NativeLlm.load(file.absolutePath, threads, nCtx)
        loadMs = (System.nanoTime() - start) / 1_000_000
        modelName = assetName
        handle != 0L
    }

    /** Returns text and timing, or null if the model is not loaded or generation failed. */
    suspend fun generate(prompt: String, maxTokens: Int, temperature: Float, seed: Int,
                         grammar: String? = null): Pair<String, GenStats>? = withContext(worker) {
        if (handle == 0L) return@withContext null
        val text = NativeLlm.generate(handle, prompt, maxTokens, temperature, seed, grammar)
            ?: return@withContext null
        val s = NativeLlm.lastStats(handle)
        text to GenStats(s[0], s[1], s[2] / 1000.0, s[3] / 1000.0)
    }
}
