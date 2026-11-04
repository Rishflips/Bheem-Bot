# Assistant slices: evidence (2026-11-04)

Built from `docs/ASSISTANT-DESIGN.md` (advice from Fable 5.1). JVM tests, the storage tests on the phone and the quality gate are green; what was and was not run on the phone is listed at the end. Numbers are measured or targets.

## What was built

| Slice | What the owner gets | Main code |
| :-- | :-- | :-- |
| 1 | The Today screen is a conversation. Ask "what is on tomorrow", "what reminders do I have", "what did I note about the electrician", "show me my tasks" and get an answer in a Bheem bubble with "From: note 20 Oct" under it, without waiting for the model | `memory/Recall`, `assistant/Thread`, `TodayScreen` |
| 2 | A daily brief (due today, clashes, missed, not followed up) on request ("give me my morning brief") and at 7:30 with a count-only notification; the conversation is kept between launches; tasks can be marked done | `brief/Brief`, `BriefSupport.kt`, schema 4 |
| 3 | "call Amma" opens the dialer with the number from the note "Amma's number is ...", "route to the office" opens maps, each after a tap | `PlaceCall`, `OpenRoute`, `PhoneBook`, gate rules |
| 4 | A sentence with several jobs becomes several cards with a "Do all" button; a command missing its time asks "What time should I use for that?"; "yes" approves what is waiting; "sort out my Friday" gives that day's plan | `assistant/Flow.kt`, `Outcome.Plan`, `Outcome.Clarify` |

## Rulings made while building (for the review)

1. **Owner decisions taken from Fable's recommendations** (the owner delegated them): brief as a count-only notification; "Do all" stops at a hand-off; phone numbers from fact notes, no contacts permission; expenses stay a plain list.
2. **Slice 4 differs from the design.** Fable proposed a new multi-line model prompt (`planner-lines-v4.0`). It would need measuring against v3.8 on the phone with the 2-point swap rule, and the planner prompt and its goldens were left alone instead: compound sentences are split by code at clear job boundaries and each part goes through the existing single-action path. Cost: only sentences with clear joining words become plans; a vague request ("sort out my Friday") is answered by code from the records, not by a model-made plan.
3. **Splitting is deliberately conservative.** After a plain "and" only "remind, note, add, save, put, book, schedule, create, set" start a new job; "message" and "tell" only after "then", "also" or "and also". "Remind me to pay the bill and message Ravi" stays one reminder.
4. **No held-out data touched.** The routes for calls and routes apply only when the app supplies a phone book; the eval pipeline has none, so the development and held-out aggregates are unchanged (gate green).
5. **Model never supplies a number.** `PlaceCall` and `OpenRoute` are code-only; a model line for either is refused.
6. **Raw phone results committed**: `eval/results/stage11-cases.json` went into a commit by mistake of the staging command; it is small evidence and stays.

## Chat with the model (2026-11-04)

Built after the owner asked for it: a message no action fits ("explain what GST is", "what is the weather today", "make it shorter") is answered by the language model as a plain question. JVM tests: `ChatTest` (prompt rules, history, cleaning, claims filter, streaming, release of the model). On the phone (measured, Gemma 4 E2B on the graphics chip): warm answers started in 1.1 and 1.5 s and finished in 4.4 and 3.2 s; the first question after the app restarted started in 6.0 s and finished in 9.3 s. Quality is the model's: "explain what GST is" answered "GST stands for Goods and GST Tax" (wrong: Goods and Services Tax), which is why every chat answer is labelled "from the model, may be wrong" and questions about health, money or law carry a fixed caution line. The timings run from the start of the chat call; when the router does not recognise the sentence the planner runs first (a second model call) and that time is not included.

**Opus 5.5 review of chat (PASS WITH CHANGES, no critical findings, chat text cannot reach the gate, an executor, an intent or a log).** Fixed: one chat job for typed and spoken messages, cancelled by Stop, with busy derived from it; the history is taken before the owner's message is added; a spoken greeting skips the pipeline; more phrasings of a false claim of action are caught and explanations are kept; hash characters, format characters and mid-sentence cuts are handled; the label and a caution line for health, money and law; the SECURITY row is narrowed to what the tests show. Not done: a JVM test of the controller's routing (it stays in `AppController`).

**Stop, checked on the phone (measured).** The first version of Stop hid the bubble but the model kept writing for 11 more seconds and the answer was still stored: the engine did not cancel decoding and the replier did not check for cancellation after the engine returned. Now `LiteRtLmEngine` calls `Conversation.cancelProcess()` when the coroutine is cancelled (and after a newline stop), and `ChatReplier` throws if it was cancelled meanwhile. On the phone: Stop pressed 3.1 s after sending, "chat cancelled" logged 43 ms later, no story stored, the input bar usable at once. The Stop button was also moved to the top of the bubble, because at the bottom it moved down as the text grew and a tap missed it. "make it shorter" did not use the earlier answer. "what is the weather today" correctly said it cannot check live information. Temperature 0.2.

## Spoken replies (2026-11-04)

Built: `SpokenText` (agent-core, `SpokenTextTest`), `Speaker` (Android text to speech, offline voices only), a Settings choice (never, when I use voice, always), a "Read aloud" button on bubbles, barge-in (talking, typing or Stop stops the speech). On the phone (measured): the engine bound (Google text to speech), Settings shows the three choices with "When I use voice" selected by default, and with "Always" a typed "hello" made the speech engine start an audio track (usage media, content type speech) while the reply showed. **Not verified:** that anything is audible, how it sounds, which voice was chosen, the "when I use voice" path (needs you to speak), and behaviour with no offline voice installed.

## After the Opus 5.5 review (PASS WITH CHANGES, no critical findings)

Fixed: the model-line refusal for `PlaceCall` and `OpenRoute` now has a test that reaches the model and fails if the check is removed; the saved number is looked up again when the card is tapped (`CallApprovalTest`); "yes" approves only what the answer just before it offered; "Do all" skips failed steps; the brief's waiting cards are filtered by provenance (`Brief.waitingCards`); the Clarifier accepts only time words and plain filler; the brief is composed under a lock; Erase cancels the brief alarm; the debug override is read only by a debuggable build; the conversation loads off the main thread; `pendingClarify` is volatile. Not fixed: a spoken reply to a clarifying question is not joined to the waiting command (typed only); `plan_id` stays unused; answers keep their text after the record is undone (now stated in SECURITY.md).

## Checked on the phone (2026-11-04, Moto g86 power, offline debug build, measured)

- **Conversation and Recall:** "what is on tomorrow" answered in a Bheem bubble with "From: reminder 5 Nov" in about a second, without loading the model (slice 1).
- **Brief:** "give me my morning brief" answered with today's reminders and a real clash (two 7 PM reminders, 0 minutes apart), citing three reminders. The automatic brief was composed on opening the app after 7:30. The 7:30 alarm path was fired with the debug flag: a notification "Your morning: 3 reminders, 1 clash" appeared, with the lock-screen version "Your morning brief is ready. Open Bheem."; without the flag it stays silent when today's brief already exists.
- **Call:** after the note "Amma number is 98765 43210", "call Amma" gave a card "Call Amma 9876543210"; OK opened the Google dialer with 98765 43210 typed in and nothing dialled.
- **Route:** "route to the office" gave a Route card; OK opened Google Maps.
- **Plan:** "remind me tomorrow at 9 to call Arun and add buy salt to my tasks" gave "Here are 2 steps" with two cards and a Do all button; Do all approved both ("Done: 2 of 2").
- **Clarify:** "remind me to call Ravi" asked "What time should I use for that?"; the reply "5 pm" gave the card "call Ravi, Wed 4 Nov, 5:00 PM".
- **Yes:** "yes" after that card approved it.
- **Quirks seen:** the planner turned "add buy salt to my tasks" into the task "add buy salt" (existing single-action behaviour, not changed here). A compound whose second job mentions a day ("note that the invoice is due Friday") was read by the model as a reminder and so asked for a time, which left it out of the plan with the reason shown. A task's "Mark done" button lives on its card in Recent activity, which is far down a long list; it was not reached by the script, so task done state is not checked on the phone. The conversation shows only the last three turns so cards stay in view.

## Not done or not verified
- No model-written wording, no spoken replies, no `READ_CONTACTS`, no `READ_CALENDAR`, no embeddings (left out on purpose in the design).
- Recall is word-overlap search over the records (as Ask was), not FTS5; the 50-question recall set from the design is not built.
- "Do all" and the "yes" path are covered by unit tests of their parts (`PlanRunner`, `FollowUp`); the controller that joins them has no JVM test.
- The case runner `scripts/phone/cases.py` still checks the old "Close" banner in a few places; the conversation bubbles replaced it.
