# Stage 6 evidence: executors, failure and undo, Ask, planner fixes

Phone: Moto g86 power (Android 16), debug builds, 2026-10-27. Numbers are measured on this one phone unless marked target.

JVM tests: 691 across the modules, 0 failures (measured); `scripts/check-tests-ran.py`, `scripts/check-network-use.sh` and `:app:verifyManifests` pass.

## Assistant sets (gate, scored through the pipeline)

The model was run on the phone with prompt v3.8 (`proposer-v3.8-line`) on both sets, GPU and CPU, and the saved answers are scored by `:eval:gate` through `CommandPipeline`.

| Set | Cases | Backend | Action | Args | JSON validity |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Development (`ast-001` to `ast-100`) | 100 | GPU | 96.0% | 96.0% | 100.0% |
| Development | 100 | CPU | 96.0% | 96.0% | 100.0% |
| Held-out (`ast-h01` to `ast-h30`) | 30 | GPU | 96.7% | 96.7% | 96.7% |
| Held-out | 30 | CPU | 96.7% | 96.7% | 96.7% |

The limit for action is 90% (target) and for args 85% (target); both sets pass. The held-out JSON validity of 96.7% is one unreadable answer per backend against a 98% limit, which on 30 cases allows no failure; it is listed as a known failure. The held-out cases were written by a separate agent from a short brief, and I did not read them or the model's answers to them. Before the code changes the 38 old cases measured 89.5% action when the saved answers were scored as raw lines. The new numbers use a larger set and a different scoring (through the pipeline), so they are not a like-for-like comparison. The prompt changed only by removing payments and the spending wording; it was not tuned against either set.

## On the phone

| Check | Result |
| :-- | :-- |
| Ask, three questions | "where did I keep the scooter key" listed the note ("scooter key is in the cupboard (note, ...)"); "show me my tasks" listed the task ("You have 1 task: renew the passport"; the first version found nothing and was fixed, see below); "what did I spend at Chai Point" listed the expense record without a total |
| Undo of a note, a task and a reminder | Each was approved and undone inside the window: the note and the task left the feed and the task list; the reminder's alarm count went from 2 to 1 in `dumpsys alarm` |
| Calendar app disabled | The card failed with "No calendar app is available. Install or enable one, then try again." After the app was enabled again, Try again opened the calendar and the card said "Opened in your calendar" |
| WhatsApp disabled | The card failed with "WhatsApp is not installed. Install it, then try again." After enabling WhatsApp, Try again opened its share screen and the card said "Opened in WhatsApp" |

The two apps were disabled for under a minute each with `pm disable-user` and enabled again; `pm list packages -d` shows neither disabled afterwards. Nothing was sent from WhatsApp and no calendar event was saved (the apps were left with the back key).

## Found on the phone and fixed

- "show me my tasks" found nothing because Ask searched for the word "task" inside the records. Ask now lists records of the kind named (tasks, notes, expenses), newest first, with a count.
- Failure banners ended in two periods ("...try again.."); one sentence helper now adds a period only when missing.

## Not done

- The Opus review of stage 6 (see the review section once it has run).
- A reminder, a note and a task were undone on the phone; the hand-off cards were retried once each. The retry of an interrupted hand-off (process killed after the app opened) was not tested: the card says "interrupted, check before retrying".
- `LogExpense` stays as a plain local record (no notifications, no totals). That was the recommendation in the direction document; the owner has not said otherwise.
