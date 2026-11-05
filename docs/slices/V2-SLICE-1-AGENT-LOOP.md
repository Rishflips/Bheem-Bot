# V2 slice 1: one voice and a read-tool loop (spec for the implementer)

Parent: `docs/BHEEM-AGENT-V2.md`. Read `AGENTS.md` first and obey it (no network in core, held-out files closed,
test-first, no em dash in any text shown to the owner).

## Goal

Every reply the owner reads comes from the same Bheem persona, grounded in the owner's own records. Fixed paragraphs and
"Here is what I found:" are no longer the answer itself.

## Changes

1. **`agent-core/src/main/kotlin/app/bheem/core/assistant/AgentLoop.kt`** (new).
   - `interface AgentTools { fun searchMemory(query: String): ToolResult; fun schedule(whenWords: String): ToolResult }`
     with `data class ToolResult(val text: String, val citations: List<Citation>)` (`Citation` from `core.memory`).
   - `class AgentLoop(host: ModelHost, tools: AgentTools)` with
     `suspend fun reply(history: List<Turn>, text: String, nowLine: String?, memoryBlock: String?, grounding: ToolResult? = null, onPartial: (String) -> Unit = {}): AgentReply`
     and `data class AgentReply(val text: String, val offer: String?, val citations: List<Citation>)`.
   - Step 1 (skipped when `grounding` is given): one generation with `ChatPrompt.SYSTEM` and `ChatText.message(...)`. If the
     first non-blank line of the output starts with `Tool|`, parse `Tool|<name>|<argument>`. Names: `search_memory`,
     `schedule`. Argument trimmed, capped at 200 chars. Run the tool. An unknown name or empty argument means no tool:
     strip the line and treat the rest as the answer.
   - Step 2 (after a tool call, or straight away with `grounding`): one more generation whose user message is the step 1
     message plus `\n\nResult of <name> (the owner's own records):\n<result text>\n\nAnswer the owner now from this result
     and what you know about them. Do not call a tool.` Any `Tool|` line in step 2 output is removed. At most one tool call
     per owner message.
   - Output goes through the existing `ChatText.splitOffer`, `ChatText.clean` and caution rule, same as `ChatReplier`.
     While streaming, `onPartial` must never show a `Tool|` line or a fragment that could grow into one (reuse the
     `hideUnfinishedOffer` idea).
   - `citations` = the tool or grounding citations.
2. **`ChatPrompt.SYSTEM`**: replace the sentence "You cannot look up anything else in the owner's notes, tasks, reminders
   or contacts in this chat, ..." with: "If you need the owner's notes, tasks or reminders, reply with only one line:
   Tool|search_memory|<words to search> or Tool|schedule|<today, tomorrow, a weekday or this week>. You will get the result
   and then answer. You cannot set reminders, send messages, call or open anything yourself: never say that you did." Keep
   the rest. If a prompt id constant exists, bump it to `chat-v3`.
3. **`app/.../AppController.kt`** (typed `submit` and voice `voiceMessage` alike):
   - Remove the `SmallTalk.reply(...)` short circuit. Keep `DeviceQuestions.answer(...)` as the only instant code answer.
   - `Outcome.Question`: run `graph.recall.answer(query, now)` as today, then call the agent with
     `grounding = ToolResult(recall.text, recall.citations)`. Add the model reply as `Turn.Chat`, then, only when the recall
     had citations, the exact recall list as the existing `Turn.Answer` (the owner sees the warm reply and the exact
     records). If the model is unavailable, fall back to today's `Turn.Answer` alone.
   - `Outcome.Nothing`: call the agent (with tools) instead of `graph.chat.replyWithOffer`. Keep the offer card logic and
     the timing log.
   - Wire `AgentTools` in `AppGraph`: `searchMemory(q) = recall.answer(q, now)`, `schedule(w) = recall.answer("what is on $w", now)`.
4. **Delete `SmallTalk`** and its test if nothing else uses it.
5. **`eval/.../conversation/ConversationRunner.kt`** must mirror the new order: no smalltalk path; a records question
   records `path = "recall"` and the grounded prompt; general messages `path = "chat"`. Update the 4 development cases in
   `eval/data/conversations.jsonl` that expect `smalltalk` to expect `chat`. Do not open held-out files.
6. **Docs**: add a short paragraph to `docs/SECURITY.md` stating that the chat model now has two read-only tools over the
   owner's own records, no write or device tools, and that write actions still go only through the pipeline and gate.

## Tests (write first, watch them fail)

`agent-core/src/test/kotlin/app/bheem/core/assistant/AgentLoopTest.kt` with a fake `LlmEngine` that returns scripted outputs
per call (see `OfferTest.kt` for a fake host):
- plain answer: one engine call, text returned, no citations;
- `Tool|search_memory|passport`: tool called with `passport`, second request's user text contains the tool result, final text
  is the second output, citations from the tool;
- unknown tool name: no tool call, line stripped;
- `grounding` given: exactly one engine call, and its user text contains the grounding;
- a `Tool|` line in step 2 is removed from the final text;
- `onPartial` never receives text containing `Tool|` (feed partials `"Tool"`, `"Tool|sea"`).
Update `ConversationRunnerTest` and any test that expected small talk.

## Done when

`~/ci.sh tests` and `~/ci.sh gate` pass in the bheem sandbox and the diff is committed on branch `v2/slice-1` with a clear
message. Do not build or install the app; Opus reviews first.
