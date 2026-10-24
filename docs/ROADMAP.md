# Roadmap

Each phase ends with exit criteria. We do not start the next phase until they are met.

**Language scope (decided): v0.01 is English only.** Hindi and Hinglish come second. Until then, the Hindi and Hinglish items below (command set accuracy, speech-to-text choice, the Hindi quality question) are deferred, and the Phase 0 benchmark runs on the English cases only (`-Language en`).

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

## Phase 2: Assistant core: Tasks, Routines, and Ask

**Deliver**
- Hinglish voice capture to reminders, tasks, alarms, and calendar events.
- Routines: user-defined time or event triggers, created by voice, shown as cards with Edit, Pause, and Undo.
- Messages and notes: draft a message, save a note, in the same typed-action flow.
- Ask: questions over local data with cited records.

**Exit when**
- The Hinglish command set meets its accuracy threshold on the mid-range device, and routines survive reboot and Doze in testing.

## Phase 3: Money (one skill among many)

Money is one skill on the same kernel, not the product. The assistant core comes first so that Bheem is useful for everyday requests before any one skill is deep.

**Deliver**
- Template packs for the top banks and UPI apps, with anonymised fixtures.
- Categorisation, recurring bill detection, due-date cards, splits via message draft, and the UPI hand-off.

**Exit when**
- Parser precision and recall meet the fixture-set thresholds, and a week of real use produces no wrong silent writes.

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
| Does Qwen3.5 at 2B handle Hindi and Hinglish well enough on mid-range phones? | Deferred with Hindi and Hinglish; Qwen3.5 is also blocked by the state leak ([PHASE0-RESULTS.md](PHASE0-RESULTS.md)) |
| Which speech stack for Hinglish, and is the license clear (Sarvam Edge, IndicConformer, Whisper fine-tunes)? Which English speech-to-text for v0.01? | Partly answered: English speech-to-text measured on one speaker (Gemma 4 E2B audio with hints 94% end-to-end action, 2.6 s; Moonshine v2 Small 71%, 0.4 s), see [STT-SHORTLIST.md](STT-SHORTLIST.md); more speakers and a choice are open; Hinglish deferred |
| Does LiteRT-LM support constrained JSON decoding for this model, or do we validate and retry? | Answered for v0.01: not used. One-line answers parsed by code, then validated and gated ([PHASE0-RESULTS.md](PHASE0-RESULTS.md)) |
| Which runtime runs hybrid models such as Qwen3.5 correctly on one long-lived engine (llama.cpp? a newer LiteRT-LM)? LiteRT-LM 0.18.1 leaks state between requests | Open (upstream [#3165](https://github.com/google-ai-edge/LiteRT-LM/issues/3165)); Gemma 4 E2B avoids it |
| How do we get first-token wait from 5–9 s to a few hundred ms (shorter prompt, prompt cache, smaller model, GPU)? | Partly answered: a prompt within two 128-token chunks gives 1.1 s on GPU and 2.2 s on CPU for commands (measured); a few hundred ms needs a prompt cache, not tried |
| Which plain-attention models give acceptable accuracy and speed on a mid-range phone (Qwen3-1.7B/4B int8, Gemma 4 E2B, others)? | Gemma 4 E2B measured (fails two frozen checks, one per backend); the others are not benchmarked on the final prompts |
| Minimum Android version and device floor | Open: one mid-range phone only; needs the flagship and low-end runs |
| Does the gate keep an injection that the model logs as an expense (12.5% on GPU) from reaching the feed without a tap? Sender trust and the notification allow-list | Phase 1 (gate and red-team fixtures) |
| GPU model load took 5 to 182 s across runs; why, and can it be kept under the 15 s limit? | Phase 1 shell work |
| Which exact-alarm permission path is appropriate for reminders? | Phase 1 plan |
| Play Store policy for notification access with this use case | Before Play submission |
| Name and trademark check for "Bheem" | Before public launch |

## Risks

| Risk | Mitigation |
| :--- | :--- |
| A small model is too weak on Hinglish | Rules-first design; larger tier on strong phones; speech model separate from the LLM; fine-tuning later if needed |
| The preferred model family needs a runtime that does not exist yet (Qwen3.5 hybrid state leak in LiteRT-LM 0.18.1) | Keep the model layer swappable; test plain-attention candidates and a second runtime early; the bug is already open upstream as [LiteRT-LM#3165](https://github.com/google-ai-edge/LiteRT-LM/issues/3165), so add our device evidence there rather than filing a new one |
| Notification formats change often | Template packs are data and community-maintained; unknown formats are ignored, not guessed |
| Background limits on aggressive OEM Android skins | Event-driven design; test on Xiaomi, Realme, Samsung; in-app guidance for battery settings |
| Scope creep toward "everything app" | Each new skill must justify itself against the pipeline and the success measures |
