"""Scores decision profiles on the labelled test set.

For each profile a llama-server is started (CPU, ctx from the profile). Every
case is sent as its own stateless request at temperature 0, with the option
order shuffled per case (seeded) and a grammar that only allows the offered
actions. Reports accuracy, how often the first-listed option was chosen,
latency and the server's peak RAM.

    .venv\\Scripts\\python benchmarks\\run.py smollm2-135m-q4 qwen3-0.6b-q4 rules
    .venv\\Scripts\\python benchmarks\\run.py qwen3-0.6b-q4 --free     # no grammar

"rules" is a numeric-threshold baseline; it only answers numeric cases.
"""

import argparse
import http.client
import json
import random
import statistics
import subprocess
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path

import psutil

REPO = Path(__file__).resolve().parent.parent
SERVER = REPO / "build" / "llama" / "bin" / "llama-server.exe"
PORT = 8089

SYSTEM = ("You control a character in a survival game. Choose the best action for "
          "the situation. Reply with exactly one word: {options}.")


def build_prompt(family, situation, options):
    system = SYSTEM.format(options=", ".join(options[:-1]) + ", or " + options[-1])
    prompt = (f"<|im_start|>system\n{system}<|im_end|>\n"
              f"<|im_start|>user\n{situation} Which action?<|im_end|>\n"
              f"<|im_start|>assistant\n")
    if family == "qwen3-nothink":
        prompt += "<think>\n\n</think>\n\n"  # Qwen3's documented empty-thinking form
    return prompt


def grammar_for(options):
    return "root ::= " + " | ".join(f'"{o}"' for o in options)


_conn = None


def post(path, body, timeout=60):
    # One keep-alive connection: a new socket per request exhausted Windows
    # socket buffers (WinError 10055) after a few thousand requests.
    global _conn
    if _conn is None:
        _conn = http.client.HTTPConnection("127.0.0.1", PORT, timeout=timeout)
    _conn.request("POST", path, json.dumps(body), {"Content-Type": "application/json"})
    return json.loads(_conn.getresponse().read())


def wait_ready(proc, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if proc.poll() is not None:
            raise RuntimeError(f"llama-server exited with {proc.returncode}")
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=2) as r:
                if r.status == 200:
                    return
        except Exception:
            time.sleep(0.2)
    raise RuntimeError("llama-server did not become ready")


def rules_decide(case, options):
    n = case["needs"]
    if n is None:
        return None
    for action, hit in (("FLEE", n["danger"] >= 0.5), ("EAT", n["hunger"] >= 0.6),
                        ("REST", n["energy"] <= 0.35), ("EXPLORE", True)):
        if hit and action in options:
            return action
    return options[0]


def run_profile(name, profile, cases, free):
    results = []
    peak = 0
    proc = None
    if name != "rules":
        cmd = [str(SERVER), "-m", str(REPO / profile["gguf"]), "-c", str(profile["ctx"]),
               "-t", str(profile["threads"]), "--port", str(PORT), "-np", "1"]
        proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        wait_ready(proc)
        ps = psutil.Process(proc.pid)
    try:
        for c in cases:
            options = c["shuffled"]
            if name == "rules":
                answer, ms = rules_decide(c, options), 0.0
            else:
                body = {"prompt": build_prompt(profile["family"], c["situation"], options),
                        "n_predict": 24 if free else 8, "temperature": 0, "cache_prompt": True}
                if not free:
                    body["grammar"] = grammar_for(options)
                r = post("/completion", body)
                answer = r["content"].strip()
                t = r.get("timings", {})
                ms = t.get("prompt_ms", 0) + t.get("predicted_ms", 0)
                peak = max(peak, ps.memory_info().peak_wset)
            results.append({"id": c["id"], "format": c["format"], "label": c["label"],
                            "options": options, "answer": answer, "ms": ms})
    finally:
        global _conn
        if _conn:
            _conn.close()
            _conn = None
        if proc:
            proc.terminate()
            proc.wait(timeout=10)
    return results, peak


def summarize(name, results, peak, free):
    answered = [r for r in results if r["answer"] is not None]
    valid = [r for r in answered if r["answer"] in r["options"]]
    correct = [r for r in valid if r["answer"] == r["label"]]
    first = [r for r in valid if r["answer"] == r["options"][0]]

    def acc(fmt):
        rs = [r for r in answered if r["format"] == fmt]
        return f"{100 * sum(r['answer'] == r['label'] for r in rs) / len(rs):.0f}%" if rs else "n/a"

    ms = [r["ms"] for r in answered if r["ms"]]
    lat = (f"{statistics.median(ms):.0f} / {sorted(ms)[int(0.95 * (len(ms) - 1))]:.0f}"
           if ms else "n/a")
    per_label = ", ".join(
        f"{lab} {100 * sum(r['answer'] == lab for r in answered if r['label'] == lab) / max(1, sum(r['label'] == lab for r in answered)):.0f}%"
        for lab in ("EAT", "REST", "EXPLORE", "FLEE"))
    return {
        "profile": name + (" (free)" if free else ""),
        "cases": len(answered),
        "accuracy": f"{100 * len(correct) / len(answered):.1f}%",
        "numeric": acc("numeric"),
        "text": acc("text"),
        "valid": f"{100 * len(valid) / len(answered):.0f}%",
        "first_option": f"{100 * len(first) / max(1, len(valid)):.0f}%",
        "latency_ms_p50_p95": lat,
        "peak_ram_mib": round(peak / 2**20) if peak else "n/a",
        "per_label": per_label,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("profiles", nargs="+")
    ap.add_argument("--cases", default=str(REPO / "benchmarks" / "cases" / "v1.jsonl"))
    ap.add_argument("--free", action="store_true", help="no grammar: test raw format following")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    profiles = json.loads((REPO / "benchmarks" / "profiles.json").read_text())["profiles"]
    cases = [json.loads(line) for line in Path(args.cases).read_text(encoding="utf-8").splitlines()]
    if args.limit:
        cases = cases[:args.limit]
    rng = random.Random(7)  # same shuffled order for every profile
    for c in cases:
        c["shuffled"] = rng.sample(c["options"], len(c["options"]))

    # Chance level: the expected accuracy of picking uniformly among offered options.
    chance = 100 * statistics.mean(1 / len(c["options"]) for c in cases)
    print(f"{len(cases)} cases, chance level {chance:.1f}%")

    out_dir = REPO / "benchmarks" / "results" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    rows = []
    for name in args.profiles:
        if name != "rules" and name not in profiles:
            sys.exit(f"unknown profile {name}")
        results, peak = run_profile(name, profiles.get(name), cases, args.free)
        row = summarize(name, results, peak, args.free)
        rows.append(row)
        (out_dir / f"{stamp}-{name}{'-free' if args.free else ''}.json").write_text(
            json.dumps({"summary": row, "results": results}, indent=1))
        print(json.dumps(row))

    cols = ["profile", "accuracy", "numeric", "text", "valid", "first_option",
            "latency_ms_p50_p95", "peak_ram_mib"]
    print("\n| " + " | ".join(cols) + " |\n|" + "---|" * len(cols))
    for r in rows:
        print("| " + " | ".join(str(r[c]) for c in cols) + " |")
    print()
    for r in rows:
        print(f"{r['profile']}: {r['per_label']}")


if __name__ == "__main__":
    main()
