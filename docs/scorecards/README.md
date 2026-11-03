# Scorecards

One file per model, written by `scripts/scorecard.ps1` (phone runs, then the card) or by `./gradlew :eval:scorecard` from saved run files:

```
./gradlew :eval:scorecard -PscorecardModel=<id> [-PscorecardPrimary=<id>] [-PscorecardResults=<folder>] [-PscorecardDevice=<exact device label>] [-PscorecardPrimaryDevice=<exact device label>]
```

Every row names its prompt version and device label. Two runs for one set and backend are an error (name one batch with its exact device label). A card whose rows use different prompt versions says so at the top. The swap rule marks a candidate "not comparable" when a run is incomplete, covers other cases, or used another prompt version. Held-out rows are aggregates only.

Current cards and how they were made:

- `gemma-4-e2b.md`: a fresh phone run on 2026-11-03 (device label `scorecard-20261103`, shipped prompt v3.8, audio encoder loaded as the app does) with `scripts/scorecard.ps1`, scored against the committed v3.8 goldens (`-PscorecardPrimaryDevice=v38`). Six runs: assistant development set, held-out set and full English set, on GPU and CPU. The raw run files are local (`eval/results`, not committed). Read the English rows with the note in the Stage 12 evidence: they expect payment actions that v0.01 refuses.
- `lfm2.5-1.2b-int8.md` (CPU build) and `lfm2.5-1.2b-int4gpu.md` (GPU build): fresh phone runs on 2026-11-03 with `scripts/scorecard.ps1 -AudioEngine off`, same device label, prompt and sets as Gemma, compared with `-PscorecardPrimary=gemma-4-e2b -PscorecardPrimaryDevice=v38`. Both fail the swap rule on every row with the shipped prompt (see the Stage 12 evidence).
