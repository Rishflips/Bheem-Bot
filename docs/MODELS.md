# Models

Models change monthly, so Bheem treats them as replaceable parts. The kernel depends on four small interfaces. Everything else in this document can change without touching skills.

## Model layer

```kotlin
// Illustrative. Final signatures are set in the implementation plan.
interface LlmEngine {
    suspend fun load(spec: ModelSpec)
    suspend fun unload()
    fun generate(request: GenerateRequest): Flow<String>
    suspend fun generateJson(request: GenerateRequest, schema: JsonSchema): JsonResult  // validated
}
interface SpeechToText { suspend fun transcribe(pcm: FloatArray, lang: Lang?): Transcript }
interface TextToSpeech  { fun speak(text: String, lang: Lang): Flow<AudioChunk> }
interface Embedder      { suspend fun embed(texts: List<String>): List<FloatArray> }
```

- **Runtimes are adapters.** LiteRT-LM first, llama.cpp second. A model is runnable if some adapter supports its file format.
- **`generateJson` owns validation.** It uses constrained decoding if the runtime supports it, otherwise validates and retries once. Callers never see invalid JSON.
- **Load and unload are explicit.** The kernel loads on demand and unloads after an idle timeout ([ARCHITECTURE.md](ARCHITECTURE.md)).
- **Phase 0 implements a subset.** Only `LlmEngine.load`, `complete`, and `unload` exist so far. `generateJson` and the other interfaces are added in Phase 1 when a feature needs them.

## Model registry

A JSON file in the app lists every model Bheem knows about. The downloader and the tier selector read only this file.

```json
{
  "id": "qwen3.5-2b-int8-litertlm",
  "role": "llm",
  "runtime": "litert-lm",
  "files": [{ "name": "model.litertlm", "sha256": "…", "bytes": 0 }],
  "source": "https://huggingface.co/litert-community/Qwen3.5-2B",
  "license": "Apache-2.0",
  "minRamGb": 6,
  "capabilities": ["tools", "json", "multilingual"],
  "defaultMode": "non-thinking"
}
```

Downloaded files are verified against the pinned `sha256` before use (see [SECURITY.md](SECURITY.md)).

## Device tiers

| Device RAM | Mode | Models |
| :--- | :--- | :--- |
| Under 6 GB | Rules-only plus speech | No LLM. Parsers, reminders, and voice if a speech model fits. |
| 6–8 GB | Standard | Primary LLM at the 2B size |
| 12 GB and up | Enhanced (optional) | Larger LLM at the 4B size, user-selectable |

These are starting points. Phase 0 sets real thresholds from measured memory and latency on mid-range phones.

## Candidates

All benchmark figures below come from model cards or vendor pages, not from our own tests. None of them measures Hindi or Hinglish on our tasks.

### LLM

| Model | License | Notes | Open questions |
| :--- | :--- | :--- | :--- |
| **Qwen3.5-2B** (blocked by the state leak, see "Measured so far") | Apache-2.0 | Claims 201 languages; tool calling; non-thinking by default; BFCL-V4 43.6 in thinking mode; LiteRT-LM build exists | Hindi and Hinglish quality unmeasured; peak RAM on mid-range phones |
| **Qwen3.5-4B** | Apache-2.0 | BFCL-V4 50.3; MMMLU 76.1; LiteRT-LM int8 file about 4.1 GB | Size is heavy; no phone speed figure found |
| **Qwen3.5-0.8B** | Apache-2.0 | Fast on flagships | Reported weak tool calling and thinking loops; may need fine-tuning to be useful as a router |
| **LFM2.5** (230M, 1.2B, 2.6B) | Liquid custom license (sources conflict) | Tool-use oriented; LiteRT-LM builds exist | License terms; Indic coverage unconfirmed |
| **Gemma 4 E2B** (working choice for v0.01, pending the Phase 0 decision) | Apache-2.0 (HF model page of the LiteRT build, checked 2026-10-23) | Real, runs in LiteRT-LM, 2.59 GB file; stable on one engine; see "Measured so far" | Hindi and Hinglish unmeasured and no Indic language named in Google's model card; first token still 3–4 s |
| SmolLM3-3B, Phi-4-mini, Granite 4 Nano | Apache-2.0 / MIT / see card | Strong English | Hindi not listed. Excluded from the India path. |
| Sarvam-1 | Non-commercial | Indic base model | Not instruction-tuned and not commercially usable. Excluded. |

### Speech to text (Hindi, English, Hinglish)

| Candidate | Notes | Open questions |
| :--- | :--- | :--- |
| **Android on-device recognizer** | Zero download, baseline | Offline pack availability and Hinglish quality |
| **Whisper-small Hindi (IIT Madras fine-tune)** | Apache-2.0; about 181 MB ggml or 358 MB sherpa-onnx int8; reported WER 25–33% on different sets | Hinglish; latency on mid-range CPUs |
| **AI4Bharat IndicConformer** | Per-language ONNX packs around 131 MB; 22-language model about 622 MB | Licence label differs across conversions; verify upstream |
| **Sarvam Edge ASR** | 74M parameters, 10 Indic languages, strong vendor figures | No public statement on weights or licensing for third parties |

### Text to speech

| Candidate | Notes |
| :--- | :--- |
| **Android system TTS** | Baseline |
| **Kokoro-82M** | Hindi voices exist but are rated C; quality is thin |
| **IndicTTS (IIT Madras)** | MIT; 13 languages; local |

### Embeddings (later, only if retrieval needs it)

| Candidate | Notes |
| :--- | :--- |
| **multilingual-e5-small** | 384-d, about 100 languages, lightest. License to verify. |
| **Qwen3-Embedding-0.6B** | About 1.1 GB at int8: too heavy for v1 |

## Measured so far (Phase 0, on a phone)

Device: Moto g86 power 5G (MediaTek MT6878, 7.4 GB RAM, Android 16), LiteRT-LM 0.18.1, CPU backend. Raw evidence is in [`eval/diagnostics/`](../eval/diagnostics/README.md). This is the first real data; it is not yet a benchmark result.

| Finding | Detail |
| :--- | :--- |
| **Qwen3.5 is unusable on one long-lived LiteRT-LM engine** | Hybrid layout (18 Gated DeltaNet layers + 6 attention layers). Sending the same command repeatedly changes the answer from the second request (one field), gives a different action from the seventh, and loops or echoes the input by the eleventh; a fresh engine per request or a plain-attention model (Qwen3-1.7B) does not drift. Thinking mode, benchmark flag, cache directory, penalties, speculative decoding, startup prefill, and the Session API did not change it. Leading explanation: the recurrent state is not reset between conversations. Already reported upstream as [LiteRT-LM#3165](https://github.com/google-ai-edge/LiteRT-LM/issues/3165), open on 2026-10-23, with Gemma 4 E2B and Qwen3-0.6B listed there as controls that stayed correct. |
| Workaround cost | A fresh engine per request fixes it but costs a load of about 36–63 s per request for Qwen3.5-2B, which is too slow for a product. |
| Qwen3.5 load and memory | Load 36–63 s for 2B and about 41 s for 0.8B. Memory after load about 1.2–1.7 GB; peak RSS up to 3.8 GB during a run. |
| Speed on CPU | Qwen3.5-2B int8 about 5.4 decode tokens/s; Qwen3.5-0.8B int8 about 9.6; Qwen3-1.7B int4 only about 2.6 (slower, likely because of its int4 dynamic format). |
| First-token wait | About 5–9 s for every model tried. The system prompt plus input is about 480 tokens and prefill dominates. A shorter prompt or reusable prompt cache is needed. |
| GPU backend | Failed to load Qwen3.5-0.8B on this chip: "Failed to modify graph with delegate". |
| Output quality, Qwen3.5 | Not measurable: the 120-case Qwen3.5-2B sample was invalid because of the drift bug above. |
| **Gemma 4 E2B, English** | One run, prompt `proposer-v2.1`, CPU, 341 synthetic templated English cases (101 commands, 200 notifications, 40 injection attempts). Commands: valid JSON 98.0%, right action 97.0%, right details 97.0%. Notifications: right action 100%, right details 98.5%. Injection attempts: 0 of 40 produced a payment or message draft (all 40 did nothing). Median first token 3.0 s (commands) and 3.9 s (notifications), 11.2 decode tokens/s, peak memory 1.54 GB, stable over 33 minutes (battery 84% to 81%). Hindi and Hinglish were not run (English first). |
| Gemma 4 E2B, load and warm-up | First install: load 22.8 s and the first four requests took 18–21 s to the first token. With the cache warm: load about 1.2 s. |
| Prompt v1 to v2 (Gemma, 120-case English sample) | Giving untrusted text only three actions (log expense, create reminder, do nothing), a shorter prompt and one instruction-style example took notification details from 72.5% to 100% and obeyed injections from 37 of 40 to 0 of 40, and cut the prompt from about 480 to 380 tokens. The three changes were made together, so the share of each is not known. |
| Gemma 4 E2B, GPU backend | Loads and runs on this chip (the Qwen3.5-0.8B GPU load failed). 120 English cases, prompt v2.1: first token median 1.4 s for commands and 1.8 s for notifications, against 3.0 s and 3.9 s on CPU; decode 9.1 tokens/s (CPU 11.2); peak memory 2.16 GB (CPU 1.54 GB); load about 40 s on every process start (CPU about 1 s with a warm cache). Accuracy matched CPU within one case, with two GPU-only glitches in 120 cases: a reminder text of "%" and a scam message logged as a credit. |
| First-token time moves in 128-token steps | Measured on 341 CPU cases and 120 GPU cases: the wait grows in steps per 128 prompt tokens, about 0.93 s per step on CPU and 0.41 s on GPU, plus about 0.2 s. CPU: 209–255 prompt tokens took 2.06 s, 259–269 took 3.0 s, 357–369 took 3.0 s, 397–457 took 3.9 s. Trimming a prompt only helps when it crosses a 128-token boundary. The step size is inferred from this fit, not from LiteRT-LM documentation. |
| Examples in the prompt (Gemma, CPU, 120 English cases) | Zero-shot (rules only) cut the first token to 2.1 s, but the untrusted-text prompt then returned a different JSON shape for every notification, because it never states the shape; commands were 92.5% right. One-shot (stopped at 100 of 120 cases): commands 97.5%, notification details 90%, 0 of 20 injection attempts obeyed even without the injection example. |
| Compact prompts (v3, rules only, both under 256 tokens) | First token reached 2.05 s on CPU and 1.08–1.11 s on GPU (full prompt: 3.0–3.9 s and 1.4–1.8 s), as the 128-token-step fit predicted. Accuracy fell: commands 87.5% on CPU and 82.5% on GPU (meetings became tasks), bill reminders 0 of 8 on both (the model invented key names such as `bill` and `due_date`), and one alert copied the example amount from the prompt. Answers got longer because the model wrapped them in code fences (median 55 output tokens for notifications, was 31). No payment or message draft in any run; GPU 40 of 40 injection attempts did nothing, CPU 18 of 20 did nothing and 2 were logged as expenses. The CPU run stopped at 100 of 120 cases (the app was removed from Recents). |
| One-line answers vs JSON (v3.1, Gemma, 120 English cases) | Code fences are gone (0 of 120). One-line answers (`Action|arg|arg`, turned into the typed action by code) cut the answer from about 30 to about 20 tokens: total time for commands / notifications was 3.7 s / 4.3 s on CPU and 2.8 s / 3.3 s on GPU, against 5.4 s / 6.6 s and 4.4 s / 5.1 s with the full JSON prompt (about 35% less). First token 2.2 s on CPU, 1.0 s on GPU. The right action was chosen for 100% of commands, but details were only 70% (CPU) and 75% (GPU) for commands and 27% and 40% for notifications. All 34 GPU misses (and 34 of 41 on CPU) were prompt-format artifacts: the model copied the ` - description` suffix into the last value or wrote a placeholder word such as `text` as the value. The compact JSON v3.1 kept notifications at 100% on GPU but produced 4 malformed reminders and 5 of 40 injection attempts came back as payment actions (v3 with similar wording gave 0), so prompt wording alone is not a reliable defense. |
| Total wait is mostly the answer | Median time for a full answer (first token plus the JSON) with the full prompt: commands 5.4 s and notifications 6.6 s on CPU, 4.4 s and 5.1 s on GPU. About 30 output tokens at 9–11 tokens/s is around 3 s of that. Shorter prompts cut only the first token; with v3 the total did not improve because the answers got longer (GPU notifications 7.3 s). |

## Phase 0 evaluation protocol

Goal: choose the primary model and thresholds from evidence, on the phones users actually own.

**Test sets (in `eval/`)**
- About 300 Hinglish and Hindi commands with the expected typed action and arguments (reminders, expenses, splits, queries).
- About 200 anonymised notification texts from varied banks and UPI apps with expected parse results.
- 50 adversarial texts (prompt-injection style) that must never produce an L1/L2 action without a tap.

**Devices**
- One flagship, one mid-range (₹15,000–₹25,000, 6–8 GB), one low-end (4 GB).

**Metrics per model and device**
- JSON validity rate and schema-valid action accuracy.
- Time to first token and tokens per second at a 512-token prompt.
- Peak resident memory, cold load time, and thermal behaviour over 20 consecutive runs.
- Battery cost of a representative day of events.
- Speech: word error rate on a recorded Hinglish set, latency, and size.

**Decision rule**
- Primary model must clear accuracy thresholds set at the start of Phase 0 on the mid-range phone. If Qwen3.5-2B fails, the next candidate by results moves up. No model is chosen from benchmark tables alone.

## Rules for swapping a model

1. Add it to the registry with its license and hashes.
2. Run the eval harness on all three devices.
3. It must meet or beat the current primary on every threshold, or have a documented reason.
4. License is compatible with Apache-2.0 distribution of the app (weights are downloaded, not bundled, and keep their own license).
