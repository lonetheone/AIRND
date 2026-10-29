"""Full fine-tune of a small chat model on the decision data (TRL SFT).

Starts from the downloaded checkpoint and saves a new one; the original is not
modified. Loss is computed on the answer only (prompt-completion data). After
training, greedy accuracy on the validation set is printed.

    .venv-train\\Scripts\\python training\\train.py
    .venv-train\\Scripts\\python training\\train.py --base models/hf/smollm2-135m-instruct --out models/finetuned/smollm2-135m-decide-v1
"""

import argparse
import json
import shutil
import time
from pathlib import Path

import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer
from trl import SFTConfig, SFTTrainer

REPO = Path(__file__).resolve().parent.parent


@torch.no_grad()
def val_accuracy(model, tok, path, limit=600):
    rows = [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines()][:limit]
    tok.padding_side = "left"
    model.eval()
    correct = 0
    for i in range(0, len(rows), 64):
        batch = rows[i:i + 64]
        prompts = [tok.apply_chat_template(r["prompt"], tokenize=False, add_generation_prompt=True)
                   for r in batch]
        enc = tok(prompts, return_tensors="pt", padding=True).to(model.device)
        out = model.generate(**enc, max_new_tokens=4, do_sample=False, pad_token_id=tok.pad_token_id)
        for r, o in zip(batch, out):
            answer = tok.decode(o[enc["input_ids"].shape[1]:], skip_special_tokens=True).strip()
            correct += answer == r["completion"][0]["content"]
    return correct / len(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="models/hf/smollm2-135m-instruct")
    ap.add_argument("--out", default="models/finetuned/smollm2-135m-decide-v1")
    ap.add_argument("--epochs", type=float, default=3)
    ap.add_argument("--lr", type=float, default=5e-5)
    ap.add_argument("--batch", type=int, default=32)
    args = ap.parse_args()

    base, out = REPO / args.base, REPO / args.out
    data = load_dataset("json", data_files={"train": str(REPO / "training/data/train.jsonl"),
                                            "val": str(REPO / "training/data/val.jsonl")})
    tok = AutoTokenizer.from_pretrained(base)
    model = AutoModelForCausalLM.from_pretrained(base, dtype=torch.float32).to("cuda")

    before = val_accuracy(model, tok, REPO / "training/data/val.jsonl", limit=300)
    print(f"val accuracy before training: {before:.1%}")
    tok.padding_side = "right"

    config = SFTConfig(
        output_dir=str(out / "_trainer"),
        num_train_epochs=args.epochs,
        learning_rate=args.lr,
        lr_scheduler_type="cosine",
        warmup_steps=20,
        per_device_train_batch_size=args.batch,
        per_device_eval_batch_size=64,
        bf16=True,
        max_length=256,
        completion_only_loss=True,
        eval_strategy="epoch",
        logging_steps=20,
        save_strategy="no",
        report_to="none",
        seed=42,
    )
    trainer = SFTTrainer(model=model, args=config, train_dataset=data["train"],
                         eval_dataset=data["val"], processing_class=tok)
    start = time.time()
    trainer.train()
    minutes = (time.time() - start) / 60

    trainer.save_model(str(out))
    # Fine-tuning does not change the tokenizer. Copy the base checkpoint's
    # files instead of re-saving: tokenizer.json written by transformers 5.x
    # is not readable by the converter's pinned transformers 4.57.
    for f in base.iterdir():
        if f.name.startswith(("tokenizer", "vocab", "merges", "special_tokens")):
            shutil.copy2(f, out / f.name)
    after = val_accuracy(trainer.model, tok, REPO / "training/data/val.jsonl")
    print(f"training time: {minutes:.1f} min")
    print(f"val accuracy after training: {after:.1%} (before: {before:.1%})")
    print(f"saved: {out}")
    (out / "train_summary.json").write_text(json.dumps({
        "base": args.base, "epochs": args.epochs, "lr": args.lr, "batch": args.batch,
        "train_examples": len(data["train"]), "val_examples": len(data["val"]),
        "val_accuracy_before": before, "val_accuracy_after": after, "minutes": minutes,
        "log": trainer.state.log_history}, indent=1))


if __name__ == "__main__":
    main()
