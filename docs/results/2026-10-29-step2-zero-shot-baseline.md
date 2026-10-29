# Step 2: zero-shot decision baseline, mobile-tier models (2026-10-29)

## Setup

- Test set: `benchmarks/cases/v1.jsonl`, 300 cases from `benchmarks/make_cases.py` (seed 20261029). 75 per label (EAT, REST, EXPLORE, FLEE); half numeric needs, half text with varied wording. Each case has one clearly dominant need, so its label is unambiguous. 2-4 options per case, always including the correct one.
- Runner: `benchmarks/run.py`. One `llama-server` per profile (CPU, 4 threads, ctx 512, one slot). Every case is a separate stateless request, temperature 0, option order shuffled (seed 7, identical for every profile), grammar restricted to the offered options. Qwen3 runs with thinking disabled (official empty `<think></think>` form; verified identical to the server's own chat template with `enable_thinking=false`).
- **Chance level: 36.4%** (expected accuracy of picking uniformly among the offered options).
- llama.cpp `b11982`, AMD Ryzen 9 9900X, Windows 11.

## Results

| Profile | Accuracy | Numeric | Text | Valid | Picked first option | Latency ms p50 / p95 | Peak RAM MiB |
|---|---|---|---|---|---|---|---|
| smollm2-135m-q4 | 35.0% | 35% | 35% | 100% | 71% | 49 / 66 | 237 |
| smollm2-135m-q8 | 34.7% | 32% | 37% | 100% | 65% | 54 / 72 | 265 |
| smollm2-135m-f16 | 34.3% | 32% | 36% | 100% | 67% | 59 / 73 | 386 |
| qwen3-0.6b-q4 | 35.7% | 37% | 34% | 100% | 69% | 85 / 108 | 1090 |
| qwen3-0.6b-q8 | 39.0% | 38% | 40% | 100% | 87% | 143 / 195 | 1114 |
| qwen3-0.6b-f16 | 39.0% | 38% | 40% | 100% | 88% | 144 / 195 | 1644 |
| rules (numeric only) | 100% | 100% | n/a | 100% | 39% | ~0 | n/a |

Without the grammar (`--free`): SmolLM2-135M q4 produced 0% valid answers; Qwen3-0.6B q4 was 100% valid with identical answers, so Qwen3 follows the one-word format by itself.

An unbiased correct model would pick the first-listed option about as often as the correct answer happens to be first (39%, see the rules row). Every model is far above that, so position, not the situation, drives most answers.

Peak RAM here is the `llama-server` process (weights + KV cache + compute buffers + server overhead), so it is higher than the `llama-completion` measurement in the Step 1 note. Qwen3's 151k-token vocabulary makes its output buffers much larger than SmolLM2's.

## Findings

1. **Zero-shot, both small models are at chance level** (34-39% vs 36.4%), at every precision. Quantization is not what limits them.
2. **Our Qwen3-0.6B q4_k_m damages the model on some cases.** On three FLEE text cases, f16 and q8_0 answered FLEE, EXPLORE, FLEE (matching Ollama's `qwen3:0.6b`), while our q4_k_m answered EXPLORE for all three. Ollama's Q4_K_M is 522 MB vs our 462 MB, so it likely keeps some tensors at higher precision. Overall accuracy hides this because the model is near chance anyway; recheck quantization after fine-tuning.
3. **Speed and memory fit mobile**: SmolLM2-135M about 50-60 ms per decision; Qwen3-0.6B 85-145 ms on desktop CPU.
4. **The rules baseline is 100% on numeric cases by construction.** It sets the bar: a model is only worth shipping where it handles input that rules cannot (free text, mixed signals) at least as reliably.

## Next

- Fine-tune SmolLM2-135M (and Qwen3-0.6B) on generated decision data, with training cases disjoint from this test set (new seed, new wording), then rerun this benchmark.
- Add a harder test set (mixed and conflicting needs, unseen wording) so the fine-tuned model is not only tested on the easy, template-like cases.
- PC tier: Qwen3 1.7B-14B with a GPU build (Vulkan), plus many-agent throughput.
