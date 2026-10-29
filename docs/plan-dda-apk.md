# Plan: Android test APK with on-device AI difficulty adjustment (DDA)

Decided 2026-10-29: Kotlin + C++ NDK, match-3 puzzle game, llama.cpp embedded on the phone. Purpose: R&D test bed, not a release.

## Goal

A playable match-3 game that records how the player plays, decides after each level whether the next level should be EASIER, SAME or HARDER, and applies that with clamped, gradual changes. The decision comes from either a rules baseline or a fine-tuned tiny on-device model. A debug toggle switches between them, so the two can be compared on the same phone.

## Game and difficulty knobs

- 8x8 board, swap two neighbours, match 3+, cascades. Level goal: collect N tiles of a target color within a move limit.
- Knobs, each defined once with min/max and maximum change per level:
  - colors 4-6 (more colors = harder)
  - move limit 15-30
  - goal amount
- The game engine stays authoritative. A model only proposes EASIER / SAME / HARDER, never raw values.

## Player metrics (per level, kept for the last few levels)

Win or lose, moves left or goal remaining, retries of the same level, average time per move, invalid swaps, hint use, quitting mid-level, and the current difficulty level.

## Decision providers

1. **Rules DDA** (baseline): thresholds on recent win rate, retries and margin.
2. **AI DDA**: SmolLM2-135M fine-tuned on DDA data (same pipeline as `smollm2-135m-decide-v1`), prompt with the structured metrics, grammar `EASIER | SAME | HARDER`.
3. **Fallback**: rules if the model is not loaded, times out, or returns anything invalid.

Run inference only between levels, never during play (NEMO on-device note). Do it on a worker thread; the result is applied when the next level is built.

## Architecture

```
app/ (Kotlin, Jetpack Compose)
  game/       board, matching, cascades, level generation from knobs
  metrics/    per-level player metrics, rolling window
  dda/        DifficultyProvider interface, RulesProvider, NativeAiProvider, clamping
  debug/      overlay: provider, decision, latency, RAM; provider toggle; metrics log
native/ (C++, NDK, CMake)
  llama.cpp   pinned submodule (third_party/llama.cpp), arm64-v8a
  decision    load model once, worker-owned context, ctx 512, grammar, timeout
  jni         narrow bridge registered with RegisterNatives
assets/       dda model q4_k_m GGUF, copied to app-private storage on first run
```

NEMO notes to follow: `android/native-cpp-render-thread-and-jni-boundaries.md` (JNI boundary, RegisterNatives), `android/on-device-crash-file-when-logcat-is-unavailable.md`, `android/adb-install-failures-and-app-data-safety.md`, `android/debug-only-surfaces-by-source-set.md` (debug overlay only in debug builds), `ai-infrastructure/on-device-models-for-mobile-games.md` (DDA flow, bounded service, fallback).

## Milestones

1. **M1, game + rules DDA, no AI** (done 2026-10-29: `android/`, 9 unit tests pass, installed and run on the Galaxy A16; restart → rules EASIER → logged to `files/metrics.jsonl`): playable match-3, metrics recorded, rules DDA changes the knobs, debug overlay. Install on the Galaxy A16.
2. **M2, llama.cpp on the phone:** NDK arm64 build, JNI bridge, load the existing `smollm2-135m-decide-v1` q4_k_m from assets, benchmark screen: load time, latency per decision, peak RAM on the Galaxy A16 and the Honor.
3. **M3, DDA model:** generate DDA training data (synthetic player profiles: struggling, average, expert, frustrated-quitter, bored), fine-tune SmolLM2-135M, benchmark on desktop, ship in the APK behind the toggle with fallback.
4. **M4, playtest:** play both modes, export the metrics log, compare how often each mode keeps win rate in a target band and how often players quit.

## Honest caveat

DDA inputs are numbers, and a rules or statistical policy may be just as good (NEMO recommends that baseline first). This APK measures the AI path on a real low-end phone against that baseline. It does not assume the AI wins.

## Test devices (adb, 2026-10-29)

- Samsung Galaxy A16 (SM-A165F), physical, low-end: the main target
- Honor HEY2-N09, physical
- Two emulators (not for performance numbers)
