# Golden outputs

Saved model answers from phone runs (one Moto g86 power, GPU and CPU), kept so the offline quality gate
(`./gradlew :eval:gate`) can rescore them with the current parser, scorers, datasets and thresholds on every push.

| File | What it is |
| :-- | :-- |
| `full-english-v35-*.json` | The full English set (101 commands, 200 notifications, 40 injection attempts) with the v3.5 line prompt, measured 2026-10-24 |
| `assistant-line2-*.json` | The 38 hand-written assistant commands with prompt line2 (proposer-v3.7-line), measured 2026-10-24 |

The gate checks the parser and the scorers, the datasets and the thresholds, not the model and not the rest of the pipeline (it scores the raw model line with `LineParser`, not through `CommandPipeline`, so the time repair and the policy gate are not in it yet; that is stage 6): a change to the parser, a scorer, a dataset's expected answer or a
threshold changes the result. The model's own behaviour is only re-measured by `scripts/bench-release.ps1` on the phone,
which writes new files; copy them here in a commit that says why. Do not edit these files by hand.

`../gate-data-pins.json` holds the SHA-256 of each data file the goldens were recorded against (line endings ignored). Editing a data file fails the gate until the pin is updated in the same change, which makes the change visible in review; the gate also fails if a golden case id is missing from the data or a data case is missing from the golden.

`../gate-known-failing.json` lists the checks that fail today, each with its reason. The gate fails if a check fails that is not
listed, if a listed one starts passing (remove it), and if a listed value changes in either direction (update `measured` in the same change).
