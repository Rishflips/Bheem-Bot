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

**Licensing and names:** Apache-2.0 allows reuse with notice. If any file or substantial text from that project is ever copied, add it to a `NOTICE` file with attribution and mark changes. The OpenMausBot and MausBot names and mascot belong to their owner and are not used here.
