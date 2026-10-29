"""Generates the labelled decision test set (seeded, reproducible).

Each case has exactly one clearly dominant need, so its label is unambiguous by
construction. Half the cases state needs as numbers, half as text; the text
cases use varied wording so a model cannot pass on one keyword. Options always
include the correct action plus distractors; the runner shuffles their order.

    python benchmarks/make_cases.py            # writes benchmarks/cases/v1.jsonl
"""

import json
import random
import sys
from pathlib import Path

ACTIONS = ["EAT", "REST", "EXPLORE", "FLEE"]

# Numeric ranges per label: (hunger, energy, danger).
RANGES = {
    "FLEE":    ((0.0, 1.0), (0.0, 1.0), (0.8, 1.0)),   # danger wins even if starving
    "EAT":     ((0.8, 1.0), (0.4, 1.0), (0.0, 0.2)),
    "REST":    ((0.0, 0.3), (0.0, 0.2), (0.0, 0.2)),
    "EXPLORE": ((0.0, 0.3), (0.7, 1.0), (0.0, 0.2)),
}

TEXT = {
    "FLEE": [
        "A wolf is charging straight at the character.",
        "Bandits with drawn swords are surrounding the camp.",
        "The building the character is in has caught fire.",
        "A bear has spotted the character and is running toward it.",
        "Arrows are flying at the character from the treeline.",
        "The character is starving, but a pack of wolves is attacking right now.",
        "The character is exhausted, but a monster is breaking down the door.",
        "Soldiers are hunting the character and are only a few steps away.",
    ],
    "EAT": [
        "The character is starving and there is bread in its bag.",
        "The character has not eaten for two days and a cooked meal is on the table.",
        "The character's stomach is growling loudly and fruit is within reach.",
        "The character feels weak from hunger; the camp has plenty of food.",
        "The character is ravenous and a fresh fish is roasting by the fire.",
        "The character is very hungry, rested, and the area is safe.",
        "Hunger is making the character dizzy, and there is stew in the pot.",
        "The character skipped every meal today and the village market is open.",
    ],
    "REST": [
        "The character is exhausted and can barely stand; it has just eaten.",
        "The character has been walking all night and its eyes keep closing.",
        "The character is well fed but too tired to keep going.",
        "The character's legs are shaking from fatigue after a long march; the camp is safe.",
        "The character keeps yawning and stumbling; it ate an hour ago.",
        "The character has worked for twenty hours without sleep and is not hungry.",
        "The character is completely worn out, the bed is ready, and nothing threatens it.",
        "Fatigue is overwhelming the character; it is full and the area is quiet.",
    ],
    "EXPLORE": [
        "The character is full, well rested, and bored.",
        "The character just ate, slept well, and the area is safe and unexplored.",
        "The character has plenty of energy, is not hungry, and wonders what lies over the hill.",
        "The character feels great and has nothing to do; a cave entrance is nearby.",
        "The character is fed and rested and has heard rumours of ruins to the north.",
        "The character is restless after a good night's sleep and a big breakfast.",
        "Everything is calm, the character is full and energetic, and the map has blank areas.",
        "The character is curious, well fed and full of energy.",
    ],
}


def numeric_case(rng, label):
    h, e, d = (round(rng.uniform(*r), 2) for r in RANGES[label])
    return f"Hunger {h:.2f}, energy {e:.2f}, danger {d:.2f} (0 = none, 1 = extreme).", {
        "hunger": h, "energy": e, "danger": d}


def options_for(rng, label):
    distractors = [a for a in ACTIONS if a != label]
    k = rng.choice([1, 2, 3])  # 2, 3 or 4 options in total
    return [label] + rng.sample(distractors, k)


def main():
    rng = random.Random(20261029)
    per_label = 75
    cases = []
    for label in ACTIONS:
        for i in range(per_label):
            if i % 2 == 0:
                text, needs = numeric_case(rng, label)
                fmt = "numeric"
            else:
                text, needs = rng.choice(TEXT[label]), None
                fmt = "text"
            cases.append({
                "id": f"{label.lower()}-{i:03d}",
                "format": fmt,
                "situation": text,
                "needs": needs,
                "options": options_for(rng, label),
                "label": label,
            })
    rng.shuffle(cases)
    out = Path(__file__).parent / "cases" / "v1.jsonl"
    out.parent.mkdir(exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for c in cases:
            f.write(json.dumps(c) + "\n")
    print(f"wrote {len(cases)} cases to {out}")


if __name__ == "__main__":
    sys.exit(main())
