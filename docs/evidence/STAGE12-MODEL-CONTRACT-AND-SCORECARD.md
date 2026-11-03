# Stage 12 evidence: the model contract and the swap scorecard

Date 2026-11-03. JVM results unless marked. Numbers are measured from saved run files (one Moto g86 power, Android 16) or are targets.

## What was built

- **Capabilities.** `Capability` (`TEXT`, `AUDIO_IN`); `LlmEngine.capabilities` (default text only); `GenerationRequest.requires()`; `GuardedEngine`, which refuses an unsupported request with `UnsupportedCapability` ("this model cannot hear audio") before any native call. The model host hands out the guarded engine. `LiteRtLmEngine` reports `AUDIO_IN` only when the audio encoder actually loaded.
- **Model profile as data.** `models.json` entries have a `profile`: answer format, planner prompt id, the zone label written after the time, capabilities, thinking mode. The registry parser validates it (unknown capability, missing `TEXT`, empty ids are refused). The host passes the profile's thinking mode at load. `ModelSelection.pick` chooses a model by role and preference.
- **Prompt and parser by profile.** `PromptCatalog` (prompt id to a spec with a version), `AnswerParser` and `AnswerFormats` (the line format), `ModelHarness` (builds the planner, parser and speech path from a profile; unknown names fail at wiring), `CommandPipeline` takes a parser. The planner prompt text is unchanged, and the zone label " (IST)" is now written by the Gemma profile rather than by the harness, so the bytes sent to Gemma are identical (checked by `ShippedRegistryTest` and `SwapTest`, which compare the whole system prompt; the pinned hash test covers only the command text). This is a deviation from the plan, which said to drop "(IST)": dropping it changes the prompt and needs a new prompt version and fresh phone goldens, so it is left for Stage 13's prompt work.
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

## After the Opus review

The review (PASS WITH CHANGES, no Critical findings) led to these changes.

- The registry is strict: an unknown profile key (a typo such as `capabilites`) is an error, an `llm` entry must carry its own profile, ids must be unique, roles are `llm` or `stt`. The shipped model is marked `"default": true`; `ModelSelection` uses the named model or the default for the role and never falls back silently (adding a candidate to the registry cannot change what the app ships).
- `ShippedRegistryTest` resolves the shipped registry through the prompt catalog, the answer parsers and the speech path, checks the whole system prompt Gemma receives against the measured prompt, and checks the eval's time line equals the app's. A renamed prompt id now fails a JVM test instead of crashing the app at launch; the pinned-hash message names `PlannerPrompt.ID` and `models.json`.
- Voice: `UnsupportedCapability` is mapped to "this model cannot hear audio" instead of "try again" (a test covers a profile that promises audio over an engine that lost it); the engine logs when the audio encoder falls back, resets its flag on unload, and the flag is volatile. The host test now covers the second `acquire`.
- `LlmPlanner` has no default prompt or zone label, so the profile is the only way in.
- The scorecard fails on duplicate runs for one set and backend, takes a device-label filter, prints the prompt version and device on every row and flags mixed prompt versions, marks a candidate "not comparable" for incomplete, differently sized or differently prompted runs, and checks JSON validity and injection leak. The CLI names skipped files and fails on a broken registry. `scripts/scorecard.ps1` also runs the full English set, loads the audio encoder like the app, keeps decode speed on, and passes the device label to the card. The committed cards were regenerated with the commands in `docs/scorecards/README.md`.

## Fresh phone scorecard for Gemma (2026-11-03, Moto g86 power, measured)

Six runs from `scripts/scorecard.ps1` (device label `scorecard-20261103`, prompt v3.8, audio encoder loaded, decode speed on), in `docs/scorecards/gemma-4-e2b.md`:

| Set | Backend | Action with code | Model alone | Args | JSON valid | First token p95 | Peak RSS |
| :-- | :-- | --: | --: | --: | --: | --: | --: |
| assistant-dev (100) | GPU | 97.0% | 92.0% | 97.0% | 100.0% | 1083 ms | 2203 MB |
| assistant-dev (100) | CPU | 97.0% | 93.0% | 97.0% | 100.0% | 2117 ms | 1723 MB |
| held-out (30) | GPU | 100.0% | 93.3% | 100.0% | 96.7% | 1082 ms | 2100 MB |
| held-out (30) | CPU | 100.0% | 93.3% | 100.0% | 96.7% | 2114 ms | 1572 MB |
| english-full (101) | GPU | 85.1% | n/a | 85.1% | 100.0% | 1415 ms | 2142 MB |
| english-full (101) | CPU | 84.2% | n/a | 84.2% | 100.0% | 3030 ms | 1671 MB |

- The development and held-out rows are identical to the committed goldens (the same cases right and wrong), so a fresh install gives the same answers and the swap rule against the goldens passes on every row (the model, the prompt and the harness behave the same after the refactor, on the phone).
- GPU load took 40.5 s on the first run and 4.8 s when the held-out run reloaded it (cache warm); CPU load 6.6 s and 1.0 s. Decode speed 9.1 tokens per second on GPU and 11.4 on CPU (median).
- **The English set needs updating for v0.01.** 30 of its 101 commands expect `LogExpense` (15) or `OpenUpiPayment` (15). The shipped prompt does not offer `OpenUpiPayment` and the app refuses payments, so those 15 cases fail by design: 86 of 101 right on GPU (85.1%) is exactly the 15 payments missing. The old 98 to 99% figures were measured on the v3.5 prompt. Before the English goldens are refreshed on v3.8, the expectations for payment requests must change (to no action or a refusal), which is a product decision recorded for the owner.
- Injection leak on the English set is unchanged: 12.5% on GPU and 2.5% on CPU (measured; the same figures as Phase 0). The injection set is not part of the gate for v0.01 because no untrusted text reaches the model yet (Stage 16 changes that).

## Not done or not verified



- **The LFM2.5-1.2B scorecard from a fresh phone run is not done.** The two LFM2.5 cards come from the Phase 0 runs (38 cases, older prompt), are not comparable and say so. The Gemma scorecard is fresh (above). The script worked for Gemma after two fixes found during the run (option passing, an exact device-label filter).
- **Streaming (`generate`) and stop sequences are not in the contract.** The plan listed them for this stage; their first user is the voice work in Stage 13, and adding them now would leave native code untested. They are added there.
- **The relative swap rule is a target** (2 points, 1.25 times); no model has been judged by it with fresh data.
- The planner still sends "now" in the system prompt; moving it to the user turn is part of Stage 13's prefix-reuse measurement (it changes the prompt, so it needs a new prompt version and fresh goldens).
- Only the line answer format exists; the profile field is ready for others.
- The model registry still holds one model, so `ModelSelection` has only been exercised in tests.
- Not fixed from the review (small or product decisions): two enums are both named `Capability` in `models` (`models.Capability` and `models.host.Capability`); a text-only profile still shows the microphone; the licence screen text and the model size in `strings.xml` are still written for Gemma instead of coming from the registry (a swap would leave them wrong); `ScorecardTest` hard-codes numbers from the goldens; the bench always uses the shipped prompt, so a candidate's own prompt id is not measured; set classification uses case id prefixes; the audio-fallback state is logged but not shown on the Diagnostics screen.
- `ModelSupplyTest.aPausedDownloadOffersMobileDataAndRetriesWithIt` timed out once during this work and passed on two reruns (an existing timing-sensitive test).
