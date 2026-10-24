# Golden outputs

Saved model answers from phone runs (one Moto g86 power, GPU and CPU), kept so the offline quality gate
(`./gradlew :eval:gate`) can rescore them with the current parser, scorers, datasets and thresholds on every push.

| File | What it is |
| :-- | :-- |
| `full-english-v35-*.json` | The full English set (101 commands, 200 notifications, 40 injection attempts) with the v3.5 line prompt, measured 2026-10-24 |
| `assistant-line2-*.json` | The 38 hand-written assistant commands with prompt line2 (proposer-v3.7-line), measured 2026-10-24 |

The gate checks the code and the data, not the model: a change to the parser, a scorer, a dataset's expected answer or a
threshold changes the result. The model's own behaviour is only re-measured by `scripts/bench-release.ps1` on the phone,
which writes new files; copy them here in a commit that says why. Do not edit these files by hand.

`../gate-known-failing.json` lists the checks that fail today, each with its reason. The gate fails if a check fails that is not
listed, and also if a listed one starts passing (remove it then).
