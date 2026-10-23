# Roadmap

Each phase ends with exit criteria. We do not start the next phase until they are met.

## Phase 0: Evidence (about 2 weeks)

Choose the model and speech stack from measurements, not model cards.

**Deliver**
- A throwaway benchmark app that loads each candidate model and runs the test sets.
- The Hinglish command set, notification fixture set, and adversarial set in `eval/`.
- A results report: per model and per device, JSON validity, accuracy, latency, memory, thermals, battery, and speech word error rate.
- Thresholds written down, and the primary model confirmed or replaced.

**Exit when**
- A model meets the accuracy and memory thresholds on the mid-range device, or we have chosen a rules-heavy fallback and written why.
- Hindi and Hinglish speech-to-text is chosen, with its license cleared.

## Phase 1: Kernel and shell

**Deliver**
- Project scaffold, CI, and the module layout from [ARCHITECTURE.md](ARCHITECTURE.md).
- Event pipeline, the gate with tests, action catalog, executors, the ledger, encrypted storage, and the swappable model layer with the first adapter.
- App shell: Today feed, the Bar, onboarding, rules-only mode, model download.

**Exit when**
- A fake event travels sense to card to approval to executor with full ledger entries, on a device, with the red-team fixtures passing.
- Cold start and idle battery meet the targets set in Phase 0.

## Phase 2: Money

**Deliver**
- Template packs for the top banks and UPI apps, with anonymised fixtures.
- Categorisation, recurring bill detection, due-date cards, splits via message draft, and the UPI hand-off.

**Exit when**
- Parser precision and recall meet the fixture-set thresholds, and a week of real use produces no wrong silent writes.

## Phase 3: Tasks, Routines, and Ask

**Deliver**
- Hinglish voice capture to reminders, tasks, alarms, and calendar events.
- Routines: user-defined time or event triggers, created by voice, shown as cards with Edit, Pause, and Undo.
- Ask: questions over local data with cited records.

**Exit when**
- The Hinglish command set meets its accuracy threshold on the mid-range device, and routines survive reboot and Doze in testing.

## Phase 4: Polish and beta

**Deliver**
- Motion, haptics, accessibility pass, empty and error states, localisation (English, Hindi).
- Skill pack import with the review screen.
- Signed releases on GitHub and F-Droid. The `offline` flavor published.
- Independent security review of the gate, storage, and downloader.

**Exit when**
- Beta users complete a full day with no crashes in the capture path, and the review's high-severity findings are closed.

## After beta

1. Play Store submission, subject to the notification-access policy declaration.
2. v1.1 Health-lite.
3. More Indic languages, as speech models are validated.
4. Partner routes for Account Aggregator or ONDC, only if a regulated partner agrees.

## Open questions

| Question | Where it is resolved |
| :--- | :--- |
| Does Qwen3.5 at 2B handle Hindi and Hinglish well enough on mid-range phones? | Phase 0 |
| Which speech stack for Hinglish, and is the license clear (Sarvam Edge, IndicConformer, Whisper fine-tunes)? | Phase 0 |
| Does LiteRT-LM support constrained JSON decoding for this model, or do we validate and retry? | Phase 0 |
| Which runtime runs hybrid models such as Qwen3.5 correctly on one long-lived engine (llama.cpp? a newer LiteRT-LM)? LiteRT-LM 0.18.1 leaks state between requests | Phase 0 |
| How do we get first-token wait from 5–9 s to a few hundred ms (shorter prompt, prompt cache, smaller model, GPU)? | Phase 0 |
| Which plain-attention models give acceptable accuracy and speed on a mid-range phone (Qwen3-1.7B/4B int8, Gemma 4 E2B, others)? | Phase 0 |
| Minimum Android version and device floor | Phase 0 device matrix |
| Which exact-alarm permission path is appropriate for reminders? | Phase 1 plan |
| Play Store policy for notification access with this use case | Before Play submission |
| Name and trademark check for "Bheem" | Before public launch |

## Risks

| Risk | Mitigation |
| :--- | :--- |
| A small model is too weak on Hinglish | Rules-first design; larger tier on strong phones; speech model separate from the LLM; fine-tuning later if needed |
| The preferred model family needs a runtime that does not exist yet (Qwen3.5 hybrid state leak in LiteRT-LM 0.18.1) | Keep the model layer swappable; test plain-attention candidates and a second runtime early; report the bug upstream |
| Notification formats change often | Template packs are data and community-maintained; unknown formats are ignored, not guessed |
| Background limits on aggressive OEM Android skins | Event-driven design; test on Xiaomi, Realme, Samsung; in-app guidance for battery settings |
| Scope creep toward "everything app" | Each new skill must justify itself against the pipeline and the success measures |
