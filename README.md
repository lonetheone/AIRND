# AIRND

Game AI R&D: tiny local LLMs, llama.cpp C++ inference, and benchmarks, leading to a reusable Game AI SDK for UNSCRIPTED and future games.

## Layout

```
experiments/   model and runtime experiments (smollm2, qwen, tiny-policy)
benchmarks/    CPU, RAM, latency, frame-time and decision-quality measurements
sdk/           reusable C++ library (include/, src/)
examples/      sample integrations (npc-decisions)
models/        model manifest only; GGUF files are not committed
docs/          design notes and results
```

## Stages

1. Research: llama.cpp and tiny models (Windows CPU first)
2. Benchmark: compare against native rules and a small policy baseline
3. SDK: provider-style C++ interface compatible with UNSCRIPTED's `IDecisionProvider`
4. Validate: bounded queue, deadlines, staleness, validation and native fallback
5. Release: version the SDK and model separately
6. Integrate: pinned dependency in games

Reusable lessons go to NEMO (`knowledge/ai-infrastructure/`); source and results stay here.
