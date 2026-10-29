# Step 3: first fine-tune, SmolLM2-135M decide-v1 (2026-10-29)

## Setup

- Base checkpoint: `HuggingFaceTB/SmolLM2-135M-Instruct` @ `12fd25f7` (Apache-2.0). Full fine-tune, all weights.
- Data: `training/make_train_data.py` (seed 4242). 6,000 examples (5,400 train, 600 validation), 1,500 per action, half numeric (three number formats), half text composed from phrase pools that do not reuse the test-set wording. 2-4 options per example in random order. The script fails if any text situation equals a test case; one numeric situation matched a test case by chance.
- Training: `training/train.py` (TRL 1.16 SFT, prompt-completion so loss is on the answer only). 3 epochs, lr 5e-5 cosine, batch 32, bf16, max length 256, seed 42.
- Environment: `.venv-train` with torch 2.11.0+cu128, transformers 5.21.1, trl 1.16.0, datasets 5.1.0, accelerate 1.15.0. RTX 4080 SUPER (16 GB).
- Export: `scripts/get-model.ps1 -Name smollm2-135m-decide-v1` (manifest `source.local`), converted with llama.cpp `b11982`.

## Training

| Epoch | Train loss (end) | Validation loss |
|---|---|---|
| 1 | 0.012 | 0.0038 |
| 2 | 0.00006 | 0.00036 |
| 3 | 0.00004 | 0.00041 |

Time 1.8 min. Greedy validation accuracy without a grammar: 0.3% before (the base model does not answer with one word), 99.8% after. Validation shares phrase pools with training, so it only shows that training worked, not that the model generalizes. Validation loss flattened after epoch 2; 2 epochs would likely be enough.

## Benchmark (`benchmarks/cases/v1.jsonl`, 300 cases with wording not used in training, chance 36.4%)

| Profile | Accuracy | Numeric | Text | First option | Latency ms p50 / p95 | Peak RAM MiB |
|---|---|---|---|---|---|---|
| decide-v1 q4_k_m | **96.7%** | 100% | 93% | 39% | 38 / 53 | 233 |
| decide-v1 q8_0 | 97.7% | 100% | 95% | 40% | 39 / 56 | 261 |
| decide-v1 f16 | 97.7% | 100% | 95% | 40% | 41 / 55 | 381 |
| base SmolLM2-135M q4_k_m | 35.0% | 35% | 35% | 71% | 49 / 67 | 237 |

Per action (q4_k_m): EAT 88%, REST 99%, EXPLORE 100%, FLEE 100%. Without the grammar (`--free`) the q4_k_m model is 100% valid with identical accuracy, so it now follows the one-word format on its own.

The first-option rate fell from 71% to 39%, the rate an unbiased correct model would show on this set. q4_k_m costs one percentage point against q8_0/f16.

## Errors (q4_k_m, 10 of 300)

- 9 are one test sentence in different option orders: "The character is very hungry, rested, and the area is safe." (label EAT; answered EXPLORE or REST). Every EAT training example mentioned available food, and "rested"/"safe" occurred mainly in EXPLORE and REST examples. The model learned the shortcut "food mentioned means EAT" rather than "hunger means EAT".
- 1: "The character has been walking all night and its eyes keep closing." (REST) answered FLEE with options FLEE, REST. That wording does not appear in the training pools.

## Conclusions

1. Fine-tuning works: a 101 MB model went from chance to 96.7% on unseen wording, in under 2 minutes of training, at about 40 ms and about 233 MiB per decision on desktop CPU.
2. The failures are data-coverage gaps (shortcut learning), not model capacity: one phrase pattern accounts for 9 of 10 errors.
3. The v1 test set is too easy and too repetitive to rank further improvements. It reuses 8 sentences per action, so one bad sentence costs about 3%.

## Next

- Training data v2: hunger with no food mentioned, "rested"/"safe" in EAT and FLEE examples too, more fatigue wording, and conflicting needs with an explicit priority rule.
- Test set v2-hard: new sentence pools written separately from training, conflicting needs, and new subjects and settings.
- Measure on a phone (Android, arm64) before choosing q4_k_m vs q8_0.
