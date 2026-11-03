# Stage 12 evidence: the model contract and the swap scorecard

Date 2026-11-03. JVM results unless marked. Numbers are measured from saved run files (one Moto g86 power, Android 16) or are targets.

## What was built

- **Capabilities.** `Capability` (`TEXT`, `AUDIO_IN`); `LlmEngine.capabilities` (default text only); `GenerationRequest.requires()`; `GuardedEngine`, which refuses an unsupported request with `UnsupportedCapability` ("this model cannot hear audio") before any native call. The model host hands out the guarded engine. `LiteRtLmEngine` reports `AUDIO_IN` only when the audio encoder actually loaded.
- **Model profile as data.** `models.json` entries have a `profile`: answer format, planner prompt id, the zone label written after the time, capabilities, thinking mode. The registry parser validates it (unknown capability, missing `TEXT`, empty ids are refused). The host passes the profile's thinking mode at load. `ModelSelection.pick` chooses a model by role and preference.
- **Prompt and parser by profile.** `PromptCatalog` (prompt id to a spec with a version), `AnswerParser` and `AnswerFormats` (the line format), `ModelHarness` (builds the planner, parser and speech path from a profile; unknown names fail at wiring), `CommandPipeline` takes a parser. The planner prompt text is unchanged, and the zone label " (IST)" is now written by the Gemma profile rather than by the harness, so the bytes sent to Gemma are identical (the pinned prompt hash test is untouched).
- **Speech by capability.** `GemmaAudioSpeech` is now `LlmAudioSpeech` (not Gemma-specific); a model without `AUDIO_IN` gets `NoAudioSpeech`, so voice says "this model cannot hear audio" in plain words.
- **`AppGraph` by role and profile**: no model id constant; the LiteRT-LM engine is built with audio only if the profile declares it.
- **Scorecard and swap rule** (`eval`): `ScorecardBuilder` (one row per set and backend: action with code, model alone, args, JSON validity, load, first token p50 and p95, decode speed, peak memory, battery, injection leak), `SwapRule` (at most 2 points less accuracy on action, model alone and args, and first token p95 at most 1.25 times the primary's; both are targets in `thresholds.json`), `ScorecardCli`, the Gradle task `:eval:scorecard`, and `scripts/scorecard.ps1` for the phone runs. Held-out rows are aggregates only.
- Tests: `CapabilityTest`, host tests (guarded engine, profile thinking), registry and profile tests including the shipped `models.json`, `SwapTest` (two fake engines with different habits, different prompts and zone labels, through one gate, plus voice for a model that cannot hear), `PlannerPromptTest` catalog tests, `ScorecardTest`, and an `AppInvariantsTest` check that no model name appears in `agent-core`, `models` or the app code.

## Measured

| Check | Result |
| :-- | :-- |
| Quality gate (`:eval:gate`) after the refactor | Unchanged and green: dev action 97.0% with code and 92.0% / 93.0% model alone (GPU / CPU), held-out 100.0% with code and 93.3% model alone, same known failures |
| The scorecard reproduces the gate's numbers from the committed golden runs | Yes (test): dev GPU 97.0% with code, 92.0% alone; held-out GPU 100.0%, 93.3% alone, 30 cases |
| The name ban bites | Adding a model name to a comment in `agent-core` turned `AppInvariantsTest` red (checked, reverted) |
| Full verification | JVM tests, gate, release scripts and builds pass |

## Not done or not verified

- **No fresh phone scorecard yet** (the phone was not connected). `docs/scorecards/gemma-4-e2b.md` is built from the committed golden runs (dev and held-out on prompt v3.8, the full English set on the older v3.5), and the two LFM2.5 cards from the Phase 0 runs (38 cases, older prompt), which are not comparable and say so. The plan's exit criterion, a scorecard for Gemma on GPU and CPU and one for LFM2.5-1.2B from a fresh phone run with `scripts/scorecard.ps1`, is open. The script was parse-checked only.
- **Streaming (`generate`) and stop sequences are not in the contract.** The plan listed them for this stage; their first user is the voice work in Stage 13, and adding them now would leave native code untested. They are added there.
- **The relative swap rule is a target** (2 points, 1.25 times); no model has been judged by it with fresh data.
- The planner still sends "now" in the system prompt; moving it to the user turn is part of Stage 13's prefix-reuse measurement (it changes the prompt, so it needs a new prompt version and fresh goldens).
- Only the line answer format exists; the profile field is ready for others.
- The model registry still holds one model, so `ModelSelection` has only been exercised in tests.
