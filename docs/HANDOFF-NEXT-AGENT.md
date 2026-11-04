# Handoff to the next agent (written 2026-11-04)

Read this first, then `AGENTS.md`, `docs/HANDOFF.md`, `docs/PERSONAL-AGENT-DESIGN.md` and `docs/evidence/PERSONAL-AGENT-BASELINE.md`. The owner is frustrated after about ten days and does not want to spend more time or usage on process. Be short, be honest, check things on the phone path before saying they work, and do not repeat what is already decided.

## What the owner wants

An on-device Android assistant that feels like a personal, persistent-memory agent (like the Grok bot or OpenMausBot style they have seen), running a local model (Gemma 4 E2B on LiteRT-LM 0.18.1). Not generic, not scripted, not a notes or reminders app. Every action still waits for a tap and the model only proposes (see `AGENTS.md`). Web access is deferred on purpose.

## Where the code is

- Repo `C:\Users\Gowrishankar\Desktop\Bheem.Bot`, remote `https://github.com/Rishflips/Bheem-Bot.git`.
- Work branch: **`wip/personal-agent`**, head `1813440`, pushed. **Not merged** into `phase0-benchmark` or `main`. `phase0-benchmark` holds the UI slices 1 to 7 and the assistant layer. Several old `wip/*` branches on the remote are fully merged and can be deleted. `fix/ui-rough-edges` (4be6a91) is also unmerged.
- App id `app.bheem.bench`. The build installed on the owner's phone (`ZA22362NT3`) is `wip/personal-agent` at e16fdf4 (debug APK, `full` flavor, sha256 `371a2427af62865fa109bcb5ac2b5531de4783cbfaa2e0f1f1ed181cdccec392`). The file is `Desktop\apk-drop\bheem-agent.apk` on the owner's computer, outside the repo.

## What was built on this branch (slices 1 to 6 of the personal agent design)

Slice 1: the phone answers time, date, battery, network, storage, alarm and sums by code (`DeviceQuestions`), and every chat message starts with a `Now:` line. Slices 2 to 6: persona chat prompt `chat-v2`, confirmed facts (`RememberFact`, `ForgetFact`, `FactRules`, `MemoryBlock`), the What Bheem knows screen (`MemoryScreen`), overnight guesses (`Extraction`, `ConsolidationJob`, never used until the owner confirms), chat offers (`OfferCheck`), phone actions (torch, timer, open app, volume in `DeviceActions`). The six new action types are code-only and documented in `docs/SECURITY.md` section 6g.

## What is verified, and what is not

Verified in the sandbox on e16fdf4 (measured): all unit tests (125 classes), `:eval:gate`, both CI checks, debug build, `:eval:conversations` routing numbers (development 24 of 31 scriptable turns, held-out 7 of 9, aggregate only).

**Not verified, and this is the important part:**

1. **The owner's first look at the phone shows the product is still generic.** Screenshot of the chat after install: "what is my name" answered "Your name is B H E E M (note, 3 Nov)" and "My name is Beam. (note, 3 Nov)" from two old notes, in the "Here is what I found" format, and "who are you" returned the same fixed paragraph as the old build ("I am Bheem, your assistant on this phone... Try: remind me tomorrow at 9 to call Arun"). Neither reached the language model, so neither used the persona prompt or the facts block.
2. Likely cause (inferred from the answer formats, not yet traced in code): fixed routes sit in front of chat. `AppController.kt` line 236 and 415 try `SmallTalk.reply(...)` first (`agent-core/.../assistant/SmallTalk.kt`), then `DeviceQuestions.answer`; records questions go through the pipeline's question path to `graph.recall.answer` (line 401), which searches old notes. Only messages that fall through reach `graph.chat.replyWithOffer(..., MemoryBlock.build(facts, text))` (line 261). So persona and facts only apply to what the fixed routes do not catch. My routing baseline already showed memory recall at 2 of 6 and I reported it as a number instead of fixing it before handing over.
3. Not run on the phone: remember and forget cards, the What Bheem knows screen, timer and open-app hand-offs, the overnight job, the offer line, reply warmth and speed. Earlier latency numbers (first text 1.1 to 1.5 s, answer 3.2 to 4.4 s warm) were taken with the old shorter prompt and do not apply to this build. No model replies were scored (the model-reply half of the baseline was never run). No Opus review of slices 2 to 6.
4. The two rule-narrowing test groups (`FactsTest`, `DeviceActionsTest` additions in a1ca65a) were written after the code; I did not watch them fail first.

## Suggested next steps (smallest first)

1. Trace "what is my name" and "who are you" through `AppController` and the pipeline to confirm the cause. Write failing tests first (the conversation runner in `eval/.../conversation/ConversationRunner.kt` mirrors the routing order by hand and must be updated with any routing change; moving routing into one shared `agent-core` function would remove that duplication).
2. Let identity and "about me" questions ("what is my name", "where do I live", "who are you") check the owner's confirmed facts first, then go to the persona chat with the memory block, instead of the fixed paragraph or old notes. Keep the rule that guesses are never used until confirmed.
3. Install on the phone and run a short real conversation (owner holds the phone) before claiming anything. Take the model-reply half of the baseline at that point.
4. Then Opus review, then merge decisions with the owner (nothing is merged).

## Rules the owner set (do not break these)

- **Builds and tests run only in the owner's instabox sandbox machine "bheem"** (never "thecolony", never the owner's computer, unless the owner explicitly asks). The local machine is only for chat, small edits, git and adb to the phone. Details and the dashboard driving notes are in the agent memory file `bheem-sandbox-instabox.md`; short version: start long jobs detached (`(nohup ~/ci.sh tests > /dev/null 2>&1 &)`) and poll `~/log-tests.txt`, because a long foreground command froze the web terminal for 40 minutes; one Gradle process at a time; warm runs are about 20 s for tests, 1 min for the gate, a few minutes for the build; Restart is in the machine "More actions" menu; check the header says "bheem" and the prompt says `user@instabox-bheem` before typing. The sandbox clone is read only (cannot push); push from the owner's computer, then `git pull --ff-only` in the sandbox.
- Getting an APK out: copy to `~/out`, serve it on port 8080, publish the port in the dashboard (**ask the owner for a fresh explicit yes every time**), `curl` it, check the sha256, unpublish, stop the server, then ask before `adb install -r`.
- The 30 held-out assistant cases and the 10 held-out conversations are for measuring only. Never open, grep, print or quote them (they are in `.ignore` and `.rgignore`). If a held-out number fails, change code from the development set and re-measure.
- Gate and validator changes are test-first. A new action type needs `docs/SECURITY.md` and gate tests in the same change. Mark every number as measured or target. Never use real notifications, numbers or VPAs in tests or logs.
- Chat style: never use the em dash character in messages to the owner.
- Fable advises, Opus reviews each stage, Sonnet implements (owner's workflow). The owner is out of patience for ceremony: do the work, show the screenshot or the number, and say plainly what you did not check.
