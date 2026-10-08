#!/usr/bin/env python3
"""Count mentions of names per story in the Markdown corpus found in INPUT_DIR."""
import os
import re
import sys


def main() -> int:
    names = sys.argv[1:]
    if not names:
        print("usage: mentions.py NAME [NAME ...]  (corpus files are read from INPUT_DIR)")
        return 2
    input_dir = os.environ.get("INPUT_DIR", ".")
    files = sorted(f for f in os.listdir(input_dir) if f.endswith(".md"))
    if not files:
        print(f"no .md files found in {input_dir}")
        return 1

    patterns = {name: re.compile(r"\b" + re.escape(name) + r"\b", re.IGNORECASE) for name in names}
    totals = {name: 0 for name in names}
    print("story | " + " | ".join(names))
    for file_name in files:
        with open(os.path.join(input_dir, file_name), encoding="utf-8") as f:
            text = f.read()
        parts = re.split(r"^## ", text, flags=re.MULTILINE)
        for part in parts[1:]:
            title, _, body = part.partition("\n")
            counts = {name: len(p.findall(body)) for name, p in patterns.items()}
            for name, count in counts.items():
                totals[name] += count
            print(title.strip() + " | " + " | ".join(str(counts[name]) for name in names))
    print("TOTAL | " + " | ".join(str(totals[name]) for name in names))
    return 0


if __name__ == "__main__":
    sys.exit(main())
