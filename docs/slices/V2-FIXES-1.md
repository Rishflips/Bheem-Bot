# V2 fixes round 1 (from the final review, spec for the implementer)

Branch: build on `v2/slice-4`, commit on `v2/fixes-1`. Read `AGENTS.md` first and obey it. Tests first for every fix.
Line numbers are approximate.

1. **Gap question must not swallow other messages** (`app/.../AppController.kt`, typed `submit` and voice paths, and
   `answerGap`). Move the decision "is this message an answer to the pending question or something else" into a pure
   helper in `agent-core` (for example `Interview.isAnswerTo(text)` or a small `AnswerRouting` object) so it is unit
   tested. A message is NOT an answer when `DeviceQuestions.answer(...)` returns text, or the pipeline returns anything
   other than `Outcome.Nothing` (Card, Plan, Clarify, Question, Refused). In that case clear `pendingGapQuestion` and
   route the message normally. In `answerGap`, use `Interview.fallbackFacts` only when the extractor threw (model
   unavailable), never when it returned no facts. Clear `pendingGapQuestion` in `onChatPaused()`.
2. **A Fact| card must not replace the pending offer.** In `chat()`, recording the fact plan must not change `offer`
   (add a parameter to `record`, or record the fact first and the offer card last). Test: a reply with both `Offer|` and
   `Fact|` leaves `offer` pointing at the offer card.
3. **Voice during the interview or a pending gap question.** `core/speech/Voice.kt` runs the pipeline before the
   controller decides. When the controller treats the transcript as an interview or gap answer, dismiss the cards of the
   dropped `result.outcome` with the existing quiet dismiss, so no orphan proposal is left pending and no duplicate fact
   is proposed later.
4. **Stronger fact grounding** (`OfferCheck.factGrounded`, core/assistant/Chat.kt):
   (a) Negation parity: reject when exactly one of the owner message and the fact contains a negator (not, no, never,
   don't, doesn't, didn't, isn't, wasn't, can't, won't, hate is not a negator). Reuse the gate's `negated` helper if it fits.
   (b) For a fact that has a `FactSlots.slot`, the slot phrase words (live, stay, moved, work, job, name, call, birthday,
   wife, husband, partner) must appear in the owner's message; remove them from the ignored function words for that
   check. "I visited Chennai last week" must not ground "I live in Chennai"; "I moved to Bengaluru" must still ground
   "I moved to Bengaluru". Update `FactGroundingTest`: remove the case that grounds "I live in Bengaluru" from
   "I moved to Bengaluru", add negation and slot cases.
   (c) Ground interview facts too: in the interview flow, keep a model fact only if `factGrounded(fact, question + " " +
   answer)` holds; otherwise use the code-built fallback fact for that answer.
5. **Remember skipped and answered gap questions.** Persist the indexes of gap questions the owner answered, skipped, or
   whose card they dismissed (preferences, next to the proactive state) and pass them in `ProactiveInput.keptQuestions`,
   so a question is not asked again forever. Test in `ProactiveTest`.
6. **Interview start rule.** Start the interview automatically once on app open when it has never been finished or
   dismissed, whether or not facts already exist (the owner wants every user to get the first-chat interview). Store a
   "started" flag and the answers so far, so a restart of the app resumes at the next question instead of Q1.
7. **`Interview.isCommand` false positives.** Treat an interview answer as a command only when the pipeline returns
   Card, Plan or Clarify for it (same helper as fix 1). "Open University", "Open to anything", "Call centre work",
   "Book reading" are answers. Test them.
8. **Same-slot duplicates in one plan.** In `CommandPipeline.planFacts`, keep only the first fact per `FactSlots.slot`.
9. **Thanks after the review.** Say the thanks line using the name only once the name fact was actually kept; fix the
   "N of M are suggestions" notice to count after dedupe and refusals.
10. **Main-thread reads.** Run the `maybeNudge` reads and the `AgentTools` lambdas' database reads on `Dispatchers.IO`.
11. **Docs.** In `docs/SECURITY.md` 6g, say the gate enforces an exact match with a confirmed fact and that code picks the
   slot in `CommandPipeline.remember`.

Done when `bash .oc/ci.sh tests`, `bash .oc/ci.sh gate` and `bash scripts/check-ui-strings.sh` pass.
