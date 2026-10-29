# Step 1: SmolLM2-135M-Instruct smoke test (2026-10-29)

## Setup

- llama.cpp `b11982` (`8a199ad3`), CPU only, MSVC (Visual Studio 2026) + Ninja, Release, built with `scripts/build-llama.ps1`
- Model: `HuggingFaceTB/SmolLM2-135M-Instruct` @ `12fd25f7`, Apache-2.0, converted locally (`scripts/get-model.ps1`); hashes in `models/manifest.json`
- Machine: AMD Ryzen 9 9900X (12 cores), 62 GB RAM, Windows 11
- Prompt: ChatML system + user message, greedy sampling (`--temp 0`), `llama-completion -no-cnv`

| File | Size |
|---|---|
| f16 | 258 MB |
| q8_0 | 138 MB |
| q4_k_m | 101 MB |

## Speed (`llama-bench`, CPU)

| Quant | Threads | Prompt 64 tok (t/s) | Generate 16 tok (t/s) |
|---|---:|---:|---:|
| q4_k_m | 2 | 1042 ± 44 | 230 ± 5 |
| q4_k_m | 4 | 1590 ± 163 | 274 ± 44 |
| q8_0 | 2 | 1069 ± 21 | 209 ± 23 |
| q8_0 | 4 | 1715 ± 56 | 238 ± 16 |

Single run of `llama-completion` (q4_k_m, 4 threads): model load 95 ms, 65-token prompt 49 ms, 4 generated tokens 17 ms. One short decision costs about 60-70 ms of CPU on this desktop.

## Memory (peak working set of `llama-completion`, 4 threads)

| Quant | File | Peak RAM, ctx 8192 (default) | Peak RAM, ctx 512 |
|---|---:|---:|---:|
| f16 | 258 MB | 473 MiB | 304 MiB |
| q8_0 | 138 MB | 353 MiB | 183 MiB |
| q4_k_m | 101 MB | 324 MiB | 155 MiB |

The difference between the two contexts is the KV cache: 30 layers x 192 K/V width x 2 (K and V) x 2 bytes (f16) = 22.5 KiB per token, so 180 MiB at 8192 and 11 MiB at 512. The computed 169 MiB difference matches the measured q4_k_m gap (324 - 155) exactly. A game decision needs well under 512 tokens, so ctx 512 is the realistic figure: about 155 MiB for q4_k_m, of which about 99 MiB is weights.

The q4_k_m file averages 6.17 bits per weight, not about 4.5: the 576-wide rows are not a multiple of the 256-element K-quant block, so llama-quantize keeps some tensors (e.g. `token_embd`) at q8_0.

## Decision quality

**Free generation:** 0 / 9 valid. At f16, q8_0 and q4_k_m the model starts with preamble ("To answer your question, I would say...") instead of the one requested word. Quantization is not the cause.

**Grammar-constrained** (`root ::= "EAT" | "REST" | "EXPLORE"`): 100% valid output, but 2 / 6 correct at both f16 and q4_k_m. It answered EAT every time, which was the first option listed. With the option order changed to "REST, EXPLORE, or EAT", the answers changed (REST, EAT, REST) and all 3 were wrong.

| Case | Expected | Order EAT/REST/EXPLORE | Order REST/EXPLORE/EAT |
|---|---|---|---|
| starving, food nearby | EAT | EAT | REST |
| exhausted and sleepy | REST | EAT | EAT |
| full, rested, bored | EXPLORE | EAT | REST |

## Conclusions

1. The toolchain works end to end on Windows: pinned build, local conversion, quantization, inference.
2. Speed is not the problem for a 135M model on desktop CPU.
3. A grammar is required for valid output; without it the model ignores the format.
4. Zero-shot SmolLM2-135M has no usable judgement for this task. Its choices follow option position, not the situation.

## Next

- Benchmark harness with a fixed labelled case set (about 100 cases), randomized option order, and accuracy per model.
- Compare: Qwen3-0.6B (zero-shot + grammar), SmolLM2-135M with few-shot examples, scoring options by log-probability instead of generating, and a rules / small-policy baseline.
- Measure peak RAM and per-decision latency from a C++ harness linked to `llama`, not the CLI.
- Fine-tuning a tiny model on game decisions is a candidate if larger zero-shot models are too slow for mobile.
