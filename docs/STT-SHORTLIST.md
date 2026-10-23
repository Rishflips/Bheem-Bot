# English speech-to-text shortlist for v0.01 (DRAFT, research only, not committed)

Date: 2026-10-24. Nothing here was run on a phone. Every number is labelled **published** (from a model card, paper or vendor page; not our measurement), **estimate** (arithmetic from published figures) or **proposed** (a limit I suggest). No number in this note is measured by us.

## What the product needs

- Hold-to-talk voice commands in English, usually a few seconds to ten seconds, handed to the planner (Gemma 4 E2B, one-line prompt). Audio stays in RAM ([ARCHITECTURE.md](ARCHITECTURE.md)).
- India-first: the speakers are Indian-English speakers on a mid-range phone microphone. Amounts, names and times are the words that must be right ("pay Ravi 840 rupees").
- Licence that can be downloaded and used in an Apache-2.0 app (weights are downloaded, not bundled, and keep their own licence).
- Budget on the test phone (Moto g86 power 5G, 7.4 GB): the planner already peaks at 2040 MB on GPU and 1550 MB on CPU (measured, [PHASE0-RESULTS.md](PHASE0-RESULTS.md)) against a 3000 MB limit, so a dedicated speech model should add well under 1 GB; I propose 500 MB or less.

## Shortlist (ranked by what I would measure first)

| # | Candidate | Licence | Size | Accuracy (published) | Latency (published, not on this phone) | Android path |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | **Gemma 4 E2B native audio** (LiteRT-LM) | Apache-2.0 | No extra download: about 300M-parameter audio encoder inside the 2.59 GB bundle we already use | Model card lists speech recognition and speech-to-translated-text, audio up to 30 s; "FLEURS 0.09 (lower is better)" for E2B, metric not named | None found | `Content.AudioBytes` and `EngineConfig.audioBackend` in the Kotlin API |
| 2 | **Moonshine v2 streaming, Small (123M)**; Tiny (34M) as the fast fallback | MIT (repo says models are MIT by default, English included) | Small about 134 MB (encoder 29.9 + decoder 104.0 + tokenizer 0.2), Tiny size not stated | Open ASR average WER 7.84% (Small), 12.01% (Tiny), 6.65% (Medium); LibriSpeech clean 2.49 / 4.49 | Apple M3, end of speech to transcript: Tiny 50 ms, Small 148 ms. Raspberry Pi 5: Tiny 237 ms, Small 527 ms (third-party mirror of project docs) | Official Android library `ai.moonshine` via Gradle, min SDK 21 (third-party mirror of the docs) |
| 3 | **Parakeet-TDT-CTC-110M**, int8, through sherpa-onnx | CC-BY-4.0 (attribution) | About 114M parameters; file size not found (about 110 MB to 130 MB is my **estimate** for int8) | Open ASR average WER 7.49%; LibriSpeech 2.4 / 5.2 (card says "Version 2.0"); punctuation and capitalisation included | No phone figure found (A100 RTFx about 5300 only) | sherpa-onnx lists it as `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8`; one Android runtime for many models |
| 4 | **Whisper base.en**, q5_0, through whisper.cpp (and **small.en** as the accuracy ceiling) | MIT (code and weights) | base: 142 MiB disk, about 388 MB memory; small: 466 MiB, about 852 MB (whisper.cpp README, multilingual rows) | base.en 74M parameters. On Indian-accented English (Svarah) Whisper base scored 13.6% WER against 4.2% on LibriSpeech; Whisper large 7.2% | One anecdotal blog: base.en Q5_K on a Pixel 6a about 0.6 real-time factor on CPU, about 0.25 with Vulkan (method not documented) | whisper.cpp has an Android example app (`examples/whisper.android`) |
| 5 | **Android on-device SpeechRecognizer** (baseline, not a shipped model) | Platform API, not open source | Language pack installed by the system | None for our use | None | `createOnDeviceSpeechRecognizer`, `isOnDeviceRecognitionAvailable`, `checkRecognitionSupport`, `triggerModelDownload` |

Kept in reserve: **sherpa-onnx streaming Zipformer English** (`sherpa-onnx-streaming-zipformer-en-2023-06-26`, Apache-2.0 on its model card). It is the only candidate here with documented hotword biasing (transducer models only, `modified_beam_search`), which helps names and merchants, but I found no WER or size figure for it and expect it to be weaker on accents (that expectation is not measured).

Excluded and why:

- **Parakeet-TDT 0.6B v2** (CC-BY-4.0, Open ASR average 6.05%): 600M parameters and the card asks for at least 2 GB RAM to load, too much next to the planner.
- **Moonshine Medium (245M, about 270 MB)** and **Whisper medium and large**: documented as desktop-class.
- **Vosk** small English (Apache-2.0, 40 MB, LibriSpeech WER 9.85%): its Indian-English small model scored 49.05% WER on NPTEL, so it is a floor, not a candidate.
- **NVIDIA parakeet realtime EOU 120M** (NVIDIA Open Model License, streaming Open ASR average 9.30%): custom licence and the card lists only NVIDIA GPUs.
- Two-billion-parameter leaders on the Open ASR leaderboard (about 5.3 to 5.4% average, from a third-party roundup): too large for this phone.

## Notes on each candidate

1. **Gemma 4 E2B audio.** Verified in our own file: the local `gemma-4-E2B-it.litertlm` header contains `tf_lite_audio_encoder_hw`, `tf_lite_audio_adapter`, `tf_lite_end_of_audio` and an `<|audio|>` chat-template slot, so the bundle we already ship has the audio parts (this shows they are present, not that they work on this phone). The LiteRT-LM Kotlin guide shows `Content.AudioBytes(audioBytes)` and an `audioBackend` setting (CPU, with GPU or NPU named as options), using a Gemma 3n example; it does not state the audio format, sample rate or duration, and gives no transcription example. The community model card says vision and audio parts are "loaded on demand", and its example recommends a 16 kHz mono WAV. Upside: no second model to download, load or keep updated, and it opens a one-call path from audio to action. Risks: a generative model can paraphrase or hallucinate; the planner and the speech step would share one engine; no accent or latency data exists.
2. **Moonshine v2.** Streaming gives partial text while the user speaks and the smallest finished-text delay on a laptop. The model card warns of repeated or hallucinated phrases on short or noisy audio, says it is English only, and does not address numbers or proper nouns. Sizes and the Android setup come from a third-party mirror of the project docs, so verify them in the repository before building. The project's own English Tiny (26M, non-streaming) and Base (58M, 10.07%) models also exist.
3. **Parakeet-TDT-CTC-110M.** Its published Open ASR average (7.49%) is close to Moonshine Small's (7.84%) at a similar size, and it adds punctuation and capitalisation; the two cards may not use the same dataset mix. It is an offline (non-streaming) model, sherpa-onnx lists it as a CTC export, so hotword biasing (transducer models only) does not apply. No phone latency figure was found, and CC-BY-4.0 means carrying an attribution notice in the app.
4. **Whisper.** The most mature ecosystem and an MIT licence, but it is not streaming: it works on fixed 30-second windows, so a short command still pays most of the window cost unless the runtime shortens it (not verified). The Svarah result is the warning for India-first use: base is three times worse on Indian-accented speech than on LibriSpeech.
5. **Android recognizer.** Useful as a baseline and a runtime fallback. The class documentation warns the default implementation may stream audio to remote servers, so only the on-device factory is acceptable. `checkRecognitionSupport` and `triggerModelDownload` are marked API 33 in the platform diff; I could not confirm the level for `createOnDeviceSpeechRecognizer` (it is not in the API 33 diff, so it is older). Biasing strings (`EXTRA_BIASING_STRINGS`) were not confirmed. It cannot be pinned to a version or tested in CI like a model file, which is why it is a baseline only.

## What is not known

- No source gives a latency or memory figure on a MediaTek MT6878-class phone for any candidate. The figures above are laptop, Raspberry Pi 5 or other-phone proxies.
- All published WER figures are on read or meeting English from the Open ASR mix, not on short commands, Indian accents, or phone microphones, except the Svarah Whisper numbers.
- Whether the audio path in LiteRT-LM works on this phone's CPU backend with our file.

## How to measure word error rate (proposal)

**Test sets**

1. **Bheem command set (primary).** The 101 English command texts in `eval/data/commands.jsonl`, read aloud. These are synthetic, so no real person's data is involved. At least 6 volunteer speakers with Indian-English accents, balanced by gender and age, each reading a subset so that every command is read by at least 2 speakers (about 300 utterances, **estimate**). Record on the test phone's own microphone as 16 kHz mono PCM WAV, in three conditions: quiet room, background noise (traffic or kitchen), and a speaker one metre away. Written consent from each speaker; audio stays in git-ignored local storage and is never committed; delete after the study.
2. **Svarah (secondary reference).** AI4Bharat and IIT Madras, 9.6 hours, 117 speakers from 19 states, CC BY 4.0, gated on Hugging Face (needs login and acceptance). Use the read subset (about 1.4 h) and the "use cases" subset (about 1.7 h), which includes digital payments, quantities and bank names; the paper names numbers and entities as a main error source. It gives a comparison against published Whisper results on the same data.
3. **LibriSpeech test-clean subset (sanity).** About 200 utterances, only to confirm our resampling and decoding reproduce the published numbers before trusting anything else.
4. **Silence and noise clips (hallucination check).** 20 clips with no speech; count non-empty outputs.

**Metrics**

- **WER** with one fixed normalizer written and tested in `eval/` (lowercase, no punctuation, one convention for digits versus number words, "Rs", "rupees" and the rupee sign equal). Also report the Whisper English normalizer's result for comparison with published numbers.
- **Entity error rate.** From each command's expected action, score the amount, name (payee, merchant, recipient) and time slots after normalization. This is the number that predicts wrong cards.
- **End-to-end accuracy.** Transcript to the frozen planner prompt to the typed action, scored with the existing harness against the same expected actions. Compare with the typed result (98 to 99% action, measured) to get the voice penalty. This is the gate, because a WER mistake on "umm" costs nothing.
- **Latency.** From button release to final text (p50 and p95, by utterance length), time to first partial for streaming models, real-time factor, cold load, peak memory, with the planner resident and idle and also with it loaded on GPU. Three warm-up utterances discarded, order randomized, same thermal state noted, same rules as the LLM matrix.
- **Uncertainty.** Report speaker-level bootstrap confidence intervals and compare candidates with a paired bootstrap over utterances. With roughly 2,000 words the plain binomial interval on a 10% WER is about plus or minus 1.3 points (**estimate**), but words from one speaker are correlated, so expect plus or minus 2 to 3 points; entity error on about 500 entities at 5% is about plus or minus 2 points (**estimate**).

**Protocol**

- Replay the same WAV files through every candidate with a file-input mode in the bench app (deterministic), plus a live-microphone pass of about 30 utterances to test the real capture path. Greedy or default decoding, threads fixed, one candidate per run, phone cooled and at 50 to 90% battery between runs.
- A second pass with biasing where the candidate supports it (a list of the command set's names and merchants): Android biasing strings, sherpa-onnx hotwords for transducer models, a prompt prefix for Whisper and for Gemma. Report the entity error change.
- Freeze the limits before the first run and commit them, as we should have done for the LLM (we changed one after seeing results).

**Proposed limits (to freeze before running; all are proposals)**

| Check | Proposed limit |
| :-- | :-- |
| End-to-end command action on spoken commands | >= 90% (same as typed) |
| End-to-end command details | >= 85% (same as typed) |
| Entity error rate | <= 8% |
| WER on the Bheem command set | <= 15% (a sanity limit; the gate is end-to-end) |
| Time from release to final text, p95, utterances up to 8 s | <= 1500 ms |
| Peak memory added by the speech model | <= 500 MB |
| Download size | <= 300 MB (Gemma audio: 0 extra) |
| Hallucinated output on no-speech clips | <= 5% |
| Licence | MIT, Apache-2.0, BSD, or CC-BY with attribution |

## What I can do next without the phone

1. Write the normalizer, WER and entity scorer in `eval/`, test-first, with fixture transcripts.
2. Generate the recording sheets from `commands.jsonl` (balanced speaker assignment, condition order) and a consent and handling note.
3. Draft the limits above as a file for you to review and freeze.

After the recordings exist: add file-input audio mode to the bench app and integrate the candidates (LiteRT-LM audio, sherpa-onnx for Parakeet and the Zipformer, Moonshine's library, whisper.cpp, the platform recognizer).

## Sources

- Gemma 4 E2B model card: <https://huggingface.co/google/gemma-4-E2B-it>; LiteRT-LM community card: <https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm>; LiteRT-LM Kotlin guide: <https://developers.google.com/edge/litert-lm/android>; Gemma 3n audio in AI Edge Gallery (30 s): <https://developers.googleblog.com/en/google-ai-edge-gallery-now-with-audio-and-on-google-play/>
- Moonshine: <https://github.com/moonshine-ai/moonshine>; v2 paper: <https://arxiv.org/html/2602.12241>; streaming tiny card: <https://huggingface.co/moonshine-ai/moonshine-streaming-tiny>; model sizes and Raspberry Pi figures (third-party mirror): <https://mintlify.wiki/moonshine-ai/moonshine/concepts/models>
- NVIDIA: <https://huggingface.co/nvidia/parakeet-tdt_ctc-110m>, <https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2>, <https://huggingface.co/nvidia/parakeet_realtime_eou_120m-v1>
- sherpa-onnx models: <https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html>; hotwords: <https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html>; Zipformer card: <https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26>
- Whisper: <https://github.com/openai/whisper>; whisper.cpp: <https://github.com/ggml-org/whisper.cpp>; Pixel 6a blog figure: <https://dev.to/software_mvp-factory/wiring-whispercpp-to-androids-audiorecord-api-hjo>
- Vosk models: <https://alphacephei.com/vosk/models>
- Svarah paper: <https://ar5iv.labs.arxiv.org/html/2305.15760>; dataset: <https://huggingface.co/datasets/ai4bharat/Svarah>
- Android SpeechRecognizer: <https://developer.android.com/reference/android/speech/SpeechRecognizer>; API 33 diff: <https://developer.android.com/sdk/api_diff/33/changes/android.speech.SpeechRecognizer>
