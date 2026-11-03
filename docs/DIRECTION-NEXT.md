# Direction and build plan after Stage 10

Advice from Fable 5.1 (medium effort), 2026-11-02, written after reading the repository. The owner approved the order on 2026-11-03: Stage 12 (model contract and swap scorecard), then Stage 13 (voice and load speed), then Stage 11 (pre-release hardening); Stages 14 to 17 follow in that order and are not yet scheduled. The three decisions in section 5 are still open except where noted there. Numbers are labelled **measured** (one Moto g86 power, Android 16), **target**, or **vendor claim**. The owner's standing direction is in `docs/HANDOFF.md`: brain changes, body never; start from the wired model (Gemma 4 E2B on LiteRT-LM) and make the harness stable, fast, better at tool calling, and model-agnostic.

## 1. Where Bheem is

Strong:
- The security spine is real and tested: a closed action catalog, one shared gate, provenance on every proposal, the ledger, the red-team suite through the real pipeline, log and wiring tests, encrypted storage, verified model files, no network outside the downloader.
- The eval harness is honest: goldens pinned by data hash, a known-failures list, a raw "model alone" floor (85%, target) and a "decided by code" cap (25%, target), and a held-out set nobody reads. Dev set 97% with code and 92% model alone on GPU (measured); held-out 100% and about 93% alone (measured).
- `agent-core` has no model runtime in it, and the model host is well built and tested.
- Release engineering is ahead of most solo projects (reproducible build on one machine, signing outside Gradle, protected release environment, schema pin).

Weak, and where a model change leaks into the harness:
- The model contract is minimal: `load`, `complete`, `unload`. No streaming, stop sequences or capability flags. Whether a model can hear or follow the line format is known only from constructor arguments in `AppGraph`.
- The planner prompt and `LineParser` are a Gemma-shaped pair, tuned over eight prompt versions. LFM2.5-1.2B on the same prompt got 13 to 16 of 38 against 34 (measured). That is the clearest proof the prompt and parse layer is not yet model-agnostic.
- Voice needs the language model to hear audio. Swap to a text-only model and voice disappears.
- `AppGraph` hard-codes one model id.
- Speed is a product problem: GPU load about 38 to 41 s per process start (measured) against about 1 s on CPU when warm (measured); voice release to card 12.9 to 14.1 s for 6 to 9 s of speech (measured) against 6 s (target); first launch to first card 77.3 s (measured).
- Memory is thin: Ask is a word-overlap scorer over notes, tasks and expenses; it cannot read reminders or the ledger, has no time scoping, and records have no provenance.
- "(IST)" is still in the planner prompt (checked).
- Open stability items: Stage 5 busy-model reminder delay, TalkBack and Accessibility Scanner, the older-database migration test, Gradle dependency hashes, Stage 11, and the release workflow has never run.

Verdict: the body is sound; the neck between body and brain is the weak joint. Make that joint explicit before adding features.

## 2. Harness direction

- **Model contract.** `LlmEngine` gains capabilities (text, audio in, streaming, stop sequences, thinking toggle, prefix cache, native tools, constrained JSON) and a streaming `generate`; the host refuses a request that needs a capability the engine lacks. `SpeechToText` is its own interface with the registry choosing the implementation (rename `GemmaAudioSpeech` to `LlmAudioSpeech`). A `ModelProfile` in `models.json` per model: answer format, prompt id, quirks, thinking default, capabilities. `PlannerPrompt` becomes a prompt catalog and `LineParser` one parser behind an answer format. The gate only ever sees `ProposedAction`. `AppGraph` picks the model from the registry by role and preference.
- **Tool calling.** Keep the one-line format as the default (about 35% less total time than JSON, measured; 100% of commands on the full English set, measured). Do not build native tool calls or constrained decoding now; add parsers only when a candidate's scorecard shows the line format failing it. Deterministic layers in order: PreRouter, RulesPlanner, model, parser, TimeReconciler, Gate, tap. Add one repair retry on a parse failure (measure its cost first) and a rules-first path for reminder, task and note when the time resolves fully, keeping the 25% cap as a measurement guard and a separate product counter for live rules share.
- **Cheap, provable swaps.** A scorecard script that runs one model on both backends over the dev set, held-out set (aggregates only), full English set and the injection set, and writes the same table every time (accuracy, injection leak, first token p50 and p95, tokens per second, load time, peak memory, battery, licence, runtime). A relative swap rule in the thresholds: a candidate may be at most 2 points worse than the current model on any accuracy row and at most 1.25 times slower on first token p95 (targets). A JVM swap test with a fake quirky engine.
- **Speed.** Voice first: one acquire for hearing and planning; cap transcription at 48 output tokens with a newline stop (target); move the time line from the system prompt into the user turn so the system prompt is identical across requests, then measure whether LiteRT-LM reuses prefill (unknown today). Decide CPU versus GPU from data on total time per command including cold starts; idle unload 10 minutes in front and 3 in the background (targets). No small-model-first cascade: peak memory is already about 2.3 GB (measured).
- **Memory.** Ask reads reminders and the ledger, supports time-scoped queries, uses FTS5 as the candidate source, and records get provenance. Personal facts are notes with a fact kind, so no new gate surface. No embeddings, summaries or model-written memory until recall on a 50-question set shows a gap.
- **Stability.** Local monitoring on the Diagnostics screen (parse failures, time corrections, refusals by reason, load time and backend, peak memory, voice durations, rules versus model share), the 40-case list, the migration test, and a 200-command soak with idle unloads (target: zero uncaught crashes, memory back under 400 MB after unload).
- **Do not build yet:** a second runtime, embeddings, constrained decoding, native tool calls, wake word, TTS, Hindi, notification access, skill packs, routines.

## 3. Product direction

Bheem for the next two or three releases: a quiet, trustworthy capture tool for English-speaking Indian Android users on 8 GB phones, starting with the owner and a few friends. One sentence in, one card out, one tap, undo. The pitch: it never leaves your phone and it never acts alone.

Features that matter most: (1) reminders that never fail, with recurring and snooze; (2) voice at or under 6 s release to card (target) with the transcript visible and editable; (3) Ask over everything you told it; (4) share-sheet text capture, the first untrusted source, at L1; (5) a task list with done state.

Cut or hold: calendar durations, routines, skill packs; decide whether expense logging stays, since it leads toward the money diary that was ruled out. Interface: the card shows what was heard, the resolved time in words and what will happen; a quiet feed; honest readiness ("getting ready, about 40 s"); nothing needs Settings.

Out of scope: payments until a regulated-partner route exists and the untrusted path has a measured live leak under 2% (target) over a 200-case set; notification reading until share-sheet capture has run for a release, the extraction call is red-teamed on the phone, and the Play policy is checked.

## 4. Build plan

Each stage is one or two sessions; Sonnet implements test-first; Opus reviews.

**Stage 12 (built 2026-11-03; phone scorecards pending): the model contract and the swap scorecard.** Goal: a model is described by data, not code, and a swap is a measured comparison. Scope: capabilities and `generate`; `ModelProfile` and registry parser; prompt catalog and answer format; `LlmAudioSpeech` behind `SpeechToText`; `AppGraph` picks by role; scorecard script and relative swap rule; drop "(IST)". Exit: all JVM tests green; `:eval:gate` unchanged (dev 97%, held-out 100%); a swap test with a fake quirky engine passes; a scorecard for Gemma GPU and CPU from a fresh phone run, and one for LFM2.5-1.2B by the same script. Risk: the prompt refactor shifts a golden by a case. Needs the phone. Review focus: any model name left in `agent-core`; a parser quirk moved into the profile without a test; gate behaviour changed; the held-out set opened.

**Stage 13: voice and load speed.** Goal: release to card at or under 6 s (target) for a 5 s utterance, warm model. Scope: one acquire; 48-token cap and newline stop; time line moved; measure prefill reuse; CPU versus GPU study; idle window; first token shown in the card header. Exit: ten live presses with p50 at or under 6 s and p95 at or under 8 s (targets, measured); dev accuracy unchanged; findings written into `docs/MODELS.md`. Risk: LiteRT-LM does not reuse prefill and the floor stays about twice the audio length. Needs the phone and the owner speaking.

**Stage 11 (existing): pre-release hardening**, run after Stage 13 because Stages 12 and 13 change the capture path it certifies. Needs the owner: the release key, `docs/release-cert.sha256`, the `release` environment, the tag ruleset, the app id, and the phone for 24 hours. Then tag `v0.1.0-rc.1`.

**Stage 14: deterministic front end and richer actions.** Fewer model calls, better arguments, no new gate surface. Rules-first for reminder, task and note; one measured repair retry; recurring reminders and snooze (with the older-database migration test); `TimeResolver` reads month names and ordinals; 50 new assistant cases from the owner's own refusals. Exit: dev action at or above 97% and model alone at or above 85% (targets); rules take at least 30% of live reminder commands in a 100-command phone log (target); a reminder survives reboot and recurs (measured).

**Stage 15: memory and Ask.** Reminders and the ledger in Ask; time-scoped queries; FTS5 as the candidate source; provenance on records; fact notes in voice hints; a 50-question Ask set. Exit: at least 45 of 50 answered with the right records (target); no answer cites an untrusted record as a fact. Risk: Ask starts adding up amounts.

**Stage 16: share-sheet capture, the first untrusted source.** Share target; no-tools extraction prompt and profile entry; untrusted provenance from pipeline to card; the red-team set run with the real model on the phone (200 texts, measured leak rate); per-source rate limit keyed by the sharing app. Exit: zero cards without a tap (measured); the live leak rate written down; SECURITY.md updated. Risk: the Phase 0 GPU leak of 12.5% (measured) returns.

**Stage 17: interface and daily cadence.** Nightly tag automation, the Ask screen, tasks with done state, a card copy pass, the TalkBack recording and Accessibility Scanner. Exit: a nightly builds from green main without a hand; the Stage 8 accessibility items closed.

### Model evaluation protocol (any new model, including LFM3)

1. Read the licence text; it must allow redistributing a downloaded weights file to users of an Apache-2.0 app. 2. It must run on an adapter the app already has (LFM3 lists llama.cpp, MLX, Ollama, vLLM and Qualcomm as a vendor claim and not LiteRT-LM, so today it has no path). 3. Registry entry with hashes, profile and capabilities. 4. Scorecard on both backends, same sets, same phone, held-out as aggregates only. 5. Gate: absolute thresholds plus the relative swap rule; red-team on the phone if the model will see untrusted text. 6. Go only if every absolute threshold and the relative rule pass, or a written reason names the one row it loses and why that is acceptable. Vendor benchmarks never count.

## 5. Decisions for the owner

1. **Application id.** Recommended: decide before `v0.1.0-rc.1`, something like `app.bheem.assistant`, and move `bench` to its own id. Wrong cost: every early installer reinstalls and loses data, and the benchmark app keeps colliding with the product. (The owner chose on 2026-10-27 to keep `app.bheem.bench` for now.)
2. **Release timing.** Recommended: tag v0.1.0 after Stage 13, with Stage 11 run against that build. Wrong cost: two or three more weeks without a public artefact; if the harness work drags, cut Stage 13 to the one-acquire change and ship.
3. **Second runtime policy.** Recommended: no llama.cpp or other adapter until a candidate has no LiteRT-LM build and a clear reason to beat Gemma on the scorecard, and then only as a `bench` adapter first. Evidence: the one-call audio-to-action path lost to the two-call path in the Phase 0 speech runs (the report quotes 79% against 94%; not re-checked here), and LFM2.5 lost badly on the line prompt. Wrong cost: if a better small model arrives on another runtime only, Bheem is two or three stages late adopting it.

Open questions the advisor could not settle: whether LiteRT-LM 0.18.1 reuses prefill across conversations (measure in Stage 13); whether storage's `search` already uses FTS5 for Ask (the interface exists, Ask scores candidates itself); the LiteRT-LM licence for F-Droid (unchecked).
