# Personal agent baseline, slice 0: routing (2026-11-04)

The "before" picture for `docs/PERSONAL-AGENT-DESIGN.md`. It measures **where each sentence goes** in today's build, with no model. It does **not** measure how good the model's chat replies are: that part is not run yet (see the end). Numbers below are **measured**, from `./gradlew :eval:conversations` in the owner's instabox sandbox on commit `048938d`; nothing ran on the owner's computer.

## What was built

- `eval/data/conversations.jsonl`: 30 development conversations. `eval/data/conversations-heldout.jsonl`: 10 held-out conversations, written in the same sitting and listed in `.ignore` and `.rgignore` at once; they are only ever counted per group (no ids or text are printed or stored).
- `eval/fixtures/memory-seed.json`: invented owner "Gowri" in Chennai, six notes, five reminders, a fixed clock (Wed 4 Nov 2026, 19:42 IST) and a device snapshot for later slices. No real data.
- `eval/.../conversation/ConversationRunner.kt`: plays a conversation through the real `CommandPipeline`, `Gate`, `Recall`, `SmallTalk`, `PhoneBook`, `FollowUp` and `Clarifier` in the order `AppController` uses. The planner returns the line the case scripts (what a good planner would say) or `NoAction`. A chat turn records the exact text the model would receive (`ChatPrompt.SYSTEM` plus `ChatText.message`).
- `ConversationScorer`: checks path, action, arguments and `must` / `must_not` text of code-written replies. Turns on the chat path whose checks need the model's words are counted as **needs model**, not failed. `JVM tests: ConversationRunnerTest` (9 tests, passed in the sandbox).
- Task: `./gradlew :eval:conversations` writes `eval/results/conversations-routing.json` (development detail) and `.md` (counts).

## Result

| Set | Turns | Passed | Needs model | Passed among turns a script can judge |
| :-- | --: | --: | --: | --: |
| development | 39 | 19 | 8 | 11 of 31 (35 percent) |
| held-out (aggregate only) | 11 | 6 | 2 | 4 of 9 (44 percent, a small sample) |

Development by group (turns, passed, needs model): small talk 3, 3, 0; date and time 5, 0, 0; device state 5, 0, 0; memory recall 6, 2, 0; persona 5, 5, 4; consistency 8, 5, 3; refusals 4, 4, 1; tool selection 3, 0, 0. "Passed" includes the turns that only await the model's reply (their route was right), which is why the last column is lower.

## What the numbers say (development turns that failed)

1. **Time, date, battery, storage, alarm: 10 of 10 turns fail.** Each goes to the model as a general question, and the chat prompt tells the model it cannot check the time. "What is today's date" is worse: it is taken for a records question and goes to `Recall`. There is no route for any fact the phone knows.
2. **Arithmetic ("15 percent of 2400"): 2 of 2 fail,** same cause: no code route, so a 2B model does the sum.
3. **Memory about the owner: 4 of 6 fail.** "Where do I live" and "what is my name" go to chat (there is no profile store and the model never sees notes); "what is Amma's number" goes to chat; "call her" is refused (most likely because "her" is not a saved name; the refusal reason was not read). The schedule, electrician-note and reminder questions pass because fixed patterns exist for them.
4. **Device actions: 3 of 3 fail** (torch, timer, open an app): the action types do not exist, so the sentence goes to chat.
5. **What passes:** greetings and "what can you do" (code), "can you cheer me up" and similar (reach the model), refusals (payments are refused by code), and a scripted calendar command, a reminder with a time, and "call Amma" with the number from the note.
6. **One failure is a test-data mistake, not the app:** `cv-24` turn 1 scripts a planner line that already contains a time, so the pipeline (correctly, from the line it was given) makes a card instead of asking "what time". What the real planner emits for a sentence with no time is a model behaviour that this run cannot show. The same pattern is in one sealed held-out case. Both need the real planner line from a model run before they mean anything; they were not changed after seeing the result.

## Today's chat prompt (estimate)

25 chat-path turns in the development set. The prompt sent to the model is **185 to 216 tokens (median 187), estimated as characters divided by 4** (no tokenizer on the JVM; the phone reports the real count in `Completion.contextTokens`). Almost all of it is the fixed system text. **None of it is the owner's memory, the date, the time or any device fact.** This is the number the later slices move: the design's budget is at most 640 tokens per turn, of which memory and device context are about 160.

## Not measured yet (the other half of the baseline)

- **The model's replies**: warmth, consistency, whether a reply contradicts an earlier turn, whether it claims to have acted, length. 8 development turns and 2 held-out turns wait for this (marked needs model), and the judged scores (0 to 2, by a person) have never been taken.
- Two ways to take it, both need the owner's yes: (1) the phone, through the guarded `scripts/phone/cases.py` style driver (one conversation first, process count checked, the owner holds the phone, about 30 minutes); (2) the sandbox CPU with the LiteRT-LM Python package and a 2.6 GB model download (not verified that it installs or runs there; the design lists the open questions). Sandbox replies would count for content and shape, never for timing.
- Latency per slice (first text p50 and p95) is a phone measurement.

## Corrections made while building

- The design document said 4 Nov 2026 is a Tuesday; it is a Wednesday (the phone's own screen shows "Wed 4 Nov"). The design and these cases now say Wednesday.
- The runner mirrors `AppController`'s routing order by hand. If that order changes, the runner must change with it; moving the routing into one shared `agent-core` function would remove the duplication and is a candidate for slice 1.

## After slice 1: the phone answers by code (2026-11-04, commit 4d7a4af)

`DeviceQuestions` answers the time, the date and the day, tomorrow and yesterday, days until a date, battery and charging, whether the phone is online (Wi-Fi, mobile data or offline), Do Not Disturb, free storage, the next alarm, and sums (percent of, plus, minus, times, divided by), from a `DeviceSnapshot` taken when the question is asked. The chat prompt no longer says the model cannot know the time and every chat message now starts with a `Now:` line (date, time, zone, battery, connection). Measured in the sandbox, same cases, same fixture clock:

| Set | Turns | Passed before | Passed after | Needs model | Passed among scriptable turns, before then after |
| :-- | --: | --: | --: | --: | :-- |
| development | 39 | 19 | 29 | 8 | 11 of 31 (35 percent), then 21 of 31 (68 percent) |
| held-out (aggregate only) | 11 | 6 | 8 | 2 | 4 of 9, then 6 of 9 |

Development by group, passed before then after: date and time 0 then 4 of 5; device state 0 then 5 of 5; consistency 5 then 6 of 8 (the percent question now passes); memory recall 2 and 2 of 6; tool selection 0 and 0 of 3; small talk, persona and refusals unchanged. All unit tests pass (119 classes), including 10 tests of the question patterns with a fixed clock and one that moves the clock to 31 December.

Still failing in the development set (10 of 31 scriptable turns) at that point: the follow-ups "and the day after" and "and 18 percent" (they need the previous turn, not built), four memory questions (slice 3), three device actions that do not exist yet (slice 6), and `cv-24` turn 1 (the test-data flaw above). New permission: `ACCESS_NETWORK_STATE` (a normal permission, no prompt) so the app can read whether it has Wi-Fi or mobile data; no network call is made. Not measured: how the on-screen answers look on the phone, and whether the `Now:` line changes the model's replies (needs the model-reply half of the baseline).

## After slices 2 to 6: persona, memory, guesses, offers and phone actions (2026-11-04, commit e16fdf4)

Built on branch `wip/personal-agent`: a warmer chat prompt (`chat-v2`) with the `Now:` line and a small block of the owner's confirmed facts; `RememberFact` and `ForgetFact` by code from the owner's own sentence; the What Bheem knows screen; overnight guesses that are never used until confirmed; a chat answer may end with an offer for a reminder, note, task or event that must come from the owner's own words; and the phone actions torch, timer, open an app and volume, all behind a tap. Measured in the sandbox, same cases, same fixture clock, still no model:

| Set | Turns | Passed after slice 1 | Passed now | Needs model | Passed among scriptable turns, after slice 1 then now |
| :-- | --: | --: | --: | --: | :-- |
| development | 39 | 29 | 32 | 8 | 21 of 31 (68 percent), then 24 of 31 (77 percent) |
| held-out (aggregate only) | 11 | 8 | 9 | 2 | 6 of 9, then 7 of 9 |

The gain is the three phone-action turns (tool selection 0 of 3, then 3 of 3). Memory recall is still 2 of 6 in this routing run: the runner has no stored facts, and a question such as "where do I live" now goes to the chat path with the memory block, whose answer only a model reply run can judge. `ConversationRunner` was not changed for slices 2 to 6, so it does not yet play a remembered fact into a later question.

**Verified:** all unit tests pass (125 classes, including 8 new gate tests for the six new action types, those six added to the every-action table of `TaintedGateTest`, and a stale `ChatTest` assertion fixed), the policy gate (`:eval:gate`) and both CI checks pass, and the debug APK builds.

**A real regression found and fixed on the way.** The scorecard replay (`ScorecardTest`) caught that the held-out action accuracy had dropped below its pinned 1.0 once the new pre-routes ran before the model. The cause was rules that were too broad, not a model change: "remember that I ..." and "my ..." became a fact whatever followed, and "open ..." became an app request. The fix was made from the development set and from principle, not from held-out cases: a fact must be a lasting statement (a relation, where the owner lives or works, a like or dislike, a routine, "call me", "from now on"), and anything with a request or event word (remind, task, note, today, lent, paid, meeting) is left to the normal routing; "open" never takes a name that starts like a phrase ("a note", "the day", "my notes"). The scorecard is back to its pinned numbers. The two new test groups were written after the code, so I did not watch them fail first.

**Not verified:** nothing from these slices has run on the phone (so reply warmth, speed with the longer prompt, the What Bheem knows screen, the timer and app hand-offs and the overnight job are all unchecked); no model replies were taken (the other half of the baseline); and no Opus review has been done yet. Latency numbers from earlier measurements (first text 1.1 to 1.5 s, full answer 3.2 to 4.4 s warm) were taken with the old, shorter prompt and are **not** valid for this build.
