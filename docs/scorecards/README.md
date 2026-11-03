# Scorecards

One file per model, written by `scripts/scorecard.ps1` (phone runs) or `./gradlew :eval:scorecard -PscorecardModel=<id> [-PscorecardPrimary=<id>]` (from saved run files in `eval/results`). Every number is measured from the run files named in the card. Held-out rows are aggregates only.

Current cards:

- `gemma-4-e2b.md`: built from the committed golden runs (assistant development set and held-out set on prompt v3.8, the full English set on the older prompt v3.5). A fresh phone run with `scorecard.ps1` is still to be done.
- `lfm2.5-1.2b-int8.md` and `lfm2.5-1.2b-int4gpu.md`: built from the earlier Phase 0 runs. They used a 38-case assistant set and an older prompt version, so they are **not comparable** with the Gemma rows above and show "not measured" for every row they lack. They demonstrate the tool and the relative swap rule; a fresh run is needed before they mean anything.
