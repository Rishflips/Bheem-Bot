# V2 slice 4: quiet proactiveness (spec for the implementer)

Parent: `docs/BHEEM-AGENT-V2.md`. Builds on slices 1 to 3. Read `AGENTS.md` first and obey it.

## Goal

Bheem speaks first now and then, only when it is useful, inside the app. No new notifications, no network.

## Behaviour

`core/assistant/Proactive.kt`: `fun next(now: LocalDateTime, input: ProactiveInput, state: ProactiveState): Nudge?`
Pure Kotlin, no model. Called when the app opens (and when the chat screen resumes). At most one nudge per open, at most 3
per day, never between 22:00 and 07:00, and the same nudge (by key) never twice in 7 days. `ProactiveState` (last shown
keys with times) is stored next to existing preferences.

Nudge kinds, checked in this order:
1. **Clash.** Two scheduled reminders less than 30 minutes apart in the next 24 hours: "Heads up: <a> at <t1> and <b> at
   <t2> are close together." Key: both reminder ids.
2. **Follow up.** An open task created more than 3 days ago: "Still planning to <task>? Say remind me ... and I will set
   it." Key: task id. At most one task per day.
3. **Get to know you.** An interview question from slice 2 that has no matching confirmed fact (use `FactSlots` from
   slice 3: name, city, work; plus a kept flag per other question): ask that one question as a Bheem turn. The owner's
   next message is treated as the answer to that single question and goes through the slice 2 fact flow (one card).
   At most once a day. Key: question index.
4. Otherwise nothing.

Each nudge is added as a Bheem chat turn (a new `Turn.Nudge` or `Turn.Chat` with a small "Bheem noticed" label, string in
resources). Tapping nothing is fine; nudges never create cards on their own.

## Tests (write first)

`ProactiveTest`: clash found and worded; no clash for 45 minutes apart; follow-up only after 3 days; quiet hours; daily cap;
7-day repeat rule; gap question chosen when city fact missing and skipped when present; nothing when all is well.

## Done when

`bash .oc/ci.sh tests`, `bash .oc/ci.sh gate` and `bash scripts/check-ui-strings.sh` pass and the change is committed on
branch `v2/slice-4`.
