---
name: holmes-stats
description: Exact statistics about the Sherlock Holmes corpus, such as how many times a character is mentioned in each story. Runs a Python script in a sandbox instead of guessing.
license: Apache-2.0
compatibility: Requires Python 3
---

# Holmes statistics

Use the `mentions` script whenever a user asks for counts over the stories.
Never estimate counts yourself.

- Pass each name to count as a separate entry in `args`, for example `["Watson", "Lestrade"]`.
- Pass the corpus as an input file: `data/sherlock/adventures-of-sherlock-holmes.md`.
- The script prints one line per story with the count of each name, then a total line.

Report the numbers exactly as printed.
