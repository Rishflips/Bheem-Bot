# Handoff: where Bheem stands and how to pick it up

Updated 2026-11-03 at a pause in the middle of Stage 11 (first written 2026-10-25 in Stage 5). Read `AGENTS.md`, `docs/PRODUCTION-DIRECTION.md` (the 11 stages) and the evidence file of the stage in progress first.

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
| 12 Model contract and swap scorecard | Done, Opus-reviewed and fixed, pushed (docs/evidence/STAGE12-MODEL-CONTRACT-AND-SCORECARD.md). Fresh phone scorecards: Gemma reproduces the goldens; both LFM2.5 builds fail every swap row with the shipped prompt (only the shipped prompt could be measured). The full English command set still expects payment actions the app refuses, so it scores about 85% on prompt v3.8: the expectations need a decision |
| 13 Voice and load speed | Partly done, pushed (docs/evidence/STAGE13-VOICE-AND-LOAD-SPEED.md). Done: 48-token cap and newline stop on transcription, speech-step timing in the voice log, a prefix-reuse probe. Measured: ten warm voice presses, median 5.3 s (6 s target met), worst 11.9 s (8 s limit not met; the slow ones were 7 to 8 s clips); LiteRT-LM 0.18.1 does not reuse prefill, so moving "now" is dropped. Open: CPU versus GPU voice study, idle window, first token in the card header, the slow tail |
| 11 Pre-release hardening | In progress (docs/evidence/STAGE11-CASE-RESULTS.md). Done: Gradle dependency hashes (CI-confirmed on Linux), schema version 2 rebuild path removed with phone tests, the 40-case list (docs/STAGE11-CASE-LIST.md) and its adb runner. 31 of 40 cases pass, C33 fails (a quick double tap on OK undoes the reminder), 8 not run. Found and fixed: message banners hidden above a scrolled list. Open: the 8 owner cases, battery, RSS, clean-phone install, release setup |

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

## Next: finish Stage 11 (pick up here)

State at the pause (2026-11-03): everything is pushed up to 562e57f on `phase0-benchmark`; check CI on the latest push first (`gh run list --repo Rishflips/Bheem-Bot --branch phase0-benchmark`; `gh` is signed in as Rishflips and `gh auth setup-git` was run, so `git push` works). Stages 1 to 10 and 12 are done; 13 is partly done; 11 is in progress.

Do next, in this order:
1. **Owner cases at an unlocked phone** (about 30 to 40 minutes, the owner holds the phone): C14 screen lock mid-load, C28 Doze, C31 Edit on a spoken card, C36 reboot with a pending reminder, C37 to C40 voice (cold, warm 3 s, silent and very short, very long and cancel). C14 and C28 are scripted in `scripts/phone/cases.py` but need the screen lock opened by hand; the phone has a screen lock that adb cannot dismiss. Then add the results to `docs/evidence/STAGE11-CASE-RESULTS.md`.
2. **Owner decision on C33**: a delay before Undo accepts a tap, so a double tap on OK does not undo the reminder (not changed yet).
3. **Owner decision on the English command set**: change the expectations for payment requests (to no action or a refusal) and refresh its goldens on v3.8, or leave it unscored.
4. Stage 13 leftovers: CPU versus GPU voice study, the idle window, first token in the card header, explain the slow 7 to 8 s presses (the voice log line now has the speech step).
5. Stage 11 measurements still to take: peak RSS during a voice command (`dumpsys meminfo`), the 24 hour battery run with idle unload, the Stage 5 busy-model reminder delay, the TalkBack recording and Accessibility Scanner (Stage 8).
6. Owner setup (below), then a clean-phone install from a release page and the case list on the signed release build.

Phone state: it has the offline debug build (not the release-signed one) with the Gemma model already in the app's private storage. Run the scripted cases with `python scripts/phone/cases.py all` (set `PYTHONIOENCODING=utf-8`); a list of case ids runs a subset. Gotchas learned: `cmd appops set --uid` is needed to change the exact-alarm permission; `bench-device.ps1` now starts the bench with `am start -S`; the Talk button has no text, only the description "Hold to talk"; Gradle on this machine needs `TEMP=C:\tmpjava TMP=C:\tmpjava`.

Needs the owner:
- The release key and the one-time GitHub setup in `docs/RELEASING.md` (key, `docs/release-cert.sha256`, the `release` environment with its four secrets and a required reviewer, a tag ruleset, a protected `main`). Without them the release workflow cannot run, so the clean-phone install from a release page cannot be done.
- The phone connected, unlocked, screen kept on, and the owner not touching it during timed runs. The 24 hour battery run needs the phone left alone for a day.
- The application id stays `app.bheem.bench` for now; it must be final before the first public tag.

Open items carried forward:
- Release to result for voice: median 5.3 s over ten warm presses, worst 11.9 s (Stage 13); the 7 to 8 s clips are slow and unexplained.
- The model-busy reminder delay re-measure (Stage 5).
- TalkBack recording and Accessibility Scanner (Stage 8) are not done.
- Done 2026-11-03: the schema version 2 rebuild path is removed and an instrumented test shows an older-layout database is refused with its rows intact. Still to do when the first schema change comes: a real migration plus a fixture of the shipped layout.
- Done 2026-11-03: Gradle dependency hashes (`gradle/verification-metadata.xml`), confirmed on Linux CI.
- Reproducibility of the release build is shown only on one Windows machine.
- Deferred review minors are listed at the end of each stage evidence file.
- A release-signed build needs an uninstall of the debug build first, which deletes app data and the model in private storage (the model is in `models-local/gemma-4-e2b/`, about 80 s to push back).
- Open the `scripts/release/` tests with `python -m unittest discover -p "test_*.py"` from that folder.

Earlier open items: `versionCode` now comes from the tag; F-Droid is not promised (the LiteRT-LM licence is unchecked).
