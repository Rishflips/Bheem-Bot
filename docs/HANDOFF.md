# Handoff: where Bheem stands and how to pick it up

Written 2026-10-25 at a pause in Stage 5. Read `AGENTS.md`, `docs/PRODUCTION-DIRECTION.md` (the 11 stages) and the evidence file of the stage in progress first.

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
| 5 Reminders and notifications | Code and receiver fix committed locally (606465e, c69078c, 58c7a07), not pushed; phone tests done except deep Doze; Opus review not started |
| 6 to 11 | Not started (see PRODUCTION-DIRECTION.md) |

## Stage 5: what is left

1. Deep Doze is unproven and WAIVED by the owner on 2026-10-26 (two tries ended by a fingerprint wake). Keep it listed as not done; skip this step unless the owner asks. Original notes:  The 04:22 try fired on time but a fingerprint wake at 04:15:06 had ended deep idle before it. Repeat with nobody touching the phone: unlock once, create a reminder due in about 28 minutes, `dumpsys battery unplug`, screen off (`KEYCODE_SLEEP`), wait about 10 minutes, `dumpsys deviceidle step deep` until `get deep` is IDLE, do not touch the phone, check the notification `when=` after the due time, then `dumpsys deviceidle unforce` and `dumpsys battery reset`. If the owner would rather skip it, record it as waived like the Stage 3 cold loads.
2. The phone state was reset at the end of this session (`deviceidle unforce`, `battery reset`); exact alarms are allowed. Check with `adb shell appops get app.bheem.bench SCHEDULE_EXACT_ALARM`.
3. Everything is committed except the edits made after 58c7a07 (this note and the evidence text). Commit them.
4. Run the full verification (below) and then ask for the Opus review of stage 5: `a492401..` is stage 4, so review `534c378..HEAD`. Review focus from the direction document: re-arming before the database key is available; database work in a receiver on the main thread (the receivers use `goAsync` and a background coroutine); `FLAG_UPDATE_CURRENT` reusing stale extras; a notification tap landing on a blank screen; the catch-up path firing hundreds of alarms after a long power-off. Also ask the reviewer about: the race between `rearm` on app start and the receiver, two `ReminderServices` instances sharing one `Storage`, and the 2 minute grace window.
5. Fix the review findings test-first, ask the owner before pushing.

Known gaps to decide on (not blockers): the notification opens the app but not the specific reminder; no screen lists missed reminders; no snooze or recurrence (out of scope for stage 5); timezone change keeps wall-clock time but was only tested in JVM tests.

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

## Next after stage 5

Stage 6: executors, failure and undo, Ask, planner fixes (see PRODUCTION-DIRECTION.md). Open items from earlier: app id is still `app.bheem.bench`; `versionCode` is 1 (a release must raise it so the model is re-hashed after an update); decide about F-Droid and the LiteRT-LM library; the voice work (Moonshine or Gemma audio) is not started.
