# Personal agent design: memory, the phone as Bheem's computer, and a chat loop for a 2B model

Advice from Fable 5.1, 2026-11-04, after reading the repository on `fix/ui-rough-edges` (ahead of `phase0-benchmark`). Written for the implementer and the reviewer; each slice gets its own spec before code. Every number is **measured** (one Moto g86 power, Android 16, Gemma 4 E2B on LiteRT-LM 0.18.1; the source file is named) or a **target**. Facts from vendor pages are marked **vendor**. The boundaries in `AGENTS.md` are fixed: the model proposes typed actions and never executes; only executors run, after the gate; untrusted text goes to a no-tools call only; core modules make no network calls; a new action type changes `SECURITY.md` and the gate tests in the same change. Web access is deferred and is not designed here; section 4 names the seam.

## 1. Diagnosis: why Bheem feels generic and irresponsive

Ranked by how much of the owner's complaint each explains, tied to code.

1. **The chat prompt tells the model to be ignorant.** `ChatPrompt.SYSTEM` (`agent-core/.../assistant/Chat.kt`) says it cannot check the time, cannot look up notes, tasks, reminders or contacts, and must answer in under 60 words. A 2B model does what it is told: it hedges, refuses and keeps it short. This is the single largest cause.
2. **Memory never reaches the model.** `Recall` answers fixed question shapes by code (good: it cannot invent), but the model sees none of the owner's records, facts or preferences. There is no table for profile facts, people or preferences; `PhoneBook` scrapes numbers from notes with a regex. So "what do you know about me" is unanswerable and every chat answer is written for a stranger.
3. **Hard caps that fit a card, not a conversation.** `ChatText`: 700 characters, 120 output tokens, 4 earlier turns cut to 200 characters each, briefs and plans dropped from history. "Make it shorter" did not use the earlier answer on the phone (`docs/evidence/ASSISTANT-SLICES.md`). Temperature 0.2 and topK 1 (`LiteRtLmEngine`) give flat, repetitive wording.
4. **No "now".** The planner prompt carries the time (`PlannerPrompt.nowText`), the chat prompt does not, so "what day is it" either gets a refusal or a wrong guess. The phone knows the date, battery, network and more; Bheem uses none of it in conversation.
5. **Two model calls before chat.** A sentence `PreRouter` does not recognise goes to the planner first (one model call, about 1 s to first token GPU, **measured** MODELS.md), then on `NoAction` to chat (a second call, 1.1 to 1.5 s to first text, **measured**). The owner waits 2 to 3 s before anything appears, and 6 to 9 s after a restart. The feeling of "irresponsive" is this double hop plus the GPU load of about 40 s per process start (**measured**).
6. **Code-written replies are canned.** `SmallTalk` has one fixed string per greeting. Fine for speed, but every "hi" gets the same sentence with no reference to the time of day or what is coming up.
7. **No prefill reuse (measured 2026-11-03, MODELS.md): every prompt token is paid on every turn**, about 0.41 s per 128 tokens on GPU and 0.93 s on CPU. This does not cause the generic feel but it bounds the fix: memory and device context must be short and selected, not dumped.

Not a cause: the gate, the ledger, the tap policy. They stay exactly as they are.

## 2. Persistent memory

### 2.1 What is stored and where

Schema 5 adds one table and nothing else (one migration from 4, with the migration fixture test the handoff asks for):

```
memories(seq PK, id TEXT UNIQUE, kind TEXT, subject TEXT, text TEXT, provenance TEXT, state TEXT,
         source_turn_seq INTEGER, created_at_epoch INTEGER, zone_id TEXT, updated_at_epoch INTEGER,
         last_used_epoch INTEGER, uses INTEGER, superseded_by TEXT, pinned INTEGER)
memories_fts (fts5 over subject, text; unicode61; same trigger pattern as memory_fts)
```

| kind | Example row (invented) | Written by |
| :-- | :-- | :-- |
| `PROFILE` | subject `name`, text `Gowri` ; subject `city`, text `Chennai` | owner's words ("call me Gowri", "I live in Chennai") |
| `PERSON` | subject `Amma`, text `mother; number 98765 43210; likes jasmine` | owner's words; replaces the `PhoneBook` regex over notes (the regex stays as a fallback for old notes) |
| `PREFERENCE` | subject `replies`, text `short, no lists` ; subject `wake`, text `6:30 on weekdays` | owner's words, or the Memory screen |
| `EPISODE` | subject `2026-11-03`, text `Owner was stressed about the invoice; asked twice about Friday` | model extraction, idle time only (slice 4) |
| `SUMMARY` | subject `conversation`, text `one paragraph of the last 200 turns` | model extraction, idle time only (slice 4) |

Notes, tasks, expenses and reminders stay where they are (`memory`, `reminders`); they are records, not memories, and `Recall` keeps answering about them by code. `provenance` is `USER` (the owner typed or spoke it, or confirmed it on the Memory screen) or `MODEL` (extracted by a model call and not yet confirmed). `state` is `ACTIVE`, `PROPOSED`, `SUPERSEDED` or `DELETED` (soft delete keeps the ledger honest; Erase removes the rows).

### 2.2 The writer

Two writers, cheapest first, and a confirmation policy that matches the existing tap rule:

1. **Code rules, at once, from the owner's own sentence.** Patterns: "remember (that) ...", "my <thing> is ...", "call me ...", "I live in / work at ...", "<name> is my <relation>", "<name>'s number is ...", "I like / prefer / hate ...", "from now on ...". These become a new typed action `RememberFact|<kind>|<subject>|<text>` (L0, local write, undo token, tap under the v0.01 policy like every action). It is offered to the model too, so "note that I take the 8:10 train" can come back as `RememberFact`. New action type, so `SECURITY.md`, `GateTest`, `TaintedGateTest` (refused from tainted text), `RedTeamTest` (a forged fact with an injection string stays text) land in the same change. Argument checks: kind from a fixed list, subject 1 to 40 characters, text 1 to 300 characters, no control characters.
2. **Model extraction, in the background, never in the turn.** While charging and idle (the nightly consolidation slot `ARCHITECTURE.md` already reserves), a no-tools call reads the day's `USER` and `CHAT` turns (never briefs, never anything tainted) and returns up to 5 lines `kind|subject|text|confidence`. Lines are parsed by code, stored as `MODEL` and `PROPOSED`, and shown on the Memory screen under "Bheem thinks it learned". One tap makes a row `USER`/`ACTIVE`; until then it is never injected into any prompt. The idea is mem0's extraction step (Apache-2.0) cut to one call and no automatic update or delete, because a 2B model should not be trusted to rewrite the owner's own words. Target cost: one call per day, under 400 prompt tokens.

**Fixing a wrong memory.** On the Memory screen: edit (writes a new row, old row `SUPERSEDED` with `superseded_by`), delete (`DELETED`), pin. In chat: "forget that" and "that's wrong, it is ..." are code rules over the memories cited by the last answer, each producing a card (`ForgetFact`, same gate surface as `RememberFact`, one tap). Nothing is rewritten silently.

### 2.3 Retrieval

Start with keywords and recency; it is enough for one person's memories (hundreds of rows, not millions):

- Candidate set: `memories_fts` MATCH over the owner's sentence (FTS5 with the existing `FtsQuery`), plus every `PROFILE` row (they are few and always relevant), plus `PERSON` rows whose subject appears in the sentence, plus pinned rows.
- Score: bm25 rank, then a recency bonus (`updated_at` within 7 days), then `uses`. Take at most 6 rows, each rendered as one line "`<subject>: <text>`", capped at 120 tokens (**target**; the same cap `ASSISTANT-DESIGN.md` set for records).
- Only `USER` provenance and `ACTIVE` state ever enter a prompt. Records from `Recall` (notes, tasks, reminders) join the same block through the existing `trusted` filter.
- Embeddings are worth it only when a 50-question recall set shows keyword misses above 10 percent (**target**). The candidate is EmbeddingGemma 300M (LiteRT build exists, **vendor**; licence is the Gemma Terms, a custom agreement with a prohibited-use policy and redistribution conditions, not OSI, checked 2026-11-04 at ai.google.dev/gemma/terms) or multilingual-e5-small (MODELS.md). Neither is verified on this phone. Not in these slices.

### 2.4 Token budget with no prefill reuse

Every token is paid every turn (**measured**, MODELS.md prefix probe). First token grows about 0.41 s per 128 tokens on GPU and 0.93 s on CPU (**measured**, step size inferred). Budget for one chat turn (**targets**):

| Part | Tokens | Rule |
| :-- | :-- | :-- |
| System prompt (persona and rules) | 175 | fixed text, chat-v4 at 700 characters (measured length; token estimate is characters / 4), pinned in a test |
| Now line and device context | 40 | one line, always present |
| Memory block | 0 to 120 | omitted when nothing matches |
| History | 0 to 120 | last 4 turns, each cut at 120 characters; a summary row replaces older turns when it exists |
| Owner's message | up to 125 | 500 characters as today |
| Total prompt | at most 640 | five 128-token steps: about 2.3 s GPU, 4.9 s CPU to first text (**target**, from the measured step size) |
| Output | 110 default, 320 on "tell me more", "explain", "in detail" | at 9 to 11 tokens/s (**measured**) a 110-token answer takes about 10 to 12 s to finish; streaming makes the first words appear in about 2 s |

How to measure: `Completion.contextTokens` is already returned by the engine; log it with first-text time per chat turn in Diagnostics (durations and counts only, no text, so `LogHygieneTest` stays green) and record p50 and p95 over 20 turns before and after each slice.

### 2.5 Forgetting, conflicts, privacy, erase, injection

- Conflicts: a new `PROFILE` or `PREFERENCE` row with the same subject supersedes the old one (code, no model). Two `PERSON` rows with the same subject are shown side by side on the Memory screen and the owner picks. `EPISODE` rows older than 90 days (**target**) are folded into the `SUMMARY` by the nightly call and marked `SUPERSEDED`.
- Memory screen (Settings, "What Bheem knows"): grouped by kind, each row shows text, date, "you said" or "Bheem guessed", edit, delete, pin; a "Forget everything Bheem learned" button deletes `MODEL` rows only; export as plain text to the share sheet (owner's data, owner's choice).
- Privacy rules: memories stay in the encrypted database; never in logs, notifications or the lock screen; never in the planner prompt that handles actions (the memory block goes to chat and to the read-only tool loop only); the daily brief may name a `PROFILE` row (the owner's name) and nothing else.
- Erase everything removes the table with the database, as today; the nightly extraction job is cancelled with the brief alarm.
- Injection safety: a memory whose `source_turn_seq` points at a tainted turn can never exist, because the writer reads `USER` and `CHAT` turns only; when share-sheet capture arrives (tainted turns), the writer's query must exclude them by provenance and a test must prove it. A `CHAT` turn can quote the owner's note text, nothing else, so a chat-derived memory is at worst a restatement of the owner's own words. Only `USER`-provenance memories are injected, and the block is wrapped as data ("Things the owner told you earlier:"), never as instructions.

## 3. The phone as Bheem's computer

Two mechanisms. **Context** is a one-line snapshot read by code at the start of every turn and written into the user turn (never cached, never in the system prompt). **Tool** is a read-only function the loop in section 4 may ask for, or an action that becomes a card. Risk levels use the existing gate; reads are `READ` like `AnswerFromMemory`.

| Signal or tool | Gives | Android API, permission | Read or act | Risk | How | First set |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| Time, date, weekday, zone, locale | "Wed 4 Nov 2026, 7:42 PM, IST, en-IN" | `java.time`, `Locale.getDefault()`; none | read | none | context | yes |
| Battery and charging | "63%, charging" | `BatteryManager` sticky `ACTION_BATTERY_CHANGED`; none | read | none | context | yes |
| Network state | "Wi-Fi" / "mobile" / "offline"; no call is made | `ConnectivityManager.getNetworkCapabilities`; `ACCESS_NETWORK_STATE` (normal permission, no prompt; acceptable, it reads a flag) | read | none | context | yes |
| Do not disturb, ringer | "DND on until 7 AM", "silent" | `NotificationManager.currentInterruptionFilter`, `AudioManager.ringerMode`; none to read | read | none | context (one word) | yes |
| Storage and RAM | "41 GB free of 128; 2.1 GB RAM free" | `StatFs`, `ActivityManager.getMemoryInfo`; none | read | none | tool | yes |
| Next alarm | "Alarm 6:30 tomorrow" | `AlarmManager.getNextAlarmClock`; none | read | none | tool | yes |
| Screen state, uptime | interactive, since boot | `PowerManager.isInteractive`, `SystemClock`; none | read | none | tool | no |
| Volume | per stream | `AudioManager.getStreamVolume`; none | read | none | tool | yes |
| Media playing | "music is playing" (no title) | `AudioManager.isMusicActive`; none. Track title needs notification access: not now | read | none | tool | yes |
| Installed apps, open one | list of launcher apps; `OpenApp` | `PackageManager.queryIntentActivities` with a `<queries>` MAIN/LAUNCHER filter (allowed; `QUERY_ALL_PACKAGES` is Play-restricted and not needed); open via launch intent | read; act | L1 hand-off (new action type) | tool; card | yes |
| Flashlight | on or off | `CameraManager.setTorchMode`; no permission | act | L0 (undo: turn it back), tap under the current policy | card | yes |
| Volume, ringer, DND change | set | `AudioManager.setStreamVolume` (none); DND needs `ACCESS_NOTIFICATION_POLICY` (settings grant) | act | L0 volume; L1 DND | card | volume yes, DND later |
| Alarms, timers | set | `AlarmClock.ACTION_SET_ALARM`, `ACTION_SET_TIMER` with the clock app's UI shown (`SET_ALARM` is a normal permission) | act | L1 hand-off | card | yes |
| Countdown, stopwatch in Bheem | "5 minutes" | the existing reminder scheduler, text "timer" | act | L0 as a reminder | card | yes |
| Location | city, coarse | `ACCESS_COARSE_LOCATION` runtime | read | n/a | opt-in | **no**: zone and a `PROFILE` city row cover "where am I" well enough; revisit with web access |
| Calendar | today's events in the brief | `READ_CALENDAR` runtime | read | none | opt-in | later, after the brief has run a month; a strong candidate |
| Contacts | numbers by name | `READ_CONTACTS` runtime | read | none | opt-in | **no** for v0.01; `PERSON` memories first (same ruling as the assistant design) |
| Clipboard | current text, only when asked and Bheem is in front | `ClipboardManager` (Android 10+ allows the focused app only; Android 12+ shows a system toast) | read | tainted text: shown, never a memory, never an instruction | tool | later |
| Notifications | other apps' text | `NotificationListenerService` | read | tainted; no-tools extraction only (`SECURITY.md` rule) | not in this design | no |

**Keeping "now" correct.** `DeviceSnapshot` is a pure data class in `agent-core` with an interface `DeviceSensor` implemented in `app`; `ChatReplier` takes the snapshot at the moment it builds the request, and a test asserts the rendered line carries the clock the replier was given (fixed clock in tests). Because LiteRT-LM reuses nothing between conversations (**measured**), there is no cached prompt to go stale; the only risk is a snapshot taken early and held, which the "build at request time" rule removes. A `now` older than 2 s at send time fails a debug assertion.

**Code first, model second.** "What time is it", "what's the date", "what day is it", "is it charging", "am I online", "how much battery", "what's my next alarm" are answered by code from the snapshot in under 50 ms (**target**), through a `DeviceQuestions` route beside `SmallTalk`, so the owner never waits for the model for a fact the phone knows. The model sees the same line, so "should I charge before I leave at 8" can use it.

## 4. Chat and agent loop for a 2B model

**Persona prompt (replaces `chat-v1`; current id `chat-v4`, measured on the baseline before it ships):** Bheem is the owner's own assistant living on this phone; warm, direct, plain English; uses the owner's name when it knows it; speaks of what it knows ("your notes say", "you told me") and of what it does not; can set reminders, notes, tasks, events, messages, calls and routes **by proposing a card the owner taps**, so it offers ("want me to set that up?") and never claims to have done anything; cannot browse or check anything outside this phone; answers in 1 to 3 short sentences unless asked for more; no lists or markdown; for health, money and law it gives general facts and says to check with a professional. The prompt states the model's limits once, positively ("you know the time and the owner's facts below"), not as a wall of cannots.

**Message assembly (user turn):** `Now: ...` line, `Things the owner told you earlier:` block (section 2.3), `Earlier in this conversation:` (4 turns, 120 characters each), `The owner now says:`. Order fixed; a test pins the token count of the fixed parts.

**One model call, not two.** The planner call runs first only when `PreRouter` says `Model` and the sentence has a doing-word or a time word; a plain question goes straight to chat. The chat answer may itself end with one typed line `Offer|<the command Bheem would run>` which code turns into a card through the normal pipeline (planner, parser, time repair, gate, tap). So "I have a dentist appointment Friday at 4" gets a warm sentence and a calendar card under it, in one wait instead of two. The `Offer` line is parsed by code, text-checked like any argument, and nothing happens without the tap.

**Tool loop, code-controlled, at most 2 steps (target):** step 1 the model may name one read-only tool from a fixed list (`device_state`, `memory_search`, `records_search`, `storage`, `next_alarm`); code runs it, appends the result as data, and calls the model again for the answer; step 2 is the answer or one `Offer` line. No tool writes anything. Actions remain cards. Web access would plug in here as one more read-only tool behind `full` flavor and an owner setting; the seam is the tool list and the `DeviceSensor` style interface, nothing else changes.

**Native function calling versus the text planner: a trial, not a switch.** LiteRT-LM's Kotlin API declares tools with a `ToolSet` and `@Tool`/`@ToolParam` annotations in `ConversationConfig(tools = ...)`, and `automaticToolCalling = false` makes the runtime return `toolCalls` for the app to execute (developers.google.com/edge/litert-lm/android, fetched 2026-11-04; the page names no version, our adapter is 0.18.1). **Bheem must always set `automaticToolCalling = false`**: the runtime executing a tool would breach "only executors run, after the gate". LiteRT-LM lists tool use with constrained decoding as a feature and 0.19.0 (released 2026-10-28, **vendor**) says "faster constrained decoding on GPUs"; the Kotlin flag name for constrained decoding was not found in the docs (the Flutter binding calls it `enableConversationConstrainedDecoding`, unverified for Kotlin). Gemma 4's own call format is `<|tool_call>call:name{arg:<|"|>value<|"|>}<tool_call|>` (ai.google.dev Gemma function-calling page, **vendor**), so a fallback text parser is possible if the API path fails. The trial: on the 40-conversation set's tool-selection and action cases, run the current line planner and the native path side by side on the phone, both backends, and keep native only if it is at least as right on action and arguments (the 2-point swap rule), produces zero unparseable calls, and first token p95 is within 1.25 times the line prompt (**targets**). The upgrade to 0.19.x is a runtime change and must pass `:eval:gate` and the scorecard first.

**Thinking mode and E4B: where to trial.** Gemma 4 thinking is switched on with `<|think|>` at the start of the system prompt and the thoughts arrive between `<|channel>thought` and `<channel|>` (model card, **vendor**; the existing `ThinkingFilter` strips `<think>` tags only and would need these). The card says thinking "significantly enhances" function-calling accuracy with no number (**vendor**). Trial it only on the tool-selection subset, measure first token and total time, and expect it to lose on time. E4B (about 4.1 GB class, **vendor**) is a registry entry and a scorecard run, nothing more; trial it only if E2B fails the tool trial, and only on the 8 GB class of phone.

**Longer and warmer without losing the guards.** Keep `ChatText.claimsToHaveActed` (an answer that claims an action still becomes "I did not do that"), the caution line, the model label, the markdown strip; raise `MAX_CHARS` to 1,400 and the default output to 110 tokens; temperature 0.6 and topK 40 for chat only (planner stays at 0.0 and topK 1), measured on the baseline for repetition and for claims-filter hits. Streaming stays. A reply longer than 450 characters is still cut for speech by `SpokenText`.

## 5. The baseline test (first, before any change)

**Sets.** `eval/data/conversations.jsonl` (30 development) and `eval/data/conversations-heldout.jsonl` (10 held-out, written in the same sitting, then added to `.ignore` and `.rgignore`, read only as aggregates, exactly as `assistant-heldout.jsonl` is). Fixture memory `eval/fixtures/memory-seed.json` (invented owner "Gowri", city Chennai, mother "Amma" with an invented number, a dentist note, a Friday with two reminders, a preference for short replies; nothing real). Fixed clock `2026-11-04T19:42` IST, fixed snapshot (battery 63 charging, Wi-Fi, DND off, next alarm 6:30). Groups, with counts across the 40: persona and warmth 5, multi-turn consistency 5, memory recall across days 7 (seed rows dated days earlier), date and time 5, device state 5, correct refusals 4 (cannot browse; cannot act without a tap), tool selection 5, small talk 4.

**Case shape.** `{id, group, fixture, now, turns:[{user, expect:{path, kind, must:[...], must_not:[...], regex?, judged?}}]}` where `path` is one of `smalltalk | device | recall | action | plan | clarify | chat | refused` and `kind` the `Turn` or `Outcome` kind.

**Rubric a script applies.** Exact: `path` right; `kind` right; every `must` substring present (case-insensitive) and no `must_not`; for a card, action type and key arguments; for recall, every cited id in the expected set and no invented number or capitalised name (the grounding rule from `ASSISTANT-DESIGN.md`); for date and device, the exact value from the fixture; no claim of action (`ChatText.claimsToHaveActed` false); length within the budget. Judged (human, 0 to 2 each, about 15 minutes for 40 cases): warmth (uses the name or the context naturally), consistency (does not contradict an earlier turn). Pass **targets**: exact checks 100 percent on routing and refusals, at least 90 percent on recall and device, at least 80 percent on tool selection; judged mean at least 1.5 on development; held-out within 2 points of development on every row (the swap rule). The baseline run is expected to fail most of these; its job is to be the number the slices move.

**How to run without loading the owner's computer.**
(a) **Routing baseline, pure JVM, in the sandbox:** a Gradle task `:eval:conversations` (beside `:eval:gate`) builds the real `CommandPipeline`, `Recall`, `SmallTalk`, `DeviceQuestions` and a `ScriptedPlanner`, plays each conversation, and writes per case the path taken and the exact prompt the model would receive (`ChatText.message` output and `ChatPrompt.SYSTEM`, with token counts). Runs under `~/ci.sh tests` in the instabox sandbox; nothing on the owner's machine.
(b) **Model replies.** Option 1, the phone: `scripts/phone/cases.py` style driver, only when the owner asks, one conversation first, process count checked (`tasklist | find "python"` must show one), the owner holds the phone. Option 2, the sandbox CPU: `litert-lm-api` 0.19.0 ships `py3-none-manylinux_2_27_x86_64` wheels with "Requires Python >= 3.10" (PyPI, checked 2026-11-04), so Python 3.13 on Debian 13 x86_64 should install; the CLI is `uv tool install litert-lm` then `litert-lm run` (GitHub README, checked 2026-11-04). The model file is the pinned `gemma-4-E2B-it.litertlm` (2.59 GB, fits 16 GB free disk; phone CPU peak RSS 1.55 GB **measured**, so 7 GB RAM should be enough, **target**). **Not verified:** that the wheel imports on Debian 13, that the sandbox CPU runs this file, and the download itself (the sandbox has network; the owner's rule about long processes applies to his machine, not to the sandbox, but the owner's yes is needed before a 2.6 GB download). The sandbox would run 0.19.0 against the phone's 0.18.1, so sandbox results count for routing, prompt shape and answer content, never for timing or for the swap rule; the phone remains the measurement of record.

**Seed conversations (the first 9 of the 40; the other 31 are written in the same sitting, 10 of them sealed at once).**

| id | group | turns (U owner, B expected) |
| :-- | :-- | :-- |
| cv-01 | small talk | U "hi" ; B path smalltalk, warm, mentions evening or the next reminder, no card |
| cv-02 | date and time | U "what day is it today" ; B path device, must "Wednesday", "4 November" ; U "and the time?" ; B must "7:42" |
| cv-03 | device | U "is my phone charging" ; B path device, must "charging", "63" ; U "am I online" ; B must "Wi-Fi" |
| cv-04 | memory recall | (seed: "Amma's number is ...", 3 days ago) U "what is Amma's number" ; B path recall, cites the seed row, must the seeded digits ; U "call her" ; B path action, card `PlaceCall`, tap required |
| cv-05 | memory recall | (seed: profile name Gowri, city Chennai) U "where do I live" ; B path recall or chat, must "Chennai" ; U "remember that I moved to Bengaluru" ; B card `RememberFact`, tap ; U "where do I live now" (after tap) ; B must "Bengaluru", must_not "Chennai" |
| cv-06 | persona | U "I had a rough day" ; B path chat, judged warmth, must_not "I cannot", must_not any action claim ; U "tell me more about how to unwind" ; B longer than the first answer, no list marks |
| cv-07 | refusal | U "what's the weather in Chennai right now" ; B path chat or refused, must "cannot" or "no way to check", must_not a temperature ; U "ok then set a reminder to carry an umbrella at 8 tomorrow" ; B card `CreateReminder` 2026-11-05T08:00 |
| cv-08 | tool selection | U "how much space is left on this phone" ; B path device tool `storage`, must "GB" ; U "turn on the torch" ; B card flashlight, tap, nothing happens before it |
| cv-09 | consistency | U "my dentist is on Friday at 4" ; B chat plus `Offer` card `CreateCalendarEvent` 2026-11-06T16:00 ; U "what did I just tell you" ; B must "dentist", "Friday" ; U "yes" (after the card shows) ; B approves the offered card only |

## 6. Build order

Each slice is one or two days of implementation plus an Opus review, test-first, built and tested in the sandbox, installed on the phone by the owner.

| # | Slice | Files (main) | Test-first note | Measurable exit | The owner notices |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 0 | Baseline | `eval/data/conversations*.jsonl`, `eval/fixtures/memory-seed.json`, `eval/.../ConversationRunner.kt`, `:eval:conversations` task, `.ignore`, `.rgignore` | the runner is tested on 2 fixture conversations before the 40 exist; the held-out 10 are sealed in the same commit | the JVM routing report exists for all 40; the model-reply baseline recorded on the phone (one owner sitting, about 30 minutes) or in the sandbox; numbers written in `docs/evidence/PERSONAL-AGENT-BASELINE.md` | nothing yet; this is the before picture |
| 1 | "Now" and device context | `agent-core/device/DeviceSnapshot.kt`, `DeviceQuestions.kt`, `app/AndroidDeviceSensor.kt`, `Chat.kt` (now line), `AppController` route | fixed-clock tests for every question shape; the rendered line pinned; `ACCESS_NETWORK_STATE` added and the manifest check updated | date, time, device cases 100 percent by code under 50 ms; first text on chat within 0.2 s of today's (**targets**) | "what day is it" answered at once and right; chat knows the time and battery |
| 2 | Persona and history | `Chat.kt` (`chat-v2`, budgets, sampler), `ChatText` caps, `ModelProfile` gains a chat sampler | `ChatTest` extended: token count of fixed parts, claims filter still catches every phrase, caution line kept | judged warmth mean from the baseline to at least 1.5; repetition and claims-filter hits recorded; first text p95 at most 1.25 times the baseline (**targets**) | replies sound like someone who lives on the phone; "tell me more" works |
| 3 | Memory writer (rules), retrieval, Memory screen, schema 5 | `storage/Schema.kt` (5, migration, fixture), `agent-core/memory/Memories.kt`, `RememberFact`/`ForgetFact` in `ActionCatalog`, `Gate`, executors, `MemoryBlock.kt`, `app/MemoryScreen.kt`, `SECURITY.md` | gate tests first (kinds, lengths, tainted refused, red-team forged fact); migration test from the schema 4 fixture; only `USER`/`ACTIVE` rows reach the block | memory recall cases at least 90 percent; cv-05 style supersede passes; block never over 120 tokens (**targets**) | "remember that ..." works and is visible and editable; Bheem uses the owner's name |
| 4 | Model extraction at idle | `agent-core/memory/Extraction.kt` (prompt `memory-extract-v1`, parser), `app/ConsolidationWorker` (charging and idle) | parser tests on malformed lines; rows land `MODEL`/`PROPOSED`; a test that tainted turns are never read | after a seeded day, proposed rows appear and none is injected until confirmed; one call per night under 400 tokens (**target**) | "Bheem thinks it learned" list the next morning |
| 5 | Loop, `Offer`, tool trial | `assistant/Loop.kt` (max 2 steps, tool list), `Offer` parsing, `models-litertlm` tool path with `automaticToolCalling = false`, `models.json` capability `TOOLS`, `ThinkingFilter` for Gemma 4 tags | tests: a tool the model names outside the list is ignored; a write-shaped tool call is refused; `Offer` goes through the pipeline and gate; swap test with a fake engine that returns tool calls | trial table in `docs/evidence`: line planner versus native on the 40 set, both backends; keep native only on the rule in section 4 | one wait instead of two; "how much space" answered with a real number |
| 6 | Device actions | `OpenApp`, flashlight, volume, alarm and timer hand-offs as action types; `SECURITY.md` rows; cards with plain effect text | gate and tainted tests for each; red-team: forged `OpenApp` to an unknown package refused; flashlight undo | the device cases of the set pass; every action still waits for a tap (red-team green) | "torch on", "set a 10 minute timer", "open WhatsApp" each give a card |

**Not built in these slices:** web access (seam only), embeddings, model-written memory updates or deletes, contacts and calendar permissions, location, clipboard, notification reading, thinking mode by default, E4B, Hindi, routines and skill packs, a second runtime, wake word.

## 7. Product roadmap: more features and product improvements

Ranked by value to the goal (a persistent, reliable personal agent that feels responsive and personal) against cost. Effort is S (under a day), M (1 to 3 days), L (a week or more), all **targets**. "Who" says whether a 2B model can do it reliably or whether code must. Slice numbers refer to section 6; "after 6" means a later slice.

| Rank | Feature | Reason | Effort | Risk | Who | When |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | "Now" and device facts by code | the cheapest visible cure for "generic" | S | low | code | slice 1 |
| 2 | Persona, longer answers, remembered preferences in the prompt | the owner's main complaint | S | medium (repetition, claims) | model, code guards | slice 2 and 3 |
| 3 | "Remember this", "forget that" by voice or text, Memory screen, "what do you know about me" | memory the owner can see and fix is the trust base | M | medium (new action types) | code writer; model only in the background | slice 3 and 4 |
| 4 | Date arithmetic ("days until Diwali", "what day is 15 Dec", "how many days since") | 2B models get dates wrong (**measured**, MODELS.md: "next Monday" to the 28th) | S | low | code (`TimeResolver`) | slice 1 |
| 5 | Calculator, percentages, tip and bill split, unit conversion | model arithmetic is unreliable; code is exact | S | low | code; the model may only route | slice 1 or 5 |
| 6 | Timers, countdowns, stopwatch | daily use; reuses reminders | S | low | code | slice 6 |
| 7 | One model call per turn (`Offer` line) | halves the wait on mixed sentences | M | medium (parse) | model proposes, code checks | slice 5 |
| 8 | Instant acknowledgement ("On it" in the thinking bubble, name of the path) | perceived latency | S | low | code | slice 2 |
| 9 | Keeping the model warm within a budget: idle unload 10 min foreground, 3 min background, CPU when GPU load would exceed 20 s, pre-load on app open when charging | the 40 s GPU load (**measured**) is the biggest felt delay | M | medium (battery) | code; measure with the 24 h run | after 6 |
| 10 | Share-sheet target "Send to Bheem" | the first untrusted source; the planned Stage 16 | M | high (injection, tainted path) | code; no-tools extraction | after 6, as planned |
| 11 | Text tools: rewrite, shorten, draft a message, translate-lite | model writes, code checks (grounding rule, no new facts, length) | M | medium | model with code checks; translation quality unverified for Indian languages | after 6 |
| 12 | Conversation search (FTS over `turns`) | "what did we talk about on Monday" | S | low | code | slice 3 (same FTS pattern) |
| 13 | Follow-up questions and quiet nudges (brief exists; add "you asked about X yesterday, want a reminder?") | feels attentive; must stay quiet | M | medium (annoyance) | code rules over records | after 4 |
| 14 | Random picks, coin, dice, "pick one of these" | small delight, trivial | S | low | code | slice 1 |
| 15 | Quick Settings tile, home-screen widget (next reminder plus a type box) | reach | M | low | code | after 6 |
| 16 | Hands-free voice conversation mode (hold-free, offline TTS exists) | natural with the persona; needs endpointing | L | high (false triggers, battery) | code | after 6, only on request |
| 17 | Lock-screen and notification quick reply to a reminder ("snooze 10", "done") | reminders that never fail | M | medium | code | after 6 |
| 18 | Invoking Bheem as the phone's assistant (`VoiceInteractionService`) | reach | L | high (OEM behaviour) | code | not yet |
| 19 | Encrypted backup and restore, plain export | trust and data; today a lost phone loses everything | L | high (key handling) | code | after 6; the export part is S and goes with the Memory screen |
| 20 | Privacy dashboard (what is stored, counts, permissions in use), battery and storage budget screen | trust; mostly reads of what exists | M | low | code | after 6 |
| 21 | Model watchdog and fallbacks (rules-only on failure, retry once, thermal and low-battery behaviour) | `Governor` exists; the loop adds a timeout per step and a cap of one retry | S | low | code | slice 5 |
| 22 | Crash and kill recovery (half-written chat turn dropped, pending clarify restored, a cold-load marker) | stability; mostly tested already | S | low | code | slice 2 |
| 23 | Model file update path (registry entry per version, download, verify, swap, keep the old file until the new one passes the scorecard) | runtime and model moves monthly | M | medium | code | after 6 |
| 24 | Shareable on-device diagnostics (counts, durations, backend, token counts per turn) | exists; add the chat and memory counters | S | low | code | slice 2 |
| 25 | Screenshot or photo questions ("what does this bill say") | Gemma 4 E2B takes images (model card, **vendor**; unverified on this phone and in this adapter); the image is tainted text | L | high | model, no-tools call | not yet |
| 26 | File and download awareness ("what did I download today") | `MediaStore.Downloads` read with the photo picker or `READ_MEDIA_*`; low value for this owner | M | medium (permissions) | code | not yet |
| 27 | Clipboard helper | low value, platform limits, tainted | S | medium | code | not yet |
| 28 | Notification triage | the untrusted-text rule; only after share-sheet has run a release | L | high | no-tools extraction | not yet |

Recommend **not** building yet: 18, 25, 26, 27, 28, and anything with a network call. The reasons are in the table: each is high risk or low value against the stated goal, and 25 and 28 widen the untrusted-text surface before share-sheet capture has been measured.

## 8. Risks and open questions, ranked

1. **The persona prompt costs correctness.** A warmer, longer prompt may raise false claims of action and wrong facts. Mitigation: the claims filter stays, the baseline measures claims-filter hits and judged consistency before and after, and the memory block carries only the owner's words.
2. **Native tool calling on 0.18.1 is unverified in Kotlin.** The documented API may need 0.19.x; a runtime upgrade can change every golden. Mitigation: the trial is a branch with its own scorecard; the line planner stays the default until the rule in section 4 is met.
3. **Prompt growth.** Memory plus history plus device line can cross two 128-token boundaries (about 0.8 s GPU, 1.9 s CPU, **measured** step size). Mitigation: the budget table, `contextTokens` in Diagnostics, omit empty blocks.
4. **Model-extracted memories are wrong or creepy.** Mitigation: `PROPOSED` until confirmed; never injected; a one-tap "forget all guesses".
5. **Longer answers feel slow.** 110 tokens is about 10 to 12 s at the measured decode rate. Mitigation: streaming, "On it" at once, default 1 to 3 short sentences, longer only on request; measure the owner's felt wait, not the token count.
6. **New action types widen the surface** (`RememberFact`, `ForgetFact`, `OpenApp`, flashlight, volume, alarm hand-offs). Mitigation: each lands with its gate, tainted and red-team rows in the same change, as the rule says.
7. **The sandbox model run may not be possible** (wheel, CPU speed, download). Mitigation: it is optional; the phone path exists and the routing baseline needs no model.
8. **Schema 5 is the second real migration.** Mitigation: fixture of the schema 4 layout and the migration test land before any new column is read.

Open questions I could not settle: the Kotlin constrained-decoding flag name and whether 0.18.1 has the `ToolSet` API; whether Gemma 4 E2B's tool calls are reliable at 2B on-device (one community test of E4B reported unreliable tool use, unverified); EmbeddingGemma's licence fit for an Apache-2.0 app (custom Gemma Terms, not OSI); the real token count of `chat-v2` until it is tokenized.

**Decisions for the owner (5):**

1. **Confirmation for remembered facts.** Keep the tap for `RememberFact` (consistent with v0.01) or let the owner's explicit "remember that ..." save silently with Undo (L0 without tap, a policy exception). Recommendation: tap for now, revisit after a month.
2. **Model-extracted memories at all.** Build slice 4, or stop at code rules. Recommendation: build it, but only as proposals.
3. **Answer length and speed.** Default 110 tokens (about 10 to 12 s to finish, streamed). Recommendation: 110, with a longer answer only on request.
4. **Runtime upgrade to LiteRT-LM 0.19.x for the tool trial.** Yes behind the scorecard, or stay on 0.18.1 and trial the text format only. Recommendation: upgrade on a branch, scorecard first.
5. **Sandbox model download** (2.6 GB into the instabox). Yes or no. Recommendation: yes, it keeps the owner's phone free for the measurement of record.

## Sources (checked 2026-11-04; what we adopt)

- OpenMausBot, github.com/milind-soni/OpenMausBot, Apache-2.0 (an `enterprise/` folder is source-available under its own licence). Adopted: memory as plain text the owner can open, correct and delete; routines that arrive paused. It is desktop and cloud-CLI based; its memory format is not documented on the README. "OpenBot" (github.com/isl-org/OpenBot) is the Intel robotics project and is not relevant.
- Letta (formerly MemGPT), github.com/letta-ai/letta, Apache-2.0; docs.letta.com. Adopted: memory as pinned blocks the agent edits through typed tools, persisted outside the context window. Not adopted: letting the model edit memory directly (a 2B model proposes, the owner confirms).
- mem0, github.com/mem0ai/mem0, Apache-2.0; paper arXiv:2504.19413. Adopted: one extraction call per exchange, add-only ("memories accumulate; nothing is overwritten"), hybrid keyword plus entity retrieval. Not adopted: cloud LLM defaults, embeddings now.
- LiteRT-LM, github.com/google-ai-edge/LiteRT-LM, Apache-2.0; Android guide at developers.google.com/edge/litert-lm/android (`ToolSet`, `@Tool`, `automaticToolCalling`); Python package `litert-lm-api` 0.19.0 on PyPI (py3-none manylinux x86_64, Python >= 3.10); CLI `litert-lm`. Adopted: manual tool execution only.
- Gemma 4 model card, ai.google.dev/gemma/docs/core/model_card_4 (Apache 2.0 stated for the weights; E2B: 2.3B effective, 128K context, text, image and audio in, 35+ languages, Hindi not named, cutoff January 2025; `<|think|>` thinking toggle); function-calling page ai.google.dev/gemma/docs/capabilities/function-calling (call format, "always validate function names and arguments before execution"). All **vendor**.
- Google AI Edge Gallery, github.com/google-ai-edge/gallery, Apache-2.0; developers.googleblog.com "Bring state-of-the-art agentic skills to the edge with Gemma 4". Adopted: the idea of a small fixed tool set run by code around an on-device model. Their Mobile Actions use a FunctionGemma 270M fine-tune (**vendor**); a fine-tune does not add knowledge and is not planned.
- Gemma Terms of Use, ai.google.dev/gemma/terms (last modified 2026-04-01): custom licence with a prohibited-use policy; relevant to EmbeddingGemma only.
