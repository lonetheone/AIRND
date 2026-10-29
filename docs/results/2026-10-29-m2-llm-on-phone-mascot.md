# M2: llama.cpp on the phone + Pip mascot (2026-10-29)

## Setup

- APK `android/` versionName 0.2-m2. Kotlin + Compose; native `libairnd_llm.so` (6.0 MB) = pinned llama.cpp `b11982` + JNI bridge (`app/src/main/cpp/llm_jni.cpp`, `RegisterNatives`, one worker thread owns the context).
- Native build: NDK 29.0.14206865, CMake 3.31.6, arm64-v8a, Release even in debug APKs, `GGML_NATIVE/OPENMP/LLAMAFILE/LLAMA_OPENSSL=OFF` (upstream docs/android.md), `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`. Both test phones report `asimddp` and `asimdhp`, no i8mm/SVE.
- Model: base `smollm2-135m-instruct-q4_k_m.gguf` (100.6 MB), stored uncompressed in the APK (`noCompress gguf`), copied to app storage on first run. APK 129.8 MB. ctx 512, 4 threads.
- Mascot: prompt with the level facts (result, try number, next difficulty, flow zone); temperature 0.7, top-k 40, top-p 0.9, repeat penalty 1.15, max 32 tokens; 4 s timeout; output cleaned and checked (`MascotText.clean`), canned line on failure.
- Difficulty stays on rules + skill estimate.

## Measured on Samsung Galaxy A16 (MediaTek Helio G99 MT6789, 3.7 GB RAM, Android 16)

| Measure | Value |
|---|---|
| Model load (after first-run copy) | 483 ms |
| RSS added by load | +218 MiB |
| App RSS with model loaded | 334-357 MiB (peak 360 MiB) |
| Prompt (99 tokens) | 771-780 ms (about 127 tok/s) |
| Generation | 20.9-28.3 tok/s |
| Comment end to end | 1.15-1.91 s (8-32 tokens) |

Six level ends were triggered with Restart; all six comments came from the model (no fallback).

## Comment quality (base model, zero-shot)

| Pip said | Problem |
|---|---|
| "Dear Pip, the level is much easier this time." | Talks to itself; claims "easier" after a restart |
| "Pip gave up and restarted the level." | Narrates in third person, describes Pip as the player |
| "Pip: "Try again, try again!" | Speaker prefix and quote left in |
| "Oh, what a fun level!" | Fine but generic |
| "Pip: "Easy! Try again." | Prefix; "easy" is wrong for a quit |
| "Oh no, I wasn't done yet!" | Speaks as the player |

The pipeline works; the base 135M model does not reliably take the mascot role, which matches the zero-shot decision results. It needs fine-tuning, as decide-v1 did.

## Conclusions

1. llama.cpp runs in the APK on a 3.7 GB budget phone: half-second load, about 220 MiB extra RAM, 20-28 tokens/s.
2. About 0.8 s of each 1.2-1.9 s comment is reading the 99-token prompt, most of it the fixed system prompt. Reusing its KV cache across calls, or a fine-tuned model with a much shorter prompt, should cut this sharply.
3. A comment between levels is fine at 1-2 s (the dialog is already usable); it would not be fine during play.

## Next (M3)

- Fine-tune a mascot model: training data of (level facts → Pip line), written by a larger teacher model (e.g. `qwen3:14b` in Ollama) and filtered, plus held-out facts for evaluation. Short prompt.
- Strip "Pip:" prefixes and quotes in `MascotText.clean`; reject third-person "Pip" lines.
- Cache the system prompt; measure on the Honor (Snapdragon 6 Gen 1) as well.
