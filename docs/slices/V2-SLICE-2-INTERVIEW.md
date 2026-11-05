# V2 slice 2: the first-chat interview (spec for the implementer)

Parent: `docs/BHEEM-AGENT-V2.md`. Builds on slice 1. Read `AGENTS.md` first and obey it.

## Goal

In the very first chat Bheem gets to know the owner and keeps it as confirmed facts. Nothing becomes a fact without a tap.

## Behaviour

1. **Start.** The interview starts by itself on app open when there are no confirmed facts and it has never been finished
   or dismissed (a stored flag). It can also be started at any time by the phrases "get to know me", "interview me",
   "learn about me", "update what you know about me", and from a Settings row "Tell Bheem about you".
2. **Questions** (code-written, warm, one at a time, shown as Bheem chat turns):
   1. "Hi, I am Bheem. I would like to get to know you so I can actually be useful. What should I call you?"
   2. "Nice to meet you. Which city do you live in?"
   3. "What do you do, work or study?"
   4. "Who are the important people in your life? Family, friends, anyone I should know by name."
   5. "What does a normal day look like for you? Wake up time, work hours, anything regular."
   6. "Any preferences I should remember? Food, favourite places, things you dislike."
   7. "What are you working towards right now?"
   8. "Which languages do you like to use?"
   While the interview is active, each owner message is the answer to the current question. "skip" or "next" moves on;
   "stop", "later" or "cancel" ends it early (answers so far still go to step 3). A message that is clearly a command the
   pipeline turns into a card (for example "remind me at 5 to call Amma") is handled normally and the same question is
   asked again.
3. **Turn answers into fact candidates.** One model call (no tools, temperature 0) reads the question and answer pairs and
   writes lasting facts in the owner's first person, one per line as `fact|<fact>`, for example `fact|My name is Rish`,
   `fact|I live in Chennai`, `fact|My sister is Meena`. Parse with the same rules as `Extraction.parse` (length, no control
   characters, no links, dedupe against existing facts), up to 12 facts. If the model is unavailable, fall back to one
   fact per answered question built by code from the answer: Q1 `My name is <answer>`, Q2 `I live in <answer>`, others
   `<short topic>: <answer>` capped at 120 chars.
4. **Suggestions from old notes.** Also offer up to 5 facts suggested from the owner's own notes (NOTE records the owner
   created; same extraction call style). These are listed after the interview facts and marked as suggestions.
5. **One review card.** Show all candidates as one plan card titled "What I learned about you" with one `RememberFact`
   step per fact, built through the pipeline and gate (add a `CommandPipeline.planFacts(facts, now)` that returns an
   `Outcome.Plan` of `RememberFact` cards via the existing `decide` path). The owner can approve all with one tap, or
   approve or dismiss each step, exactly like existing plans. Then set the finished flag.
6. **Thanks.** After the review, Bheem says one warm line using the name if it was kept.

## Code places (suggested)

- `agent-core/.../assistant/Interview.kt`: questions, state machine (`start`, `answer(text)`, `skip`, `stop`, `isDone`,
  `pairs()`), the fallback fact builder, and the parse of `fact|` lines. Pure Kotlin, no model.
- `agent-core/.../memory/InterviewFacts.kt`: the model call for answers and for notes (like `MemoryExtractor`).
- `AppController`: interview state, the first-open trigger, the phrases, routing answers while active.
- Settings row and a string resource for its label (no hard-coded UI strings, `scripts/check-ui-strings.sh` must pass).
- The stored flag next to existing preferences.

## Tests (write first)

`InterviewTest`: question order; skip; stop early keeps answers; a command message does not consume the question;
fallback facts for Q1 and Q2; `fact|` parsing and dedupe; the 12 cap. `CommandPipeline` test: `planFacts` returns a plan
of RememberFact cards and ledger rows like other plans. A controller-level test if one exists for the flow, otherwise keep
the logic in `Interview` so it is unit tested.

## Security

No new action type (RememberFact exists). The extraction reads only the owner's own words and own notes, has no tools, and
its output is only ever shown as cards waiting for a tap. State this in `docs/SECURITY.md` in one short paragraph.

## Done when

`bash .oc/ci.sh tests` and `bash .oc/ci.sh gate` pass and the change is committed on branch `v2/slice-2`.
