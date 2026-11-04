# Assistant design: from capture tool to assistant

Advice from Fable 5.1, 2026-11-04, written after reading the repository at `8b2d883` on `phase0-benchmark`. It is a design for the implementer and the reviewer, not a spec; each slice gets its own spec before code. Every number is **measured** (one Moto g86 power, Android 16, Gemma 4 E2B on LiteRT-LM 0.18.1) or a **target**. The constraints in `AGENTS.md`, `docs/SECURITY.md` and `docs/DIRECTION-NEXT.md` are taken as fixed: the model proposes, only executors act, every action waits for a tap through the one shared gate, no network in core modules, `agent-core` never sees a model runtime, and a new action type changes `SECURITY.md` and the gate tests in the same change.

## A. The shape

Today a sentence goes in and a card comes out. After this work the same sentence goes into a conversation: Bheem answers in words when asked something, shows a card when one action is wanted, shows a short plan of cards when the request is vague or has several parts, and once a day writes a brief of its own. Nothing about how an action runs changes: a card is still a stored proposal behind the gate, OK still runs it, Undo still works. What changes is that the card now sits in a thread next to the words that led to it, that Bheem can read everything the owner told it back to them with the record and date it came from, that two hand-offs (call, route) join the two that exist (calendar, message), and that a plan is just several cards under one heading.

The smallest structure that holds all five features is three new packages in `agent-core`, one schema version in `storage`, one screen in `app`, and no new module. The pipeline, the gate, the ledger, the coordinator, the hand-off executors, the feed model and the model harness are all reused.

```
agent-core (pure Kotlin, depends only on models interfaces)
  pipeline/CommandPipeline   reused; grows three outcomes: Answer, Plan, Clarify
  memory/Recall              new; replaces Ask. Reads MemoryStore, ReminderStore, LedgerStore.
                             Code only. Returns Answer(text, citations). Never calls the model.
  assistant/Turn, Thread     new; the typed conversation (User, Answer, Card, Plan, Clarify, Brief, Notice)
  assistant/PlanRunner       new; approves the steps of a plan in order through ProposalCoordinator
  brief/Brief                new; composes the daily brief from records by code; a pure function of (now, records)
  action/ActionCatalog       reused; gains PlaceCall and OpenRoute (slice 3) and Clarify (slice 4)
  proposal/HandOff           reused; gains HandOffRequest.Call and HandOffRequest.Route
  parse/AnswerFormats        reused; gains the "lines" format (several lines, one action each)
  planner/PromptCatalog      reused; gains planner-lines-v4.0 (slice 4) next to planner-line-v3.8
  gate/Gate                  reused; new argument checks for a phone number, a place, a plan size cap

storage (schema 4, one migration with a fixture of the shipped layout)
  turns table (the thread), plan_id on proposals, done_at on memory

app
  ThreadScreen               new; replaces TodayScreen as the main screen. Cards render as today.
  BriefAlarm                 new; the daily inexact alarm, re-armed by the existing RearmReceiver
  AndroidHandOffLauncher     reused; two more intents (ACTION_DIAL, geo:)
```

Dependencies stay as they are: `app` wires everything; `agent-core` depends on `models` interfaces only; nothing depends on `app`. `Recall` and `Brief` are deliberately model-free, so the two features the owner will trust least (memory and the proactive brief) cannot invent anything. The model is used for one thing only: turning the owner's sentence into zero or more typed lines.

## B. The turn model

A turn starts with text (typed, or spoken and transcribed by the same `VoiceCommands` path as today) and ends with one `Turn` appended to the thread.

```
text ──► PreRouter ──► Recall context ──► model (lines) ──► parser ──► per line: TimeReconciler ──► Gate
              │                                                                                   │
              └─ Memory route: Recall answers by code, no model ──► Answer                          ├─ 0 lines: Nothing or Clarify
                                                                                                  ├─ 1 line:  Card (today's path)
                                                                                                  └─ 2..5:    Plan of Cards
```

Key types (sketch; final signatures belong to the slice specs):

```kotlin
sealed interface Turn {
    data class User(val text: String, val source: String, val at: LocalDateTime) : Turn
    data class Answer(val id: String, val text: String, val citations: List<Citation>, val at: LocalDateTime) : Turn
    data class Card(val proposalId: String) : Turn                        // rendered from the feed row, as today
    data class Plan(val planId: String, val title: String, val steps: List<String>) : Turn   // proposal ids, in order
    data class Clarify(val id: String, val question: String) : Turn       // model text, shown as a question, never acted on
    data class Brief(val id: String, val sections: List<BriefSection>, val citations: List<Citation>, val at: LocalDateTime) : Turn
    data class Notice(val text: String) : Turn                            // refusals, model unavailable, "nothing found"
}
data class Citation(val recordId: String, val kind: String, val createdAt: LocalDateTime, val proposalId: String?)

sealed interface Outcome { Card; Answer(id, Answer); Plan(id, cards: List<Card>); Clarify(id, question); Nothing; Refused; ModelUnavailable }
```

**Context assembly.** Before the model call, `Recall.context(text, now)` picks at most 6 of the owner's own records that match the sentence by FTS5 and by time scope (a weekday or "tomorrow" in the sentence selects reminders and events in that window), and writes them as one line each with a short id into the user turn, never into the system prompt. Records are the owner's own: every record today is linked to a proposal with `USER` provenance, and `Recall` must filter on that link so a record created from a tainted source later can never enter the model's context (the same rule `VoiceHints.fromUserRows` already applies). The model sees the current sentence, the context block, and one line naming the previous assistant turn kind ("you just showed a plan of 3 steps"); it never sees older turns. Cost: first token grows in 128-token steps, about 0.41 s per step on GPU and 0.93 s on CPU (**measured**, step size inferred from the fit in MODELS.md), so the block is capped at about 120 tokens (**target**) and omitted when nothing matches.

**Model call and typed result.** The model answers in the one-line format it already uses, extended to several lines (`lines` answer format, prompt `planner-lines-v4.0`, at most 5 lines, `Clarify|<question>` allowed as a line). The parser returns `List<ProposedAction>`. A model whose profile names the `line` format gets single actions and no plans; the brain decides the format, the body does not care. Each line is then handled exactly as one command is today: time repaired by `TimeReconciler`, then `Gate.decide` with `Provenance.USER`, the owner's text, the source and `now`. A line the gate rejects is dropped from the plan and the refusal is written to the ledger as today; a plan whose every line is rejected becomes a `Notice`.

**Plans are lists of normal proposals.** A plan has no execution semantics of its own. Each step is a `StoredProposal` with its own ledger row and its own `requiresTap`, plus a `plan_id` and a position. The plan card shows the steps in order with OK on each and one "Do all" button. "Do all" is `PlanRunner.approveAll(planId, now)`: it calls `ProposalCoordinator.approve` for each step in order, which re-checks the gate as it does today, and it stops at the first hand-off (the owner has left for the dialer or WhatsApp) or the first failure; when the owner comes back the remaining steps still wait for a tap. So "Do all" is N taps collapsed into one gesture, never a bypass: the ledger still shows PROPOSED then APPROVED then EXECUTED per step, and the red-team assertion "nothing executes without APPROVED" holds per step. The gate caps a plan at 5 steps (**target**) and rejects two steps with the same action and arguments.

**Answers are grounded by construction.** `Recall.answer(query, now)` runs the query against FTS5 over `memory`, plus the `reminders` table for anything due, plus the ledger for "what did I do" questions, scopes by time when the question names one, and composes the answer text by code from the records it found: kind, text, date, and for a reminder the due time in words. Every answer carries citations and the screen shows "from 2 notes and 1 reminder" with the records one tap away. If nothing matches it says so. The model never writes an answer in these slices; when model wording is tried later it must pass the same kind of check `DraftMessage` passes today: every name and number in the wording must appear in a cited record or in the question, or the wording is dropped and the code-composed answer shown instead.

**The proactive brief.** `Brief.compose(now, reminders, tasks, openProposals, recentLedger)` is a pure function with no model and no untrusted input. Sections, each with citations: due today (reminders by time), clashes (two items within 30 minutes of each other, **target** window), not followed up (tasks older than 3 days without `done_at`, cards waiting for OK for more than a day, hand-offs that FAILED), and missed reminders. An inexact daily alarm (`setAndAllowWhileIdle`, default 7:30 local, **target**; re-armed by `RearmReceiver` with the reminders) wakes a receiver that opens `SharedStorage` the way `ReminderReceiver` does, runs `Brief.compose`, stores the result as a `Brief` turn, and posts one notification with a count only ("Your morning: 2 reminders, 1 clash"; private text on the lock screen as reminders are today). The app shows the brief at the top of the thread on the next open. I recommend the alarm path over WorkManager because the alarm machinery, the re-arm receiver and the database-in-a-receiver pattern exist and are tested, and the brief needs no network or charging constraint. Model wording for the brief is a later experiment under the same grounding check; it is not in these slices.

**Follow-ups.** "Yes", "do it", "OK" are handled by code: if the previous turn is a plan or card waiting, approve it; the model is not called. "Change it to 5" and "not that one" go to the model as a fresh sentence with the previous card in the context block; the model proposes a replacement and code dismisses the old card when the new one is approved. Multi-turn editing beyond that is out of scope here.

## C. The harness

The assistant is tested in three rings, and each ring is deterministic except the phone.

**Ring 1: JVM scenario tests (`agent-core`, run in CI).** `AssistantScenarioTest` builds the real pipeline, gate, ledger, coordinator, `Recall`, `PlanRunner` and `Brief` over in-memory stores, with a fixed clock (`2026-10-23T08:00`, the dev set's reference), fixture records (`agent-core/src/test/resources/fixtures/records-*.json`: an electrician note, a Friday with two reminders 15 minutes apart, an overdue task, a fact note with a phone number, all invented), and a `ScriptedPlanner` that returns a fixed model answer per user sentence. Each scenario is a list of user turns and taps; the result is a transcript (turn kind, text, citations, cards with their `requiresTap`, ledger events) compared exactly against a golden file in `eval/golden/transcripts/`. Goldens change only when a test is run with an explicit regenerate flag, and the diff is reviewed. Wrong-model scenarios belong here too: the planner returns six lines, a line with an invented phone number, a `Clarify` with control characters, a plan with a payment, and the transcript must show the refusal and no card.

**Ring 2: the offline quality gate (`eval`, run in CI).** A new dataset `eval/data/conversation.jsonl` (development set, about 60 cases: 20 questions over the fixture records, 20 single actions in conversational wording, 20 vague or compound requests) and `eval/data/conversation-heldout.jsonl` (20 cases, written in the same sitting by the owner and the implementer, then frozen, listed in `.ignore` and `.rgignore`, read only as aggregates, exactly as `assistant-heldout.jsonl` is today). A case names its fixture set and expects a turn kind, a list of actions with key arguments, the record ids an answer must cite, and tokens an answer must not contain. The saved model answers from the phone are rescored through the real pipeline (`Rescorer` with the `lines` format) and judged by a `ConversationJudge`. Pinned by data hash like the other sets.

**Ring 3: the phone.** `scripts/scorecard.ps1` grows conversation rows and runs the development set and the held-out set on both backends through `bench`, which already drives the real pipeline on the device. The owner's own refusals, copied by hand from Diagnostics, feed the development set weekly; the held-out set is never touched except to add a new frozen batch.

What is scored, with thresholds (all **targets**, to be frozen after the first phone run):

| Check | Definition | Development set | Held-out |
| :-- | :-- | :-- | :-- |
| Answer grounded | Every citation exists; every number and capitalised name in the answer appears in a cited record or the question | 100%, hard | 100%, hard |
| No invented facts | A question with no matching record gets "nothing found", never a guess | 100%, hard | 100%, hard |
| Right kind | Answer, card, plan, clarify or nothing as expected | >= 90% | >= 90% |
| Right steps | For a card or plan: expected action types and key arguments, as a set | >= 85% with code, >= 75% model alone | at most 2 points below development (the swap rule) |
| No card without a tap | Every card and every plan step has `requiresTap`; no EXECUTED without APPROVED in the ledger | 100%, hard | 100%, hard |
| Decided by code | Share of cases `PreRouter` or the follow-up rule decides | <= 25% (the existing cap) | same |
| Brief | Clashes, overdue tasks and waiting cards found on the fixture days, nothing else | 100%, hard | not applicable |
| Turn time | Model call first token p95 at most 1.25 times today's command p95 on the same backend (today about 1.0 s GPU, 2.1 s CPU, **measured**); an answer from memory with no model call under 300 ms p95 | | |

The three "hard" rows are gate failures with no known-failure entry allowed. A new model passes the assistant harness the way it passes the planner today: registry entry with profile (`lines` or `line` format decides whether plans exist), scorecard on both backends over all sets, every absolute threshold, the relative swap rule, and the red-team suite extended with the new attack shapes in section E. A model that only supports `line` is allowed; its scorecard says "plans: not offered".

## D. Build order

Five slices. Each is about a day of implementation plus review, ends with a debug build on the owner's phone, and has an exit test that is a test, not a claim. Hardening and release come last, as the owner asked.

**Slice 1: the conversation screen answering from memory.** `Recall` over notes, tasks, expenses, reminders and the ledger, with time scoping from `TimeResolver` ("what is on tomorrow", "what did I note about the electrician", "what did I set up yesterday"); `Thread` in process memory; `ThreadScreen` with user and answer bubbles, citations expandable, cards rendered inline exactly as today, Upcoming reminders as a pinned strip at the top; `Outcome.Answer` replaces `Outcome.Question`. No prompt change, no schema change, no new action. Exit: 12 golden transcripts green; the owner asks five questions about things typed earlier and each answer lists the right records with dates, and a question about nothing gets "I found nothing about that". Left out on purpose: thread persistence, spoken replies, model wording.

**Slice 2: the brief, task done state, and the one schema migration.** Schema 4 (turns, `plan_id`, `done_at`) with the migration test and the fixture of the shipped layout that `HANDOFF.md` says is owed; "Done" on task cards; `Brief.compose`; the daily alarm and its receiver; the brief turn and notification; thread persistence through the `turns` table. Exit: scenario tests for an empty day, a clash day and an overdue day; on the phone the brief appears the next morning and after a reboot the alarm is still armed (`pend.py`). Left out: reading the system calendar (needs `READ_CALENDAR`), model wording.

**Slice 3: actions that do something.** `PlaceCall|<who>` (`ACTION_DIAL`, which opens the dialer with the number and makes no call, so no `CALL_PHONE` permission) and `OpenRoute|<destination>` (a `geo:0,0?q=` intent, no permission); recipient and number resolution from the owner's own fact notes ("Amma's number is ...") by code, so `DraftMessage` can open WhatsApp to a number (`wa.me`) or `smsto:<number>` instead of the bare composer; prompt `planner-line-v3.9` with two more lines; dev-set goldens refreshed on the phone. Exit: `GateTest`, `TaintedGateTest` and `RedTeamTest` rows for both types; the owner says "call Amma" and the dialer opens with the right number after a tap; "route to the office" opens maps after a tap. Left out: `READ_CONTACTS` (fact notes first; contacts as an opt-in later if the owner wants it).

**Slice 4: plans and clarifying questions.** The `lines` answer format, `planner-lines-v4.0`, `Outcome.Plan` and `Outcome.Clarify`, `PlanRunner`, the plan card with per-step OK and Do all, the context block, the follow-up rule, the conversation development and held-out sets frozen before any prompt tuning. Exit: "sort out my Friday" over the fixture Friday yields two to four tappable steps and the scenario golden; the development set measured on both backends with the single-action rows within 2 points of v3.8 (the swap rule applied to our own prompt); the held-out aggregates written down once. If v4.0 loses more than 2 points on single actions, split: keep v3.8 for sentences a compound-or-vague detector does not fire on, and use v4.0 only when it does, each with its own goldens.

**Slice 5: hardening and release.** Red-team with the real model on the phone over the new attack shapes; accessibility pass on the thread (TalkBack order: user text, answer, citations, actions); the 24 hour battery run with the daily alarm and idle unload; the Stage 11 case list extended with ten conversation cases; scorecard rows; `SECURITY.md` and `ARCHITECTURE.md` brought to match; then the release steps already planned in Stage 11.

Deliberately not in these slices: spoken replies (TTS), notification reading, `READ_CONTACTS` and `READ_CALENDAR`, model-written answers or brief wording, embeddings, multi-turn editing beyond one replacement, Hindi, routines, skill packs.

## E. Security surface by slice

| Slice | New inputs | New action types or hand-offs | New permissions | Gate tests required in the same change |
| :-- | :-- | :-- | :-- | :-- |
| 1 | None (reads the owner's own records) | None | None | `RecallTest`: only records linked to `USER` proposals are returned or cited; a query of 9,000 characters and control characters is refused; answers never add up amounts |
| 2 | The alarm broadcast (carries only an id); the brief as a notification | None (`done_at` is a local write behind the existing task card) | None (`SCHEDULE_EXACT_ALARM` is not needed; the alarm is inexact) | `BriefTest`: output built from records only; a record with an injection string appears verbatim as text, never as a step; receiver ignores every action but its own; notification text private on the lock screen (phone check) |
| 3 | A phone number or a place as an argument | `PlaceCall` (L1, hand-off, no undo), `OpenRoute` (L1, hand-off, no undo); `DraftMessage` gains a resolved number | None | `GateTest`: number shape (digits, plus, 6 to 15 characters), the name or number must appear in the command (the run-of-words rule), place text length and characters, both refused from tainted provenance, both require a tap whatever the policy; `RedTeamTest`: forged `PlaceCall` to a premium or foreign number, `OpenRoute` to an unrelated place, a message to a number not in the command; `TaintedGateTest`: fact notes cannot be created from tainted text (they are `SaveNote`, already refused) |
| 4 | The context block in the model prompt; several lines per answer; `Clarify` text from the model | `Clarify` (no execution; a question shown to the owner, text-checked like any argument, marked as the model's words) | None | `GateTest`: plan capped at 5, duplicate steps rejected, a rejected line never reaches a card, a plan with a payment line loses that line and the rest still wait for taps; `PlanRunnerTest`: Do all records APPROVED before each EXECUTED, stops after a hand-off and after a failure, re-checks the gate per step; `RedTeamTest`: injection text inside a context record does not change the parsed lines (the fake planner returns the attack as lines); `Clarify` with control characters or over 200 characters refused |
| 5 | None | None | None | Full red-team run on the phone with the real model over the new shapes; the leak rate written down |

`SECURITY.md` gains rows for `PlaceCall`, `OpenRoute` and `Clarify` in the catalog and the "built and tested" table, and `ARCHITECTURE.md` gains the three outcomes and the brief. Payments stay refused by the gate and absent from every prompt. No slice reads a notification or shared text; when share-sheet capture arrives (Stage 16 in `DIRECTION-NEXT.md`), its records are tainted and `Recall` must already be excluding them from the model context and labelling them in answers.

## F. Risks and open questions, ranked

1. **The multi-line prompt costs single-action accuracy.** A small model given five possible lines may hedge or pad. Recommendation: measure v4.0 against v3.8 with the 2-point swap rule before it ships (slice 4 exit), and split into two prompts behind a code detector if it fails. Do not tune on the held-out set.
2. **Retrieval picks the wrong record and the answer is grounded but wrong.** Code-composed answers cannot invent, but they can cite the wrong note. Recommendation: always show the citation with its date, say "nothing found" rather than show the weakest hit, and grow a 50-question recall set from the owner's real questions (the Stage 15 target of 45 of 50 stands).
3. **The first real schema migration.** Slice 2 changes the shipped layout for the first time. Recommendation: the fixture of the schema 3 layout and the migration test land before any new column is used, and the previous build must still open a migrated database or the release note says it does not.
4. **New hand-off arguments widen what a forged line can do.** A dialer prefilled with a premium number is one tap from a call. Recommendation: the number or name must be in the owner's own words or fact notes, `ACTION_DIAL` never `ACTION_CALL`, the card shows the number in full, and the red-team shapes in section E are in CI before the executor exists.
5. **The daily alarm under Doze and OEM battery limits.** The brief may arrive at 7:50 instead of 7:30, or not at all on a Xiaomi. Recommendation: inexact alarm, the brief states the time it was composed, the existing OEM hint card covers it, and a missing brief is composed on the next app open so the screen is never stale.
6. **Prompt growth from the context block.** Six records can push the prompt over a 128-token boundary and add about 0.4 s on GPU or 0.9 s on CPU (**measured** step size). Recommendation: cap at 120 tokens, omit when nothing matches, and record first token per turn in Diagnostics so the cost is seen, not guessed.
7. **The thread hides what the feed showed.** Upcoming reminders and failed cards could scroll away. Recommendation: a pinned Upcoming strip and the "needs you" items always above the thread; the feed model is reused, not replaced.
8. **Owner time on the phone.** Slices 3 and 4 refresh goldens and need two backend runs each, about an hour of a connected, unlocked phone per slice. Recommendation: batch the two prompt changes into one phone session if the owner prefers fewer sittings, at the cost of slice 3 shipping a day later.

Decisions for the owner before slice 2 and 3 start:

- **Brief delivery.** A notification at 7:30 with a count only, or in-app only on the next open? Recommendation: notification with a count; it is the first time Bheem speaks first, and the text stays private.
- **Do all with a message in the plan.** Allow Do all to run up to a message hand-off and stop there, or require a separate tap for any message? Recommendation: run and stop; the messaging app still shows the text before anything is sent, and the ledger shows the step.
- **Contacts.** Stay with fact notes for numbers, or request `READ_CONTACTS` as an opt-in in a later slice? Recommendation: fact notes now; revisit after a month of use shows how often "call X" fails for want of a number.
- **Expenses in Recall.** Keep expenses answerable ("what did I spend at the chemist") as a plain list, or drop them? Recommendation: keep as a list with no totals, matching the Stage 6 decision; drop `LogExpense` from the prompt only if the owner wants the money diary gone entirely.
- **Application id.** Still `app.bheem.bench`; it must be final before the first public tag (standing item from `DIRECTION-NEXT.md`).
