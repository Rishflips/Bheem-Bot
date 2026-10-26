# Stage 6 evidence: executors, failure and undo, Ask, planner fixes

Phone: Moto g86 power (Android 16), debug builds, 2026-10-27. Numbers are measured on this one phone unless marked target.

JVM tests: 708 across the modules, 0 failures, and 31 instrumented storage tests on the phone (measured, after the review fixes); `scripts/check-tests-ran.py`, `scripts/check-network-use.sh` and `:app:verifyManifests` pass.

## Assistant sets (gate, scored through the pipeline)

The model was run on the phone with prompt v3.8 (`proposer-v3.8-line`) on both sets, GPU and CPU. `:eval:gate` scores the saved answers through `CommandPipeline` (pre-router, time repair, policy gate). The numbers below are from the final code, after the review fixes; the saved model answers are the same ones.

| Set | Cases | Backend | Action | Args | Model alone | Decided by code | JSON validity |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| Development (`ast-001` to `ast-100`) | 100 | GPU | 97.0% | 97.0% | 92.0% | 17.0% | 100.0% |
| Development | 100 | CPU | 97.0% | 97.0% | 93.0% | 17.0% | 100.0% |
| Held-out (`ast-h01` to `ast-h30`) | 30 | GPU | 100.0% | 100.0% | 93.3% | 6.7% | 96.7% |
| Held-out | 30 | CPU | 100.0% | 100.0% | 93.3% | 6.7% | 96.7% |

- "Model alone" scores the raw model line with no code in front of it. Its floor is 85% (target) and the share decided by code is limited to 25% (target); both are gate checks now, so a router that grows to carry the number is caught, and so is a model swap that is worse on its own.
- Action and args limits are 90% and 85% (targets); both sets pass.
- The held-out set was written by a separate agent from a short brief. I did not read it or the model's answers to it. Its first measurement was 96.7%; after the review fixes it reads 100.0% (the payment route, the stricter message check, the date and time changes). I did not look at which cases changed, because that would mean reading them.
- The router rules were first written looking at the failing development cases, so the development number is inflated by about 4 points (92 to 93% alone, 97% with code); the held-out gap is 93.3% to 100.0% with 6.7% decided by code.
- JSON validity is read from the raw answer. The held-out 96.7% is one unreadable answer per backend against a 98% limit, which allows no failure on 30 cases; it is listed as a known failure.
- Before the code changes the 38 old cases measured 89.5% action when the saved answers were scored as raw lines. The new numbers use a larger set and a different scoring, so they are not a like-for-like comparison.
- The prompt changed by removing payments and the spending wording, and by adding "calls, payments" to the no-action hint. The word "calls" was added after seeing development case `ast-038` ("call my mother"), so the prompt was nudged against the development set once; it was not tuned against the held-out set.

## On the phone

| Check | Result |
| :-- | :-- |
| Ask, three questions | "where did I keep the scooter key" listed the note ("scooter key is in the cupboard (note, ...)"); "show me my tasks" listed the task ("You have 1 task: renew the passport"; the first version found nothing and was fixed, see below); "what did I spend at Chai Point" listed the expense record without a total |
| Undo of a note, a task and a reminder | Each was approved and undone inside the window: the note and the task left the feed and the task list; the reminder's alarm count went from 2 to 1 in `dumpsys alarm` |
| Calendar app disabled | The card failed with "No calendar app is available. Install or enable one, then try again." After the app was enabled again, Try again opened the calendar and the card said "Opened in your calendar" |
| WhatsApp disabled | The card failed with "WhatsApp is not installed. Install it, then try again." After enabling WhatsApp, Try again opened its share screen and the card said "Opened in WhatsApp" |

The two apps were disabled for under a minute each with `pm disable-user` and enabled again; `pm list packages -d` shows neither disabled afterwards. Nothing was sent from WhatsApp and no calendar event was saved (the apps were left with the back key).

The phone checks below were made on the build before the review fixes; the fixed build (router, resolver, message check, Ask kinds) was built and tested on the JVM and the gate but not installed and exercised on the phone again.

## Found on the phone and fixed

- "show me my tasks" found nothing because Ask searched for the word "task" inside the records. Ask now lists records of the kind named (tasks, notes, expenses), newest first, with a count.
- Failure banners ended in two periods ("...try again.."); one sentence helper now adds a period only when missing.

## Not done

- Not shown on the phone: the SMS hand-off failure, the reminder executor's `SecurityException` path, and a retry after a hand-off that was interrupted after the app had opened.
- A reminder, a note and a task were undone on the phone; the hand-off cards were retried once each. The retry of an interrupted hand-off (process killed after the app opened) was not tested: the card says "interrupted, check before retrying".
- `LogExpense` stays as a plain local record (no notifications, no totals). That was the recommendation in the direction document; the owner has not said otherwise.

## Review

An Opus review (high effort) gave PASS WITH CHANGES. Fixed afterwards, each with a test written first:

- Clock-only resolution replaced correct model dates with today or tomorrow ("on December 5 at 9 am" became today). The resolver now returns nothing when the text holds a date it cannot read (month names, ordinals, dd/mm, "next week", weekend, festival names), so the model's date is kept; the rules planner uses the same check. A day or week offset ("in 3 days") now keeps the model's time of day and uses the current time only when the model gave none.
- The pre-router took real requests: "call mom later", "play tennis this weekend", "explain to Ravi on WhatsApp ...", "show me my reminders" (Ask cannot read reminders), and compound lines ("list my tasks and add buy milk"). Time words, messaging and note verbs and compound lines now keep a request with the model, and reminders were removed from the memory route.
- A payment request ("send 1500 rupees to Suresh") became an expense card. An imperative payment is now refused by code with "payments come later" before the model runs; "I paid 380 rupees to Uber" still logs an expense.
- The message check passed meaning flips ("I won't come" to "I will come", "I" to "you") and refused two-word names, apostrophes and non-Latin names. Pronouns are no longer exempt, a negation must match, the recipient may be a run of up to three words, and tokens keep combining marks.
- An interrupted write left a note or task nobody could undo. Local executors now remove their record when the failed proposal is dismissed, and the recheck-reject path cleans up too.
- Ask treated the verb "note" as a kind filter; it now reads kinds from the plural, or from "my/the/a" plus the singular.
- The held-out protection is now in `AGENTS.md`, `.ignore`, `.rgignore` and `.gitignore`, and `bench-release.ps1` runs the held-out set.
- `SECURITY.md` no longer claims VPA checks and describes the new message and router rules and their limits.

Not changed, listed: the one-argument `execute` still exists next to the proposal-id form; an executor exception message can reach a card in raw words; the sweep text after an interrupted hand-off is generic ("check before retrying"); `setPackage("com.whatsapp")` does not reach WhatsApp Business and a disabled WhatsApp reads as "not installed"; the 98% JSON validity limit cannot pass a 30-case set with one miss; the grounding rule is English only and does not catch number words against digits.
