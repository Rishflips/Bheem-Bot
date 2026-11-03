# Handoff: where Bheem stands and how to pick it up

Updated 2026-10-27 at a pause before Stage 11 (first written 2026-10-25 in Stage 5). Read `AGENTS.md`, `docs/PRODUCTION-DIRECTION.md` (the 11 stages) and the evidence file of the stage in progress first.

## Working rules the owner set

- Fable advises (direction), Sonnet implements test-first, Opus 5.5 at high effort reviews each stage; fix review findings before the next stage.
- Never use the em dash character in chat messages to the owner (also stored in memory).
- Commit and push only when told. Every push so far was approved in words ("Yes, push..."). Merging to `main` was approved once (pull request 1, merged 2026-10-25); work continues on `phase0-benchmark`.
- Ask the owner when blocked; otherwise keep shipping. The owner is alone on the phone and unlocks it on request.
- Say what is measured and what is a target. Do not claim something works until a test or a run shows it.

## Stage status

| Stage | State |
| :-- | :-- |
| 1 Quality gate | Done, reviewed twice |
| 2 Encrypted storage (SQLCipher) | Done, reviewed twice |
| 3 Model host | Done, reviewed twice; ten locked-screen cold loads waived by the owner |
| 4 Model delivery, flavors, rules-only | Done, reviewed, pushed, merged to `main` |
| 5 Reminders and notifications | Done and reviewed (PASS WITH CHANGES, fixed), pushed up to 361a4bd; deep Doze waived; one measurement open (below) |
| 6 Executors, failure and undo, Ask, planner fixes | Built, Opus review (PASS WITH CHANGES) fixed, gate green; the final build is not yet re-checked on the phone (see docs/evidence/STAGE6-EXECUTORS-AND-PLANNER.md) |
| 7 Voice end to end | Built, phone-checked and Opus-reviewed (PASS WITH CHANGES, fixed; docs/evidence/STAGE7-VOICE.md); 6 s release-to-card target not met for 6 to 9 s utterances; deferred minors listed in the evidence file |
| 8 Onboarding, Today feed, accessibility | Built and phone-checked (docs/evidence/STAGE8-ONBOARDING-FEED-ACCESSIBILITY.md); the TalkBack recording and Accessibility Scanner are not done; Opus review pending |
| 9 Security gate hardening and privacy | Built, Opus-reviewed (PASS WITH CHANGES, fixed), phone-checked and pushed; CI green at 012b371 (docs/evidence/STAGE9-SECURITY-GATE-AND-PRIVACY.md) |
| 10 Release engineering | Built (docs/evidence/STAGE10-RELEASE-ENGINEERING.md); the workflow has never run (needs the owner's key, secrets and a tag); app id undecided; Opus review pending |
| 12 Model contract and swap scorecard | Built (docs/evidence/STAGE12-MODEL-CONTRACT-AND-SCORECARD.md); Opus-reviewed and fixed; fresh Gemma scorecard done (docs/scorecards/gemma-4-e2b.md), fresh LFM2.5 scorecards done and no-go with the shipped prompt (both builds fail every swap row); the English command set still expects payment actions (see the evidence file) |
| 13 | Not started: voice and load speed (docs/DIRECTION-NEXT.md) |
| 11 | Not started (see PRODUCTION-DIRECTION.md); runs after Stage 13 |

## Open measurement (Stage 5)

Re-measure the reminder delay while the model is generating: create a reminder due in 2 minutes, then send a free-form request so the model runs at the due time, and read the notification `when=`. Before the change to `Dispatchers.IO` in `AsyncReceiver` the delay was 28 s in that situation (34 ms when idle).

## Where things are

- Repo `C:\Users\Gowrishankar\Desktop\Bheem.Bot`, branch `phase0-benchmark`, remote `https://github.com/Rishflips/Bheem-Bot.git`. `main` holds the merge of pull request 1 (534c378). Local `phase0-benchmark` is ahead of `origin/phase0-benchmark` by the stage 5 commits.
- Modules: `models` (engine contract, model host, registry, verified file writer, model supply), `agent-core` (pure Kotlin: gate, ledger, pipeline, planner, rules planner, reminder scheduler), `downloader` (the only module allowed to use the network), `storage` (SQLCipher), `models-litertlm`, `app` (flavors `offline` and `full`), `eval`, `bench`.
- Docs: `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/PRODUCTION-DIRECTION.md`, `docs/V001-PLAN.md`, evidence per stage in `docs/evidence/`. Ledger of the earlier plan: `.superpowers/sdd/2026-10-23-phase0-llm-benchmark/progress.md` (git-ignored).
- The model is Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, 2,588,147,712 bytes, SHA-256 `181938105e...a63c`), pinned in `app/src/main/assets/models.json`. It lives in the app's private storage on the test phone.

## Commands that matter on this Windows machine

- Gradle needs a short temp folder or it fails with "Unable to establish loopback connection": in bash `export TEMP='C:\tmpjava' TMP='C:\tmpjava'` (folder `C:\tmpjava` must exist).
- Full check: `./gradlew :models:test :agent-core:test :downloader:test :eval:test :eval:gate :storage:testDebugUnitTest :app:assembleDebug :bench:assembleDebug :app:verifyManifests`, then `python3 scripts/check-tests-ran.py` and `bash scripts/check-network-use.sh`. Instrumented tests (phone connected): `./gradlew :storage:connectedDebugAndroidTest`.
- adb: `C:\Users\Gowrishankar\AppData\Local\Android\Sdk\platform-tools`; in git bash use `export MSYS_NO_PATHCONV=1` for `/sdcard` paths.
- Large heredocs in the bash tool sometimes fail; write files with the file tool and run small edit scripts instead.
- Phone helpers in `scripts/phone/`: `ui.py` (reads the screen, taps by text, only acts when the Bheem app is in front), `drive.py` (`say "text" "wait for text"`, `tap OK`, `notes` lists Bheem notifications, `texts`), `pend.py` (lists pending Bheem alarms with their times). They expect Python and the adb path above.

## Phone facts learned (Moto g86 power, Android 16)

- After `adb reboot` the phone does not show in `adb devices` until it is unlocked once; the first unlock sends the boot broadcast to the app.
- `KEYCODE_SLEEP` locks the phone, so the app cannot be driven afterwards. Create all test reminders first.
- Deep Doze: `force-idle` stops at `QUICK_DOZE_DELAY`; fake-unplug the battery, screen off, wait about 10 minutes, then `dumpsys deviceidle step deep`. The delay restarts if the screen wakes.
- A vendor component ("aitune") starts the app about a minute after a force-stop.
- `pm clear` also deletes the app's external files folder.

## Direction set by the owner on 2026-10-27: the harness first

On-device models launch every day, so Bheem stays model-agnostic: brain changes, body never. The next session starts with the currently wired model (Gemma 4 E2B on LiteRT-LM) and works on the harness: stability, speed, better tool calling that holds for any model, memory, features, product direction and the interface. A new model is judged only through the eval harness and the gate (see `docs/MODELS.md`, "Rules for swapping a model"), not by vendor benchmarks, and a new runtime adapter is added only when the owner asks. Candidate scope for that session, to be agreed with the owner at its start: (1) tool calling and routing: more deterministic routing and validation in front of the model, richer typed actions and arguments, an eval set that grows from real failures, and a swap test that runs two models through the same gate; (2) speed: the voice path (release to result is 12 to 14 s for 6 to 9 s of speech against a 6 s target) and model load time; (3) stability: the open Stage 5, 7 and 8 items below; (4) memory: what Ask can answer, better recall over notes and tasks; (5) the interface and the feature list. Stage 11 below still stands and can run alongside or after.

The advisor's plan for this track (Stages 12 to 17, with Stage 11 after Stage 13) is in `docs/DIRECTION-NEXT.md`. The owner approved the order on 2026-11-03: Stage 12, then Stage 13, then Stage 11. Start with Stage 12.

## Next: Stage 11 (pre-release hardening)

Stages 1 to 10 are built, reviewed and pushed. Stage 11 is in `docs/PRODUCTION-DIRECTION.md`: a written capture-to-card list of 40 cases (typed and spoken, model cold and warm, screen locked mid-load, airplane mode, low storage, permission revoked mid-flow) run on the phone with logcat and zero uncaught crashes; `dumpsys batterystats` over 24 hours of normal use with idle unload on; peak RSS during a voice command; a clean-phone install from the release page; the on-phone eval before tagging; every number labelled measured or target. Start by reading `docs/V001-PLAN.md` (the release gate) and `docs/evidence/STAGE10-RELEASE-ENGINEERING.md`.

Needs the owner:
- The release key and the one-time GitHub setup in `docs/RELEASING.md` (key, `docs/release-cert.sha256`, the `release` environment with its four secrets and a required reviewer, a tag ruleset, a protected `main`). Without them the release workflow cannot run, so the clean-phone install from a release page cannot be done.
- The phone connected, unlocked, screen kept on, and the owner not touching it during timed runs. The 24 hour battery run needs the phone left alone for a day.
- The application id stays `app.bheem.bench` for now; it must be final before the first public tag.

Open items carried forward:
- Release to result for voice is 12 to 14 s for 6 to 9 s of speech (target 6 s).
- The model-busy reminder delay re-measure (Stage 5).
- TalkBack recording and Accessibility Scanner (Stage 8) are not done.
- Done 2026-11-03: the schema version 2 rebuild path is removed and an instrumented test shows an older-layout database is refused with its rows intact. Still to do when the first schema change comes: a real migration plus a fixture of the shipped layout.
- Done 2026-11-03: Gradle dependency hashes (`gradle/verification-metadata.xml`); not yet confirmed on CI (Linux).
- Dependency hash verification for Gradle; reproducibility is shown only on one Windows machine.
- Deferred review minors are listed at the end of each stage evidence file.
- The phone has the release-signed build (a debug install needs an uninstall first, which deletes app data and the sideloaded model; the model is in `models-local/gemma-4-e2b/`, about 80 s to push back).
- Open the `scripts/release/` tests with `python -m unittest discover -p "test_*.py"` from that folder.

Earlier open items: `versionCode` now comes from the tag; F-Droid is not promised (the LiteRT-LM licence is unchecked).
