# Production direction: from today to v0.01, then daily

Written 2026-10-24 against branch `phase0-benchmark` at `493ed0e`. Every number is **measured** (a phone run, one Moto g86 power, one speaker) or **target** (chosen). The owner's decisions in [V001-PLAN.md](V001-PLAN.md) are taken as fixed. This is advice for an implementer and a reviewer, not a spec; each stage gets its own spec before code.

## 1. Where Bheem is today

| Area | Status | Main gap |
| :--- | :--- | :--- |
| Product | Scope decided and written down (English, typed plus hold-to-talk, every action taps, no notifications) | README still says "design phase, no code"; ARCHITECTURE lists modules that do not exist (`memory`, `skill-*`) |
| Harness core (`agent-core`) | Gate, ledger with legal transitions, pipeline, coordinator, time resolver, feed, Ask, planner; about 400 JVM tests green | Ledger and feed scan every row on every call; ledger ids come from `store.all().size + 1`; proposals do not record provenance, source, prompt or model version |
| Persistence and privacy | Plain `SQLiteOpenHelper`, version 1, `onUpgrade` is a no-op, `allowBackup=false` | No encryption, no migrations, no indexes, no erase-everything, times stored as zone-less strings with "IST" hard-coded in the prompt |
| Model delivery | File sideloaded to the app's external dir; GPU then CPU load inside the first command | No registry, no SHA-256 check, no download, no unload, no governor; a load (5 to 182 s **measured**) runs in a plain coroutine and dies with the process |
| Voice | Measured in `bench` only: Gemma audio with hints 94% action on one speaker, 2.6 s (**measured**) | Nothing in the app; no `SpeechToText` interface in `models`; `Transcriber` lives in `eval` |
| UI and onboarding | One screen, Bar (type), cards with OK, Dismiss, Undo, Try again; exact-alarm banner | No onboarding, no permission flow, no voice button, no ledger or settings screen, 5 s polling, no accessibility semantics |
| Background and battery | Reminders via `AlarmManager` with inexact fallback | Alarms are lost on reboot (no table, no boot receiver); request codes from `hashCode` can collide; engine stays resident forever once loaded (2.0 GB RSS **measured**) |
| Security | Gate rejects unknown actions, bad args, invented message text; untrusted text limited to two actions; 15 gate tests | Red-team fixtures (`adversarial.jsonl`) test the model, not the gate; no network test; no flavors; grounding is a 50% word-overlap heuristic |
| Quality and evals | Strong harness: datasets, scorers, thresholds, report, rescoring of saved outputs | No CI at all (no `.github`); assistant set (38 cases, 89.5% **measured**) has no threshold and is below the 90% **target**; on-phone runs are manual |
| Release engineering | `versionCode 1`, `0.0.1-dev`, app id `app.bheem.bench` | No signing, no flavors, no CI, no release artefacts, no changelog, no F-Droid metadata |
| Docs | Honest and well labelled | Stale README; module list; `SECURITY.md` describes controls that are not built (allow-list, SQLCipher, downloader) without saying so |

Verdict: the kernel is in good shape for its age and the measurements are honest. Nothing around the kernel (storage, model lifecycle, voice, onboarding, release) exists yet. v0.01 is roughly eleven stages of work, and the first three are the ones that make the rest reviewable.

## 2. Stages

Order matters: CI first so every later stage hands the reviewer test output; storage and the model host next because every feature touches them. Each stage ends with a tagged dev build on the phone.

### Stage 1: CI and the planner quality loop

**Goal:** every push runs the JVM tests and the offline eval gates, so prompt, parser and gate changes are checked without a phone.

**Scope**
- `.github/workflows/ci.yml`: `./gradlew :models:test :agent-core:test :eval:test`, then `:app:assembleDebug` and `:bench:assembleDebug`, on `ubuntu-latest`, Gradle cache on.
- A `:eval:gate` task that rescores the saved line2 outputs (checked in as fixtures under `eval/golden/`) against `eval/thresholds.json` and fails below any limit. Add `assistantActionMin: 0.90` and `assistantArgsMin: 0.85` (**target**). The assistant check will fail at 89.5% (**measured**): that is correct; stage 6 fixes the planner and the gate turns green.
- `PlannerPrompt.VERSION` in `agent-core`; `eval` reads it so the shipped and measured prompts cannot drift. A test asserts the command prompt stays within two 128-token prefill chunks (the proxy already used in `PromptsTest`).
- `scripts/bench-release.ps1`: the full English set plus the assistant set on the phone, both backends, writing `docs/PHASE0-REPORT.md`. Manual, before each tagged release.
- README status and the ARCHITECTURE module list corrected to what exists.

**Exit criteria:** CI green on a PR that changes only a comment; CI red on a PR that changes one expected action in `assistant.jsonl`; the gate task's output lists every threshold with its value.

**Out of scope:** on-phone runs in CI, device farms, instrumented tests.

**Review focus:** thresholds loosened to pass; golden outputs regenerated instead of committed as fixtures; the prompt constant duplicated rather than shared; CI steps that fetch anything but Gradle dependencies; the `.gitignore` still excluding results the gate now needs.

### Stage 2: Storage, data model and migrations

**Goal:** one encrypted SQLite database owns proposals, ledger, memory and reminders, with migrations and indexed queries, so no later stage touches the schema blind.

**Scope**
- New Android library module `:storage` (`app/bheem/storage`), depended on by `:app` only. SQLCipher for Android (community edition, Apache-2.0 compatible; verify the exact artefact licence before adding) with a 32-byte key generated once and wrapped by an Android Keystore AES key (StrongBox when available, fall back silently). Key file in `noBackupFilesDir`.
- Schema version 2 (version 1 databases are dev-only: the migration deletes and recreates, and the stage note says so). Tables: `proposals` (adds `provenance`, `source`, `state`, `prompt_version`, `model_id`, `backend`, `created_at_epoch`, `zone_id`), `ledger` (`id INTEGER PRIMARY KEY AUTOINCREMENT`, index on `proposal_id`), `memory` (adds FTS5 virtual table over `text`), `reminders` (`id`, `proposal_id`, `text`, `fire_at_epoch`, `state` in scheduled, fired, cancelled). Times as epoch millis plus zone id, never `LocalDateTime.toString()`.
- `agent-core` interface changes, test-first: `LedgerStore.append(entry): Long` (store assigns the id), `history(proposalId)`, `lastEvent(proposalId)`, `openProposalIds()`. `Ledger` keeps its transition rules and stops calling `all()`. `ProposalStore.put` and the ledger append for one transition run in one SQLite transaction, exposed as `TransactionRunner` in `agent-core` with an in-memory implementation for tests. `StoredProposal` gains `provenance`, `source`, `promptVersion`, `modelId`. `Feed.items` takes already-joined rows instead of calling `ledger.history` per proposal.
- A `changes: Flow<Unit>` from the store so `AppController` stops polling every 5 s.
- `EraseEverything` in `:storage`: closes the database, deletes the file, key file and model cache directory, resets the Keystore alias. Wired to a Settings entry in stage 8.

**Tests:** JVM: ledger transitions unchanged (existing tests pass against the new interface), transaction atomicity with a failing append, feed from joined rows, zone round trip. Instrumented (`:storage:connectedAndroidTest`, run on the phone): open with wrong key fails, migration 1 to 2 recreates, FTS query returns the note, erase leaves no file under the app's data dirs, 1,000 ledger rows read in under 50 ms (**target**).

**Exit criteria:** the app runs on the phone against the encrypted database; `adb shell run-as` pull of `bheem.db` is not readable by `sqlite3`; all JVM and instrumented tests listed above green and pasted into the hand-off.

**Out of scope:** vector search, retention policy UI, Room (keep raw SQLite; the stores are small and Room buys nothing here).

**Review focus:** key material logged or kept in `SharedPreferences`; a path where a proposal row changes without a ledger entry; `LocalDateTime` strings surviving in a column; `all()` still called on a hot path; FTS tokenizer choice (use `unicode61`); SQLCipher licence and version pinned in `libs.versions.toml`.

### Stage 3: Model host and lifecycle

**Goal:** the model loads on demand, survives the app being backgrounded during a 5 to 180 s load, falls back from GPU to CPU once per device, unloads when idle, and lowers itself under thermal or battery pressure.

**Scope**
- `models`: `ModelRegistry` data class (`id`, `role`, `runtime`, `files` with `sha256` and `bytes`, `license`, `minRamGb`) loaded from `app/src/main/assets/models.json`; Gemma 4 E2B entry with the hash from [PHASE0-RESULTS.md](PHASE0-RESULTS.md). `ModelHost` interface: `state: StateFlow<ModelState>` (`Absent`, `Verifying`, `Loading(backend)`, `Ready(backend)`, `Failed(reason)`), `suspend fun acquire(): LlmEngine`, `release()`. Pure Kotlin, no Android.
- `:app` (or `:runtime-host` if it grows): `AndroidModelHost`. Verify SHA-256 on first use after install or update (store the verified hash plus file size and mtime so later launches skip the 2.6 GB read). Backend order GPU then CPU; on a GPU failure persist `gpuFailed=true` with the LiteRT-LM version, so the next load starts on CPU and a library upgrade retries GPU. Idle unload after 3 minutes without a request (**target**, tune after battery measurement in stage 11). `LlmPlanner` loses `ensureLoaded` and takes a `ModelHost`.
- `ModelLoadService`: a foreground service started when a load begins while the activity is not resumed, or when the activity pauses mid-load. Type `shortService` on API 34 and above (3 minute cap from the Android developer documentation, to be verified when this stage is built, covers the measured range except the 182 s outlier; on timeout, stop, mark `Failed("load timed out")`, and the card says so), plain foreground service on API 31 to 33. Notification: "Bheem is getting ready". No work other than the load runs in it (Android 16 quotas).
- `Governor` in `agent-core`, pure: inputs battery saver, thermal status (0 to 6), charging; outputs `Full`, `CpuOnly`, `RulesOnly`. v0.01 rules: battery saver or thermal at or above 3 means do not load; a loaded engine is unloaded at thermal 4. App feeds it from `PowerManager` and `PowerManager.OnThermalStatusChangedListener`.
- First-command UX: the card area shows "Getting ready, about N s" with N from the last measured load on this device, and a cancel.

**Tests:** JVM: host state machine (fake engine: GPU throws then CPU succeeds; both fail; cancel during load; idle unload timer with a test clock; governor table). Instrumented on the phone: 10 cold loads with the screen locked two seconds after submit, all reach `Ready` or `Failed` with a reason, none leave the process killed mid-load (`dumpsys activity services` and logcat captured); tampered file (one byte) is refused before `Engine(...)` is constructed.

**Exit criteria:** first useful answer p95 under 1.5 s on GPU with the model warm (**target**, already 1.2 s **measured**); a load started and then backgrounded completes; a tampered model never loads; the phone log of the 10 loads is in the hand-off.

**Out of scope:** download (stage 4), prompt cache, a second runtime, NPU.

**Review focus:** the SHA check skipped when the cached hash is stale; GPU fallback that retries GPU on every launch (182 s each time); the service started from the background on API 31 to 33 (not allowed); idle unload racing a request in flight (`acquire` must hold a use count); any `Dispatchers.Main` call in the engine path; thermal listener leaking the Application context.

### Stage 4: Model delivery and build flavors

**Goal:** a clean phone gets the model with a resumable, verified download in `full`, or by sideload in `offline`, and the app is useful in rules-only mode until then.

**Scope:** `offline` and `full` flavors; only `full` declares `INTERNET`, and a manifest test fails the build if `offline` gains it. Downloader in `full` only: HTTP range resume, Wi-Fi default with cellular opt-in, progress card, pause on battery saver, SHA-256 verify then atomic rename, 3 GB free-space check (**target**). Rules-only mode: commands `TimeResolver` plus a small verb table can handle ("remind me ... at ...", "note ...", "task ...") become cards without the model, through the same gate; everything else says the model is still downloading.

**Exit criteria:** a download killed at 40% resumes (log of `Range` headers); a corrupted file is deleted and retried; the `offline` manifest has no `INTERNET`; a fresh install sets a reminder in rules-only mode.

**Out of scope:** mirrors, delta updates, model switching UI.

**Review focus:** redirects dropping the range header; a wake lock held for the download; rules-only parsing bypassing the gate; any network call outside the downloader (a CI grep for `java.net`, `okhttp`, `HttpURLConnection` across modules).

### Stage 5: Reminders and notifications reliability

**Goal:** a reminder set today fires at the right minute after reboot, Doze and an app force-stop, or the user is told why not.

**Scope:** `ReminderScheduler` in `:app` over the `reminders` table from stage 2: the executor writes the row, then arms `setAlarmClock` (exact granted) or `setAndAllowWhileIdle` (not granted) with the row id as request code. `BootReceiver` on `RECEIVE_BOOT_COMPLETED` (first unlock, since the database key needs the user's credential) re-arms future `scheduled` rows and posts one catch-up notification for missed ones. `ReminderReceiver` marks the row `fired` and posts a notification that opens the ledger entry. A one-time OEM guidance screen (Xiaomi, Realme, Samsung) when `isIgnoringBatteryOptimizations` is false.

**Exit criteria:** on the phone, a reminder fires after `adb reboot`, under `dumpsys deviceidle force-idle`, and after the exact permission is revoked (within the inexact window, and the card said so); undo cancels the alarm (`dumpsys alarm` shows none); equal text and time do not collide.

**Out of scope:** recurring reminders, snooze, calendar sync.

**Review focus:** re-arming before the database key is available; database work in a receiver on the main thread; `FLAG_UPDATE_CURRENT` reusing stale extras; a notification tap landing on a blank screen; the catch-up path firing hundreds of stale alarms after a long power-off.

### Stage 6: Executors, failure and undo, Ask, planner fixes

**Goal:** every v0.01 action has an executor with defined failure and undo behaviour, and the planner meets the assistant-set threshold.

**Scope:** the executor contract written into `Proposals.kt`: `execute` is idempotent per proposal id and returns `Done(undoToken)` or `Failed(reason)` in user-readable words; `undo` returns false only when nothing changed. Calendar and message executors hand off and cannot undo, so their card says "Opened in your calendar" with no Undo. `OpenUpiPayment` leaves the v0.01 prompt and is gated to `Refused("payments come later")`, with a test. `LogExpense` stays as a plain local record if the owner wants "I spent 90 at Chai Point" to work (recommend yes: no notifications, no totals). Ask: FTS over notes and tasks with cited records; spending totals removed (the money diary is out). Planner: fix assistant-set failures in code first (a small list of question openers routes facts and weather to `NoAction` before the model). Grow `assistant.jsonl` to at least 100 hand-written cases with a 30-case held-out split the prompt author never reads.

**Exit criteria:** `:eval:gate` green on both sets (assistant at or above 90% action, **target**); on the phone, each executor's failure path shown and retried once (calendar app disabled, WhatsApp absent); undo of a note, task and reminder verified; Ask answers three questions with the right records.

**Out of scope:** routines, contacts lookup, SMS sending.

**Review focus:** retry after `FAILED` re-running a hand-off that already opened; undo token reused across proposals; the grounding check passing on a message that is 50% the user's words and 50% invented (tighten to recipient exact plus every content word present, measured against the assistant set); denylist leaking into the eval as a free pass.

### Stage 7: Voice end to end

**Goal:** hold to talk produces a card through the same pipeline, with the transcript shown before the action.

**Scope:** `SpeechToText` in `models` (`suspend fun transcribe(wav: ByteArray, hints: Hints): Transcript`); `GemmaAudioSpeech` in `models-litertlm` behind the same `ModelHost` (one engine, audio encoder on CPU as measured). Hints: the command-word list plus names from the user's own notes, tasks and recent recipients, capped at 60 (**target**; 60 decoys cost 0.2 to 0.4 s **measured**). `AudioRecord` 16 kHz mono, RAM only, WAV bytes form (raw PCM fails in 0.18.1, **measured**), 10 s cap. Bar: hold to talk with a level meter, release to send; the transcript sits in the card header with an Edit affordance that resubmits as typed text. Moonshine stays in `bench`.

**Exit criteria:** the 34 S03 takes replayed through the app path (a debug file-input mode) match the bench result within one take; a live spoken reminder reaches a card within 6 s (**target**: 2.6 s audio plus about 3 s planning, both **measured**); a denied microphone permission shows a plain message; no audio file exists on disk after a session.

**Out of scope:** streaming partials, wake word, Hindi, TTS, more speakers (that is the first daily cycle).

**Review focus:** audio written to cache for the file form (must stay bytes); the engine opened without `audio=true` then asked for audio; the hint list including anything from untrusted text; recording continuing after the activity pauses; the one-call audio-to-action path creeping back in (rejected on evidence).

### Stage 8: Onboarding, Today feed, cards and accessibility

**Goal:** a person who never opens settings can install, grant what is needed when it is needed, and understand every card.

**Scope:** onboarding in place, not up front: one screen on what Bheem is and that nothing leaves the phone; notifications permission at the first reminder; microphone at the first hold; exact alarms inline as today. Model status card (downloading, verifying, ready, failed with a fix). Feed sections: needs you, upcoming reminders, recent activity. Ledger screen in plain words. Settings: Erase everything (two-step confirm), model and backend status, licences. Accessibility: content descriptions everywhere, 48 dp targets, TalkBack order title then detail then actions, dynamic type to 200% without clipping, colour never the only status signal. Light and dark.

**Exit criteria:** a TalkBack screen recording of set reminder, approve, undo; Accessibility Scanner clean; fresh install to first card under 90 s with the model present (**target**); Erase everything leaves no files under the app's data dirs and the next launch is first-run.

**Out of scope:** widgets, share-sheet capture, quick-settings tile, animations.

**Review focus:** a permission asked before its benefit is shown; erase leaving the Keystore alias or model file; `DISMISSED` or `UNDONE` rows in the feed; polling reintroduced; strings hard-coded in composables (Hindi comes later).

### Stage 9: Security gate hardening and privacy

**Goal:** the controls [SECURITY.md](SECURITY.md) describes exist, are tested in CI, and the document says which are built.

**Scope:** `eval/redteam.jsonl`: the 50 adversarial texts plus 30 hand-written pipeline attacks (forged one-line answers, control characters, 9,000-character text, a VPA inside a reminder, "message mom I love you" variants) run through `CommandPipeline` with a fake planner that returns the attack verbatim, asserting no `Card` without `requiresTap`, no ungrounded `DraftMessage`, no `OpenUpiPayment`. The tainted path tested now, before any source feeds it: `Provenance.TAINTED` with every action type, asserting the allow-list and the L1 cap. Logging: a `Redact` helper and a test that no `Log.*` call in `:app` receives user text, a transcript or an action argument. Local diagnostics: the last 50 refusals with reasons, viewable and shareable by the user, never sent. SECURITY.md gains a "built and tested" column.

**Exit criteria:** red-team suite in CI, green; a deliberate regression (allow `DraftMessage` from tainted) turns it red; grep shows no raw text in logs.

**Out of scope:** independent audit (Phase 4), notification allow-list (no notifications in v0.01), biometric gate for L2.

**Review focus:** fixtures that only test the model path; the fake planner wrapping attacks so they never parse; rate limiter keyed by a constant source; `Gate` instances with different policies in pipeline and coordinator drifting (share one); diagnostics storing full user text.

### Stage 10: Release engineering

**Goal:** a signed, reproducible `offline` and `full` APK from a tag, by CI, with a changelog, and a path to F-Droid.

**Scope:** signing keystore held by the owner and passed to CI as secrets; `versionCode` from the tag, semver `versionName` (v0.01 ships as `0.1.0`); reproducible build (pinned Gradle and AGP, no build timestamp, verified by building twice and diffing); GitHub Release on tag with both APKs, SHA-256 sums and the model hash; `CHANGELOG.md`; fastlane `metadata/` for F-Droid with the `offline` flavor, after checking F-Droid's inclusion policy on the LiteRT-LM prebuilt AAR (unverified; do not promise F-Droid until checked). Cadence: a `nightly` tag on green `main`, a `v0.x.y` tag when the release gate passes. Rollback is "install the previous release APK", so every schema migration stays readable by the previous version. Decide the app id before the first public tag; changing it later forces every user to reinstall.

**Exit criteria:** two CI builds of one tag are byte-identical; `apksigner verify` passes; the install guide works from the release page on a phone that never had the app.

**Out of scope:** Play Store, auto-update, telemetry.

**Review focus:** a signing key in a workflow log; `versionCode` not monotonic across flavors; the `full` flavor sent to F-Droid; a release built from a dirty tree; a migration that drops a column the previous version reads.

### Stage 11: Pre-release hardening

**Goal:** the v0.01 gate in [V001-PLAN.md](V001-PLAN.md) passes with evidence.

**Scope:** a written capture-to-card list of 40 cases (typed and spoken, model cold and warm, screen locked mid-load, airplane mode, low storage, permission revoked mid-flow) run on the phone with logcat captured and zero uncaught crashes; `dumpsys batterystats` over 24 hours of normal use with idle unload on (mAh and percent, **measured**); peak RSS from `dumpsys meminfo` during a voice command; a clean-phone install from the release page; the on-phone eval before tagging; every number in the docs labelled.

**Exit criteria:** every line in the release gate has a measured value or a passing test next to it; the owner signs the release note.

**Review focus:** a crash hidden by a `catch (e: Exception)` that shows a card and continues; battery measured with the screen on; RSS measured on CPU only; the test list run against a debug build instead of the signed release.

## 3. Architecture corrections to make now

1. **Ledger and feed must stop scanning.** `Ledger` calls `store.all()` on every record and derives ids from row count; `Feed` calls `ledger.history` per proposal. Indexed queries plus a `state` column written in the same transaction (stage 2). Keep the transition table; it is the valuable part.
2. **Proposals must carry provenance, source, prompt version and model id.** The coordinator hard-codes `Provenance.USER` on re-check; the day notifications arrive this is the injection hole. Store it while the schema is young.
3. **Reminders need a table, not a `PendingIntent`.** Today the alarm is the only record, keyed by `hashCode`. A row plus a boot receiver is the only way to survive reboot and undo reliably.
4. **Take model lifecycle out of `LlmPlanner`.** `ensureLoaded` loads once, never unloads, and `loaded` survives `unload()`. A `ModelHost` with a use count, idle unload, remembered GPU failure and a foreground service for the load is the right owner. Throw `ensureLoaded` and `AppController.modelPresent` away.
5. **Store epoch plus zone, not `LocalDateTime` strings, and drop "(IST)" from the prompt.** Use the device zone; the eval clock is already a parameter.
6. **Put speech behind `models`.** Promote `eval`'s `Transcriber` to `SpeechToText` in `models` and make the eval one an adapter, or the app ends up importing `eval`.
7. **Add `:storage`; do not add `memory` or `skill-*` yet.** `agent-core` depending on `:models` (interfaces only) is fine. Fix the docs to match the code.
8. **Keep manual DI, but `AppGraph` takes a `Clock`, an id generator and a `ModelHost`** so the UI runs with a fake planner in tests and in rules-only mode. Hilt buys nothing at this size.
9. **One shared `Gate`.** The pipeline has a rate limiter and the coordinator does not; policy drift between two instances is a bug waiting.
10. **Replace polling with a change flow.** The 5 s refresh wakes the CPU for nothing and shows stale cards after a tap.
11. **Keep `bench`.** It is the only on-phone eval runner and the recorder. Rename its purpose in the docs; never ship it.
12. **Do not build the one-call audio-to-action path or Moonshine in the app.** The evidence (79% versus 94%; 71% versus 94% action, **measured**) is clear for v0.01.

## 4. Ten risks to shipping

| # | Risk | Mitigation | Early test |
| :-- | :--- | :--- | :--- |
| 1 | GPU load varies 5 to 182 s (**measured**) and Android freezes or kills the app during it | Foreground `shortService` load, remembered backend, progress card | 20 cold loads with the screen locked at 2 s, logcat kept |
| 2 | A 2.6 GB download fails on Indian networks or full storage | Range resume, Wi-Fi default, storage check, offline sideload path | Kill at 40%, toggle airplane, verify SHA |
| 3 | Voice numbers rest on one speaker | Record the other five before freezing voice limits; keep the transcript visible | Replay six speakers through the app path |
| 4 | Assistant accuracy 89.5% (**measured**) is below 90% and the templated set overstates it | 100-case hand-written set with a held-out split; code fixes before prompt fixes | `:eval:gate` on the held-out split |
| 5 | Reminders silently lost on OEM skins and reboot | Table plus boot receiver, OEM guidance | Reboot, force-idle, force-stop on a Xiaomi or Realme |
| 6 | 2.0 to 2.4 GB peak (**measured**) is killed on 6 GB phones under load | Idle unload, governor, rules-only fallback | Run with a 2 GB memory hog resident; borrow a 6 GB phone |
| 7 | LiteRT-LM 0.18.1 experimental APIs change or regress | Pin version; any bump must pass the on-phone eval | Bump in a branch, run `bench-release.ps1` |
| 8 | Invented or injected actions once untrusted text exists | Red-team suite through the pipeline, provenance stored now | Stage 9 suite with a verbatim fake planner |
| 9 | One developer, one phone, no CI: regressions ship | Stage 1 CI with offline rescoring; reviewer protocol | Break one expected action, watch CI fail |
| 10 | Distribution: `app.bheem.bench` id, F-Droid policy on prebuilt AARs, Play notification policy later | Decide the id before the first public tag; verify F-Droid inclusion rules early | Submit a dry-run `fdroiddata` build locally |

## 5. What to cut, and the first daily cycle

**Cut or defer from v0.01:** `OpenUpiPayment` (out of the prompt, gated to a refusal), spending totals in Ask, Moonshine in the app, event durations beyond one hour, share-sheet and widget capture, routines, skill packs, embeddings, TTS, Hindi, a second phone tier, the 5 s polling. Keep `LogExpense` only as a local record (owner call).

**First daily cycle after v0.01 (two weeks):** record and score the other five speakers and freeze the voice thresholds; try a prompt cache for first token (the open ROADMAP question); grow the assistant set weekly from the owner's own refused commands, copied by hand from local diagnostics; share-sheet capture as the first tainted source, which is the first real test of stage 9; a second mid-range phone; decide the app id.

## 6. Review protocol

For every stage the implementer hands the reviewer one PR with:

- **Diff range** (`base..head`), a one-paragraph stage note naming what is built, what is not, and any deviation from this document with the reason.
- **Test output**: the CI run link; for instrumented tests, the full `connectedAndroidTest` console output; for on-phone checks, the logcat and `dumpsys` excerpts named in the stage's exit criteria, with the phone, Android version and build id.
- **Phone evidence**: a screen recording for any UI stage, the eval report for any stage that touches the planner, parser, gate or prompt, and the measured numbers written into the docs with the **measured** label.
- **Security surface statement**: "no new action type, no new permission, no new network call" or the SECURITY.md diff.

**Pass bar:** every exit criterion in the stage has evidence, not a claim; tests were written before or with the behaviour (the reviewer checks the gate and validator commits are test-first per AGENTS.md); no number in docs is unlabelled; no em dash in any text; the review-focus list for the stage has been checked item by item and each is either shown false or fixed. A stage with one open high-severity item does not merge; medium items may merge with an issue filed and linked. The reviewer writes the next stage's spec deltas, if any, in the merge comment so the implementer starts from a settled scope.
