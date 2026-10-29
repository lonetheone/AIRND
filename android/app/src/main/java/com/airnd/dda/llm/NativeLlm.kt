package com.airnd.dda.llm

/** JNI binding to app/src/main/cpp/llm_jni.cpp (registered in JNI_OnLoad). */
object NativeLlm {
    init {
        System.loadLibrary("airnd_llm")
    }

    @JvmStatic external fun load(path: String, threads: Int, nCtx: Int): Long
    @JvmStatic external fun generate(handle: Long, prompt: String, maxTokens: Int,
                                     temperature: Float, seed: Int, grammar: String?): String?
    @JvmStatic external fun lastStats(handle: Long): LongArray
    @JvmStatic external fun release(handle: Long)
}
