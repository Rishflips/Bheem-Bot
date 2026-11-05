# V2 speed round 1, plus the interview name fix (spec for the implementer)

Build on `v2/agent`. Read `AGENTS.md` first and obey it. Tests first for every item.

Measured on the owner's phone (moto g86 power, 5 Nov 2026): Gemma 4 E4B gave 7.4 to 9.2 s to first text and 13.8 to 17.7 s
per answer with about 400 MB RAM free and 3 GB in swap, so the phone stays on E2B. On E2B the owner still feels replies are
slow. Two causes found in code:

1. **A conversational message costs two model calls.** `CommandPipeline` sends every message `PreRouter.route` returns
   `Route.Model` for to the planner model first; only when it answers `NoAction` does the chat model run.
2. **The model unloads after 3 idle minutes** (`ModelHost.IDLE_MILLIS = 180_000`), so a reply after a pause waits for a full
   reload first.

## Changes

1. **Skip the planner for plain conversation** (`agent-core/.../planner/PreRouter.kt`). Return `Route.NoAction` when the
   message has no action cue and no time words. Action cues (word boundaries, case-insensitive): remind, reminder, note,
   notes, task, tasks, todo, to-do, add, save, put, book, schedule, meeting, event, calendar, alarm, timer, call, dial, ring,
   message, text, whatsapp, sms, email, mail, send, tell, pay, paid, spent, spend, bought, buy, pick up, cancel, delete,
   remove, move, reschedule, route, navigate, directions, open, torch, flashlight, volume, list, track, log. Keep every
   existing rule that runs before this (payment, records, identity, notAssistantWork). The chat model can still suggest a
   command with `Offer|`, so nothing is lost. Add PreRouter tests: "how was your day", "I started going to the gym",
   "tell me a fun fact about space" (contains tell: stays Model, fine), "what should I cook tonight" (time word: Model),
   "buy milk" (Model), "I feel tired" (NoAction).
   The quality gate and the conversation eval must still pass; if a development case changes route, update it only when
   the new route is still the right behaviour, and say so in the commit message.
2. **Keep the model warm while the app is open** (`models/.../host/ModelHost.kt` and the app lifecycle hook that already
   trims on background, see `trimWanted`). While the app is in the foreground the idle unload waits 15 minutes; when the
   app goes to the background keep today's behaviour (unload after 3 minutes, or at once when the system asks to trim).
   Add `ModelHost` tests for both timings with a test clock or delay injection.
3. **Measure the whole turn.** The `BheemChat` log line must also print `route=<chat|recall|planner+chat>` and the total time
   from the owner's send to the final text (`turn <n> ms`), next to the existing first text and answer times and model id.
   Nothing personal in the log (update `LogHygieneTest` allowlist only for these numeric and enum values).
4. **The interview must not store a non-name as the name** (`Interview`, fallback facts and the Q1 answer). A Q1 answer is a
   name only if it is 1 to 3 words of letters (apostrophes and hyphens allowed) and none of the words is in a stop list:
   ready, yes, yeah, yep, ok, okay, sure, hi, hello, hey, fine, good, start, go, next, skip, no, nope, nothing, idk, later,
   cancel, stop, bheem. "Call me Rish" and "I am Rish" and "my name is Rish" give "Rish". If the answer is not a name, Bheem
   asks once more: "Sorry, just your name, what should I call you?" A second non-name answer skips the question. Also apply
   the same check to model-extracted `My name is ...` facts from the interview: drop one whose name fails the check.
   Tests for all of these.

## Done when

`bash .oc/ci.sh tests`, `bash .oc/ci.sh gate`, `bash scripts/check-ui-strings.sh` and `bash scripts/check-network-use.sh`
pass and the change is committed on branch `v2/speed-1`.
