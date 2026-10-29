package com.airnd.dda.llm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the debug panel shows about the on-device model. Set by MainActivity. */
object LlmStatus {
    var state by mutableStateOf("llm: not loaded")
}
