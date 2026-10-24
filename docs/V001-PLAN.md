# v0.01 plan (approved 2026-10-24)

Bheem is the harness. Models, speech engines and executors sit behind interfaces, so a better model is a configuration change. v0.01 is the smallest release that proves the harness end to end on a real phone, in English, then ships daily.

Numbers marked **measured** come from our phone runs ([PHASE0-RESULTS.md](PHASE0-RESULTS.md), [STT-SHORTLIST.md](STT-SHORTLIST.md), [MODELS.md](MODELS.md)). Numbers marked **target** are goals.

## What v0.01 is

A general personal assistant you type or talk to, that proposes one typed action, shows it on a card, and does it only after your tap.

**In v0.01**
- The Bar: type, and hold to talk.
- Actions: reminders, tasks, calendar events, notes, drafted WhatsApp messages, and Ask (questions over your own notes and tasks, with the records cited).
- Confirm card for every action, an Undo for a short window, and an activity ledger.
- Today feed: pending cards, upcoming reminders, recent activity.
- Model download on Wi-Fi (resumable), rules-only mode until it finishes, GPU by default with CPU fallback.
- Everything on the phone. No account, no cloud, no telemetry.

**Left out of v0.01, on purpose**
- Notification access and the money diary (Phase 3). They bring untrusted text, the Play Store notification-access policy, and the 12.5% injection leak on GPU (**measured**). Leaving them out removes the biggest security surface from the first release.
- UPI payments (L2 risk), Hindi and Hinglish, routines, skill packs, health.

## Build order (each step ships something)

| Step | Delivers | Done when |
| :--- | :--- | :--- |
| M0 Close Phase 0 | Decision note with the assistant set added to the frozen checks; dates resolved in code | The owner signs the note |
| M1 `agent-core` (pure Kotlin, no Android, no model runtime) | Typed actions, the line parser moved out of `eval`, a deterministic time resolver, the gate and risk levels, the ledger interface | JVM tests green, including the 38-command assistant set through the resolver |
| M2 App shell | Compose app, Bar (type), confirm cards, executors (alarm, calendar intent, local notes and tasks store, WhatsApp draft), ledger and Undo | A typed command becomes a card, a tap runs it, Undo works, on a real phone |
| M3 Model layer | LiteRT-LM adapter, download with progress, warm start, backend fallback | First useful answer within the first-token limit (**target** 1.5 s p95 on GPU) |
| M4 Voice | Speech interface; Gemma audio with the command-word hint as the default, Moonshine as the fast option | Spoken command to card on the phone; the choice recorded with its limits |
| M5 Ask | Full-text search over notes and tasks, answers cite records, read-only | Questions in the assistant set answered with the right records |
| M6 Release | Signed APK on GitHub, install guide, crash-free capture path, idle battery measured | Gate checklist below passes |

## Release gate for v0.01

- Command accuracy: the frozen thresholds in `eval/thresholds.json` plus the assistant set once the owner has reviewed its limits (**target**: right action at least 90% on both sets).
- Nothing runs without a tap; the model's output never reaches an executor unvalidated; invented message text is never shown as fact.
- No crash in the capture-to-card path across a written test list.
- Idle battery and peak RAM measured on the mid-range phone and written down.
- Signed release, checksum, and the README install steps tested on a clean phone.

## Shipping daily after v0.01

- Trunk-based development. CI runs the unit tests and the command checks on every change; the on-phone benchmark runs before each release.
- A dated build every day that is green; a tagged release when the gate passes again.
- Model, speech and prompt changes go through the eval harness first ([MODELS.md](MODELS.md), "Rules for swapping a model").
- Any new action type updates [SECURITY.md](SECURITY.md) and adds gate tests in the same change.

## Decisions (owner, 2026-10-24)

1. Notification access and the money diary are left out of v0.01.
2. Minimum Android version: 12 (API 31). A target, not yet checked against what LiteRT-LM and the executors need.
3. App id: keep `app.bheem.bench` for now.
4. Default voice engine for v0.01: Gemma audio with the command-word hint (about 2.6 s, 94% end-to-end action on one speaker, **measured**), until more speakers are recorded.
