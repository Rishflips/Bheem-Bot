# Research notes

What was checked, where, and what is still unverified. Research date: 23 October 2026. Re-verify anything that matters before relying on it; this space moves fast.

## Verified from primary pages

| Topic | Finding | Source |
| :--- | :--- | :--- |
| Gemma 4 on Android | E2B and E4B run in LiteRT-LM; Apache-2.0; E2B about 52 tok/s and 0.3 s time to first token on a Galaxy S26 Ultra GPU (flagship only) | [Google LiteRT-LM](https://developers.google.com/edge/litert-lm/models/gemma-4) |
| AppFunctions | Experimental; needs Android 16; Gemini integration in private preview as of May 2026; no public path for third-party agents to get the caller permission | [Android docs](https://developer.android.com/ai/appfunctions) |
| Android 16 background work | Jobs launched from foreground services follow runtime quotas | [Android docs](https://developer.android.com/develop/background-work/services/fgs/changes) |
| Qwen3.5 small models | Apache-2.0; 201 languages claimed; tool calling; BFCL-V4 43.6 (2B, thinking) and 50.3 (4B); Hindi not named on the card | [Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B), [Qwen3.5-4B](https://huggingface.co/Qwen/Qwen3.5-4B) |
| Qwen3.5 on Android runtimes | LiteRT-LM conversions exist (4B int8 about 4.1 GB); Qualcomm lists 2B at about 31–40 tok/s on a flagship Snapdragon | [litert-community](https://huggingface.co/litert-community/Qwen3.5-4B), [Qualcomm](https://huggingface.co/qualcomm/Qwen3.5-2B) |
| SmolLM3-3B | Apache-2.0; six European languages, no Hindi | [Model card](https://huggingface.co/HuggingFaceTB/SmolLM3-3B) |
| Phi-4-mini | MIT; Hindi not listed; may hallucinate function names | [Model card](https://huggingface.co/microsoft/Phi-4-mini-instruct) |
| Sarvam-1 | Non-commercial license; base model, not instruction-tuned | [Model card](https://huggingface.co/sarvamai/sarvam-1) |
| Sarvam Edge | ASR 74M params (10 Indic languages), TTS 24M, translation 150M; no stated weights license | [Sarvam](https://www.sarvam.ai/blogs/sarvam-edge/) |
| Play SMS policy | SMS and Call Log permissions are restricted to qualifying core-function apps | [Google Play help](https://support.google.com/googleplay/android-developer/answer/9047303) |
| DPDP Rules | Notified November 2025; core duties apply about 18 months later (around May 2027) | [Taxmann summary](https://www.taxmann.com/post/blog/analysis-indias-dpdp-act-and-rules/) |
| Existing open-source reference | Cashiro parses UPI and bank SMS with regex patterns and offers optional AI | [F-Droid](https://f-droid.org/packages/com.ritesh.cashiro/) |

## Not verified

- Hindi and Hinglish quality of any candidate model or speech stack. Phase 0 measures it.
- Whether Sarvam Edge weights can be used in an open-source app.
- The upstream license of AI4Bharat IndicConformer ONNX conversions (sources conflict between MIT and Apache-2.0).
- LFM2.5 license terms (sources conflict).
- Current Google Play policy text for notification-listener access.
- How a non-bank app can take part in NPCI UPI Circle. No public route for independent apps was found; Bheem does not depend on it.
- Reddit and user-sentiment research. Searches returned no relevant threads; user need rests on app listings and the PRD-style reasoning in this repository.
- Several claims in the original generated reference documents (specific latency, battery, and accuracy targets; a "94% local" ratio; named incidents at other companies). They were dropped.

## Reference project: OpenMausBot

[`milind-soni/OpenMausBot`](https://github.com/milind-soni/OpenMausBot) (Apache-2.0) is an open-source desktop app, in TypeScript and Electron, that runs a team of AI bots in a chat interface by orchestrating cloud and command-line agents. It is a useful reference for product shape, not a code base to reuse: it is not native Android, not on-device, and depends on cloud computers and third-party services.

**Ideas Bheem adopts (re-implemented, no code copied):**
- Approval cards: risky actions surface as inline cards the user allows or denies.
- Triggers and routines: schedule or event-based automations, created by talking, shown as a card with Edit, Pause, and Undo, and listed in one screen.
- Portable packs: a Markdown file with structured frontmatter that others can share; connections stay off, routines arrive paused, and packs never carry credentials or history.
- Golden-set evaluations: replayable scenarios with baselines that gate changes.
- Write-only secrets: the UI sees "configured", never the value.
- Verify on fixtures, never on the user's live data.
- Short comment policy: comment only what code cannot say.

**What Bheem does differently:** fully on-device, a closed catalog of typed actions instead of general agents, no cloud computers, no third-party connectors in the core, a deterministic gate in front of every action, and a native Android app.

## Other reference: PocketLLM

[`PocketLLM/PocketLLM`](https://github.com/PocketLLM/PocketLLM) (checked 23 October 2026) is a Flutter chat client with a FastAPI and Supabase backend that aggregates cloud providers (OpenAI, Groq, OpenRouter) using each user's own API keys. Its README no longer describes on-device inference, so it is not a model for Bheem's local runtime. It has no LICENSE file, so by default no one may copy its code; use it only as a UI idea source (a model library with clear "no key configured" status messages).

## More references (checked 24 October 2026, from each project's own page)

Bheem is the harness; models are swappable. These projects were read for design ideas only. No code or data is copied. Where a license is shown, it is as stated on the page.

| Project | License (as shown) | What it shows | What Bheem takes (re-implemented) |
| :--- | :--- | :--- | :--- |
| [Goose](https://github.com/block/goose) | Apache-2.0 | A local agent with 15+ model providers, tools added through MCP, saved workflow "recipes" | A provider-neutral model layer (already planned); recipes map to routines and skill packs. Not taken: open-ended tool servers in the core, because the catalog stays closed |
| [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) | Apache-2.0 | On-device skills loaded from a URL, a 270M function-calling model for device actions, a model allowlist with custom models, per-device benchmarks, a prompt test bench | A small function-calling router for the fast common commands (to test, not adopted yet); an allowlist plus bring-your-own model; a built-in benchmark that picks the model for the phone; a debug screen for prompts |
| [OpenClaw](https://github.com/openclaw/openclaw) and its [skills docs](https://docs.openclaw.ai/tools/skills) | MIT | A gateway that owns sessions and tools; policy enforced by code, inbound messages untrusted, unknown senders paired; skills as a folder with a `SKILL.md` (YAML frontmatter and Markdown); skills filtered by requirements and a fixed precedence order; third-party skills treated as untrusted with an install policy hook | Sender trust for notifications (pairing for unknown senders); skill packs that declare requirements (Android version, permissions, packages) and are filtered at load; a fixed precedence order; an install review step. Not verified: its memory and cron design (page not read in full) |
| [Home Assistant intents](https://github.com/home-assistant/intents) | CC-BY-4.0 (data) | Sentence templates in data files apart from the matcher, test sentences beside each intent, validation in CI, tools to sample and count template expansions | Tier 1 of the architecture as data: common commands matched by templates with tests, before any model call. Hindi and Hinglish arrive later as data files. Their sentence data is not copied (we write our own) |
| [Android AppFunctions](https://developer.android.com/ai/appfunctions) | Platform API | Apps expose functions to agents; Android 16 or higher; experimental preview; callers need `EXECUTE_APP_FUNCTIONS`; the page says only limited apps and system agents have full access for now, and does not say how a third-party assistant gets the permission | Do not depend on it. Put executors behind one interface so AppFunctions can become an executor later |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 (per a package listing) | One runtime for several speech models on Android, with Moonshine and Whisper tiny.en shown in its examples | A speech interface in the harness so engines swap. Worth testing as an alternative runtime. Not verified: the licenses of the model weights |
| OpenDroid and similar forks | not checked | Autonomous agents that drive other apps' screens through accessibility services | Not adopted: driving other apps' screens is a non-goal in [PRODUCT.md](PRODUCT.md) |

**Principles this supports**
1. Every model, speech engine and executor sits behind an interface, so a better model is a configuration change, not a rewrite.
2. Policy lives in code and tests, not in prompts (gate, sender trust, risk levels).
3. Common commands go through data-driven templates first, with test sentences next to them; the model handles what templates miss.
4. Skills are data files with declared requirements, loaded by a fixed rule, reviewed on import, and never able to add action types.
5. A built-in benchmark and debug screen make a model swap a measured decision on the user's own phone.

**Licensing and names (OpenMausBot):** Apache-2.0 allows reuse with notice. If any file or substantial text from that project is ever copied, add it to a `NOTICE` file with attribution and mark changes. The OpenMausBot and MausBot names and mascot belong to their owner and are not used here.
