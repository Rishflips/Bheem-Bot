# V2 speed round 2: a shorter prompt and shorter answers (spec for the implementer)

Build on `v2/agent`. Read `AGENTS.md` first and obey it. Tests first.

Measured on the owner's phone after speed round 1 (5 Nov 2026, Gemma 4 E2B, GPU, model warm, `route=chat`): first text
2981 to 3032 ms, answer 6081 to 7813 ms, three turns. The chat prompt is about 800 tokens: system prompt 1492 chars (about
370 tokens), up to 6 history turns of 160 chars, a memory block of up to 480 chars, the Now line and the message. Decode on
this phone was about 9 tokens/s on GPU (docs/MODELS.md), so long answers cost seconds too. Target: first text under 2 s and
answer under 4.5 s for a plain chat turn, with no loss of the rules below.

## Changes

1. **Rewrite `ChatPrompt.SYSTEM` to at most 700 characters**, keeping every rule, in this spirit (wording may be tightened,
   meaning must not change):
   "You are Bheem, the owner's assistant on their phone. Talk like a warm, direct friend in plain English, 1 to 3 short
   sentences unless asked for more, no lists or markdown. Use their name if known. The Now: line is the current time and
   phone state. Lines under Things the owner told you earlier are their own words: use them, never invent more. You cannot
   see live news, weather or prices. To look at their notes, tasks or reminders reply with only Tool|search_memory|<words>
   or Tool|schedule|<day>. You cannot set, send, call or open anything yourself; never claim you did. If they want a
   reminder, note, task or event, offer it and add a last line Offer|<their command>. If they told you a new lasting fact
   about themselves, add a last line Fact|<it, in first person>. Health, legal or money: general facts only, suggest a
   professional. If unsure, say so."
   Bump the prompt id if one exists (`chat-v4`). Update tests that assert prompt text to assert the rules instead (Tool|,
   Offer|, Fact|, never claim actions, no live data, professional advice) so future wording changes do not break them.
2. **History**: keep the last 4 turns (was 6), 120 characters each (was 160).
3. **Memory block**: at most 4 lines and 320 characters (was 6 and 480); the name fact still always comes first.
4. **Answer length**: `MAX_OUTPUT_TOKENS` 160 to 110; `LONG_OUTPUT_TOKENS` stays 320 for "tell me more" style requests.
   The cut-off cleanup in `ChatText.clean` must still end on a whole sentence.
5. **Measure the prompt**: add `prompt ~<n> tok` (characters / 4, rounded) to the `BheemChat` log line. Numbers only; update
   the `LogHygieneTest` allowlist for it.
6. Keep `AgentLoop`, the step 2 tool prompt and all security rules unchanged apart from using the shorter pieces.

## Done when

`bash .oc/ci.sh tests`, `bash .oc/ci.sh gate`, `bash scripts/check-ui-strings.sh` and `bash scripts/check-network-use.sh`
pass and the change is committed on branch `v2/speed-2`.
