# Architecture

Bheem is an **agent kernel** with pluggable **skills**. Every feature runs through the same pipeline, which is what lets one small codebase replace many apps while staying testable.

```
 SENSE            UNDERSTAND          PROPOSE        GATE            ACT
 ───────          ──────────          ───────        ────            ───
 notifications ┐   1. rules/parsers   typed action   policy gate     intents
 voice         │   2. LLM extraction  from a fixed    (plain code)    DB writes
 share/photo   ├─► (schema-checked) ─► catalog     ─► risk L0/L1/L2 ─► UPI hand-off
 calendar      │   3. memory lookup   + confidence    + user tap      Health Connect
 schedules     ┘                                      or biometric
                                          │                │
                                          └──── ledger ◄───┘  (every step logged)
```

## Principles

- The model never executes anything. It selects from a closed catalog of typed actions.
- Untrusted text (a notification, a forwarded message, a PDF) is only ever given to an extraction call that has no tools.
- Deterministic code owns every decision about whether an action runs.
- If anything is uncertain or invalid, Bheem shows nothing or asks. It never guesses silently.

## 1. Sense

| Source | Mechanism | Notes |
| :--- | :--- | :--- |
| Payment and app notifications | `NotificationListenerService` (user-granted) | Allowlist of packages (banks, UPI apps). Others ignored. |
| Voice | `AudioRecord` + speech-to-text | Hold-to-talk only. Audio lives in RAM and is discarded after transcription. |
| Share and screenshots | Share target, image picker | Text or image extraction. |
| Calendar | `CalendarContract` observer | Optional. |
| Time and idle | WorkManager, AlarmManager | Reminders, routines, nightly consolidation while charging and idle. Exact-time delivery uses the alarm path; the permission choice is set in the plan. |
| Routines | Trigger registry | User-defined time or event triggers. Each firing creates an `event` and goes through the same pipeline; a routine can never skip the gate. |

Raw SMS is intentionally not an input (restricted on Google Play and unnecessary given notification access).

Each incoming item becomes an `event` with a provenance flag. Anything originating outside the user's own voice or typing is marked `tainted`.

## 2. Understand

Three tiers, cheapest first:

1. **Deterministic parsers.** Versioned template packs per bank or UPI app, plus date, amount, and number normalisation (₹, lakh/crore, Hinglish numerals). Fast, free, and testable. Most payment alerts end here.
2. **LLM extraction.** For text the parsers do not recognise and for voice transcripts. The model returns JSON that is validated against a schema. On invalid output: one retry with the error, then fall back to "no card" or an explicit question to the user.
3. **Memory lookup.** For Ask and for context ("same as last month's electricity bill"), via full-text search first and embeddings later.

## 3. Propose

The planner turns an understood event into zero or more **proposals**: `(action_type, args, confidence, rationale)` where `action_type` is from the catalog below. Low-confidence proposals are not shown as cards.

### Action catalog (v1)

| Action | Effect | Origin rule |
| :--- | :--- | :--- |
| `LogExpense` | Local write | Silent, shown in feed with Undo |
| `CreateReminder` / `CreateTask` | Local write + alarm | L0 if from the user's own command, L1 if inferred from external text |
| `CreateCalendarEvent` | Writes to the system calendar | Same rule as above |
| `DraftMessage` | Opens WhatsApp/SMS composer, prefilled | L1: the user taps send in the target app |
| `OpenUpiPayment` | Opens the user's UPI app with `upi://pay` prefilled | L2: UPI app confirms with its own PIN or biometric |
| `SaveNote` | Local write | L0 |
| `AnswerFromMemory` | Read-only | No gate needed |

Adding an action type changes the security surface and requires a note in [SECURITY.md](SECURITY.md).

## 4. Gate

A small, pure Kotlin component. It has no model dependency and is unit-tested exhaustively.

- Assigns a risk level from action type and event provenance.
- **L0**: runs, appears in feed, can be undone.
- **L1**: waits for a user tap on a card.
- **L2**: waits for an explicit confirmation (and hands authorisation to the UPI app).
- Rejects any proposal whose arguments fail validation (bad VPA format, amount out of range, unknown package).
- Rate-limits proposals per source so a notification flood cannot spam the feed.

## 5. Act (executors)

Each action type has one executor behind an interface: Android intents, database writes, Health Connect (v1.1). Executors never call the model. Each writes a ledger entry on success or failure.

## Skill packs

Skills are packaged as declarative Markdown packs with YAML frontmatter (see [PRODUCT.md](PRODUCT.md)). The pack loader validates the frontmatter against a schema, registers parser templates and routines, and refuses anything that tries to add an action type or code. Built-in skills use the same format. Pack handling is covered by tests and by the security rules in [SECURITY.md](SECURITY.md).

## Memory

Built in `storage` (stage 2 of [PRODUCTION-DIRECTION.md](PRODUCTION-DIRECTION.md)): encrypted SQLite (SQLCipher 4.19.1, BSD-style license that needs attribution in the app), a random 32-byte key wrapped by an Android Keystore AES-GCM key (StrongBox when available) and kept in the no-backup folder. Full-text search with FTS5 (`unicode61`, case and accent insensitive). Times are stored as epoch milliseconds plus the zone id. Vector search is added later and only if it earns its place.

Schema version 2 (`storage/Schema.kt`; the first dev build used a plain `bheem.db` that is deleted on start, since nothing in it was worth migrating):

| Table | Purpose |
| :--- | :--- |
| `proposals` | Proposed actions: type, args, risk level, provenance, source, prompt version, model id and backend, user text, undo token |
| `ledger` | Append-only record of what was read, proposed, executed and undone; indexed by proposal |
| `memory` and `memory_fts` | Notes, tasks and expense records, with full-text search |
| `reminders` | Created now and used from stage 5: one row per scheduled reminder so alarms can be re-armed after a reboot |

Not built yet: `events` and `transactions` (they belong to notification access, after v0.01).

Retention is configurable. OTP-like text is dropped before it reaches storage. Raw notification text is kept only as long as needed to parse and audit, then reduced to structured fields.

## Background execution

- Event-driven. Planned: the notification listener (after v0.01) and WorkManager wake the process; there is no always-running foreground service for inference. Today the app runs the model only while you use it.
- The language model **loads on demand and unloads after an idle timeout**. Between events, the process holds only the rules engine and the database.
- Android 16 applies runtime quotas to jobs started from foreground services, so heavy work is scheduled as regular WorkManager jobs with constraints, and the nightly consolidation runs only while charging and idle.
- A thermal and battery governor lowers capability under pressure: disable reasoning mode, defer indexing, and in battery-saver run rules-only.

## Model layer

Defined in [MODELS.md](MODELS.md). The kernel depends only on the interfaces in `models` (`LlmEngine` today; `SpeechToText`, `TextToSpeech` and `Embedder` are planned), so swapping a model or runtime never touches skills.

Built in stage 3 (`models/host`, wired in `app`): a `ModelHost` owns the engine's life. `acquire()` returns the engine and counts a use, `release()` ends it, and an idle model is unloaded after 3 minutes (a target, to tune after the battery measurement in stage 11). Before the first load after an install or an app update the host checks the file's size and SHA-256 against the registry. It loads GPU first and falls back to CPU, remembering a GPU failure per LiteRT-LM version so a bad GPU is not retried at every launch. A `Governor` (pure rules, thresholds are targets with no phone data behind them: thermal status 3 or battery saver off the charger stops loading, thermal status 2 or battery saver while charging forces CPU, thermal status 4 unloads) can stop a load, force CPU, or trim a loaded model. While a load runs (including the hash check), a foreground service of type `shortService` keeps the process alive when the app goes to the background; when Android times the service out after about 3 minutes the host aborts the load and the card says the model took too long. The planner only ever sees the host.

## Modules

```
app/                Compose UI, app wiring, Android executors (built)
agent-core/         pure Kotlin: action catalog, parser, time resolver, gate, ledger, pipeline, proposal coordinator,
                    local memory and Ask, feed model, planner prompt and planner (built, depends on models interfaces only)
models/             LlmEngine and the model contract types, WAV helpers (pure Kotlin, built)
models-litertlm/    LiteRT-LM adapter (Android library, built)
storage/            encrypted database (SQLCipher), schema, stores for the ledger, proposals, memory and feed, key vault, erase (Android library, built)
eval/               datasets, scorers, benchmark runner, speech study tools, golden outputs and the offline quality gate (not shipped)
bench/              Phase 0 benchmark and recorder app (on-phone eval runner; never shipped)
```

Planned, not built: model download, `SpeechToText` in `models`, `skill-*` modules once a skill needs one. See [PRODUCTION-DIRECTION.md](PRODUCTION-DIRECTION.md).

Skills (when they exist as modules) depend on `agent-core`, never on each other. There is no separate `memory` module yet: local memory and Ask live in `agent-core`. Adapters depend on `models`; nothing depends on an adapter except `app`. `app` is the only module that wires everything together.

Stack today: Kotlin, Jetpack Compose, Coroutines, plain SQLite, manual DI. Planned: SQLCipher (no Room) and WorkManager; manual DI stays (PRODUCTION-DIRECTION.md). Minimum Android version is decided after the Phase 0 device matrix.

## Failure behaviour

| Situation | Behaviour |
| :--- | :--- |
| Parser does not recognise a notification | Passes to LLM extraction if available; else ignored and counted for diagnostics |
| Model output invalid after retry | No card; for a user command, ask a clarifying question |
| Model not downloaded or device too small | Rules-only mode; voice still works if the speech model is present |
| Out of memory while loading | Fall back to a smaller registered model, then rules-only |
| Executor fails | Ledger entry, a visible "didn't work" state on the card with retry |

## Testing strategy

- **Unit:** gate rules, validators, time and amount parsing, template parsers against fixtures.
- **Eval harness:** replays a fixed Hinglish and notification test set against any registered model and reports JSON validity, intent accuracy, latency, and memory. A model swap must pass it.
- **Instrumented:** capture-to-card path on a device matrix; macrobenchmarks for cold start, and battery tracking over a day.
- **Security:** red-team fixtures with hostile notification text asserting that no L1/L2 action can run without a user tap.
