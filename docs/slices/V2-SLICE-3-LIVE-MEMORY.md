# V2 slice 3: live memory (catch facts in normal chat, replace stale ones)

Parent: `docs/BHEEM-AGENT-V2.md`. Builds on slices 1 and 2. Read `AGENTS.md` first and obey it.

## Goal

Bheem keeps learning after the interview. When the owner mentions something lasting in normal conversation, a remember
card appears. When a new fact contradicts an old one, the card offers to replace the old one. Nothing is stored without a
tap.

## Behaviour

1. **Agent suggests facts.** Extend the chat prompt (slice 1 `ChatPrompt.SYSTEM`) with: "If the owner just told you
   something lasting about themselves that is not already in what they told you earlier, add one extra last line:
   Fact|<the fact in the owner's first person, for example: I moved to Bengaluru>." `AgentLoop` splits a `Fact|` line off
   like `Offer|` (never shown, hidden while streaming) and returns it in `AgentReply.fact`.
2. **Grounding check by code.** The suggested fact becomes a card only if its content words come from the owner's own
   message (reuse the `OfferCheck.grounded` idea: every content word of the fact, ignoring function words and the words
   I, my, me, am, is, was, have, has, moved, now, live, in, at, to, the, a, an, must appear in the owner's message, allowing
   simple plurals). Otherwise it is dropped silently. The controller creates the card with the same new
   `CommandPipeline.planFacts` from slice 2 (single fact gives a single card).
3. **Slots and replace.** Add `FactSlots.slot(text): String?` in `core.memory` for facts that can only have one value:
   `name` (my name is, call me), `city` (I live in, I stay in, I moved to), `work` (I work at, I work for, I work as, my job
   is), `birthday` (my birthday is), `partner` (my wife is, my husband is, my partner is). When a RememberFact is proposed
   (by FactRules, the interview, or the agent) and a confirmed fact with the same slot exists, the action gets an extra
   argument `replaces` = the old fact text, and the card reads "Replace: <old> with <new>". On approve the executor stores
   the new fact and removes the old one in one transaction; undo restores the old one.
   This changes the RememberFact argument set: update `ActionCatalog`, the gate validator, gate tests and
   `docs/SECURITY.md` section 6g in the same change, test first. `replaces` must exactly match an existing confirmed fact or
   the gate rejects the action.
4. **What Bheem knows screen** groups facts by kind using `FactSlots.kind(text)`: About you (name, city, work, birthday,
   languages), People, Preferences, Routine, Goals, Other. Pure display; strings in resources.
5. **MemoryBlock** keeps the name fact first, then slot facts (city, work), then the matching ones, within the existing caps.

## Tests (write first)

`FactSlotsTest` (slots and kinds for typical sentences, no slot for "I like tea"); `AgentLoopTest` additions (`Fact|` split,
hidden while streaming, returned on the reply); grounding check cases (accepted when words come from the owner, rejected
when the model invents a city); gate tests for `replaces` (accepted when it matches a confirmed fact, rejected otherwise,
rejected for any other new argument); executor test for replace and undo; `MemoryBlock` ordering test.

## Done when

`bash .oc/ci.sh tests`, `bash .oc/ci.sh gate` and `bash scripts/check-ui-strings.sh` pass and the change is committed on
branch `v2/slice-3`.
