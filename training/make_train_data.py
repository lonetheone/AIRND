"""Generates fine-tuning data for the decision task (seeded, reproducible).

Text situations are composed from phrase pools that do not reuse the wording of
the benchmark test set (benchmarks/make_cases.py); the script fails if any
generated situation equals a test situation. Numeric situations use the same
dominant-need ranges as the test set, with a different seed and two extra
number formats. Options are 2-4 actions in random order, always including the
correct one, so position carries no information.

    python training/make_train_data.py     # writes training/data/{train,val}.jsonl
"""

import json
import random
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "benchmarks"))
from decision_prompt import system_text, user_text  # noqa: E402

ACTIONS = ["EAT", "REST", "EXPLORE", "FLEE"]
RANGES = {
    "FLEE":    ((0.0, 1.0), (0.0, 1.0), (0.8, 1.0)),
    "EAT":     ((0.8, 1.0), (0.4, 1.0), (0.0, 0.2)),
    "REST":    ((0.0, 0.3), (0.0, 0.2), (0.0, 0.2)),
    "EXPLORE": ((0.0, 0.3), (0.7, 1.0), (0.0, 0.2)),
}

SUBJECTS = ["The character", "The hero", "The scout", "The villager", "The traveller",
            "The survivor", "Your NPC", "The hunter"]
DANGER = ["a pack of hyenas is closing in", "an armed raider is sprinting toward it",
          "the cave is collapsing around it", "a giant spider drops from the ceiling",
          "enemy archers have spotted it and are drawing their bows",
          "the river is flooding the camp", "a wild boar is charging at it",
          "zombies are breaking through the fence", "a dragon is diving toward it",
          "thieves with knives are cornering it", "a forest fire is spreading toward it",
          "a crocodile lunges out of the water", "a troll is smashing through the trees",
          "poison gas is filling the room"]
HUNGRY = ["has not eaten since yesterday", "is weak with hunger", "is famished",
          "has stomach cramps from hunger", "is desperately hungry",
          "has gone days without food", "can think of nothing but food"]
FOOD = ["there are apples on a nearby tree", "a meal is waiting at the inn",
        "its pack holds dried meat", "berries grow beside the path",
        "a pot of soup is simmering", "the larder is full", "a merchant is selling hot pies"]
TIRED = ["is drained of energy", "can hardly keep its eyes open",
         "is ready to collapse from fatigue", "has not slept in two days", "is dead tired",
         "feels its whole body begging for sleep", "keeps nodding off"]
FED = ["has eaten well", "just finished a large meal", "is not hungry",
       "had a filling dinner", "is still full from lunch"]
RESTED = ["slept soundly last night", "is bursting with energy", "feels fresh and alert",
          "woke up rested", "has plenty of stamina"]
SAFE = ["the area is quiet", "no threats are around", "the valley is peaceful",
        "the village walls keep it safe", "the guards are on watch"]
CURIOUS = ["an unmapped forest lies to the east", "it is bored of sitting around",
           "strange lights glow on the hill", "there is an old tower nobody has entered",
           "it wonders what is beyond the river", "nothing needs doing right now",
           "a traveller mentioned a hidden lake nearby"]


def text_situation(rng, label):
    s = rng.choice(SUBJECTS)
    if label == "FLEE":
        other = rng.choice([None, "HUNGRY", "TIRED"])
        d = rng.choice(DANGER)
        if other == "HUNGRY":
            return f"{s} {rng.choice(HUNGRY)}, but {d}."
        if other == "TIRED":
            return f"{s} {rng.choice(TIRED)}, but {d}."
        return f"{d[0].upper()}{d[1:]}, and {s.lower() if s.startswith('The') else s} has nowhere to hide."
    if label == "EAT":
        extra = rng.choice(["", f" It {rng.choice(RESTED)}.", f" {rng.choice(SAFE).capitalize()}."])
        return f"{s} {rng.choice(HUNGRY)} and {rng.choice(FOOD)}.{extra}"
    if label == "REST":
        extra = rng.choice(["", f" {rng.choice(SAFE).capitalize()}."])
        return f"{s} {rng.choice(TIRED)}; it {rng.choice(FED)}.{extra}"
    return f"{s} {rng.choice(FED)} and {rng.choice(RESTED)}; {rng.choice(CURIOUS)}."


def numeric_situation(rng, label):
    h, e, d = (round(rng.uniform(*r), 2) for r in RANGES[label])
    style = rng.randrange(3)
    if style == 0:
        return f"Hunger {h:.2f}, energy {e:.2f}, danger {d:.2f} (0 = none, 1 = extreme)."
    if style == 1:
        return f"Hunger {round(h * 100)}%, energy {round(e * 100)}%, danger {round(d * 100)}%."
    return f"Stats: hunger={h:.2f} energy={e:.2f} danger={d:.2f} (scale 0-1)."


def example(rng, label, fmt):
    situation = numeric_situation(rng, label) if fmt == "numeric" else text_situation(rng, label)
    options = [label] + rng.sample([a for a in ACTIONS if a != label], rng.choice([1, 2, 3]))
    rng.shuffle(options)
    return situation, {
        "prompt": [{"role": "system", "content": system_text(options)},
                   {"role": "user", "content": user_text(situation)}],
        "completion": [{"role": "assistant", "content": label}],
    }


def main():
    rng = random.Random(4242)  # different from the test set seed
    per_label = 1500
    test_situations = {json.loads(line)["situation"] for line in
                       (REPO / "benchmarks" / "cases" / "v1.jsonl").read_text(encoding="utf-8").splitlines()}

    rows, texts, numeric_overlap = [], set(), 0
    for label in ACTIONS:
        for i in range(per_label):
            situation, row = example(rng, label, "numeric" if i % 2 == 0 else "text")
            if situation in test_situations:
                if not situation.startswith("Hunger "):
                    sys.exit(f"training text equals a test case: {situation}")
                numeric_overlap += 1  # same random numbers by chance; reported below
            texts.add(situation)
            rows.append(row)
    rng.shuffle(rows)
    n_val = len(rows) // 10
    out = REPO / "training" / "data"
    out.mkdir(parents=True, exist_ok=True)
    for name, part in (("val", rows[:n_val]), ("train", rows[n_val:])):
        with (out / f"{name}.jsonl").open("w", encoding="utf-8", newline="\n") as f:
            for r in part:
                f.write(json.dumps(r) + "\n")
    print(f"{len(rows)} examples ({len(rows) - n_val} train, {n_val} val), "
          f"{len(texts)} distinct situations, {numeric_overlap} numeric equal to a test case")


if __name__ == "__main__":
    main()
