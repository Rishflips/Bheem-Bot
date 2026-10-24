# English speech-to-text shortlist for v0.01 (research plus first phone measurements)

Date: 2026-10-24. The shortlist and the proposal sections are research and were not run; the sections titled "measured" are phone measurements. Every number is labelled **published** (from a model card, paper or vendor page; not our measurement), **estimate** (arithmetic from published figures) or **proposed** (a limit I suggest). No number in this note is measured by us.

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

## First measurement: Gemma 4 E2B audio on the phone (synthetic speech, measured 2026-10-24)

Moto g86 power 5G (MT6878, 7.4 GB), LiteRT-LM 0.18.1, text on GPU and the audio encoder on CPU, audio placed before the text. Input: 12 clips of Windows Zira (US English) synthetic speech, 1.7 to 4.3 s each, clean. This checks the pipeline; it says nothing about Indian accents, noise, or real voices.

- **Audio forms:** the file path works, and the WAV file bytes work (the 12 transcripts are identical). Raw PCM samples fail on all 12 clips: the library's audio decoder (miniaudio) returns error -10, so it needs a container format.
- **Accuracy:** WER 3.4% (3 errors in 88 words: "pay" heard as "hey" once, "Idli" written "Idly" twice). Amount errors 0%, time errors 0%, name errors 20% (the two "Idly"). With the one-line planner on each transcript: right action 11 of 12, right details 10 of 12. The action miss is "pay Karthik 2300 rupees" heard as "Hey Karthik 2300 rupees", which became a message draft. The other details miss is "BigBasket" for "Big Basket", a spelling variant that the end-to-end scorer counts as wrong and the speech scorer does not.
- **Latency:** transcription total median 2.7 s per clip (file 2.75 s, WAV bytes 2.65 s), first chunk median 1.4 s, real-time factor 0.72. The slowest clip (3.3 s) is the first one, which includes warm-up. This excludes planning, which took about 3 s on GPU in the text benchmark.
- **Memory:** peak 2207 MB with audio and planning in one engine (the text-only engine peaked at 2040 MB). Model load 5.3 s.
- **Against the proposed, not yet frozen, limit** of 1500 ms from release to final text: not met (median 2.7 s).
- **Not tested:** real human speech, Indian accents, noise, audio placed after the text, the CPU text backend.

## Second measurement: Moonshine v2 Small on the same 12 clips (measured 2026-10-24)

Library `ai.moonshine:moonshine-voice` 0.1.5 (Maven Central, MIT), model "small streaming, English" (architecture 4, 8 files, 142.3 MB from `download.moonshine.ai/model/small-streaming-en/quantized_26_08_21`), run on CPU through the library's non-streaming call (`transcribeWithoutStreaming`) on the whole clip after it ends. Same phone, same clips, same one-line planner on GPU. Two runs: with the planner loaded (A) and Moonshine alone (B).

| | Gemma 4 E2B audio (file form) | Moonshine v2 Small |
| :-- | :-- | :-- |
| WER | 3.4% (3 errors in 88 words) | 2.3% (2 errors: "carthic" for Karthik, "Murgan" for Murugan) |
| Entity error (amounts / names / times) | 8.7% (0% / 20% / 0%) | 8.7% (0% / 20% / 0%) |
| Right action, end to end | 11 of 12 | 12 of 12 |
| Right details, end to end | 10 of 12 | 11 of 12 |
| Transcription time, median per clip | 2.75 s | 1.33 s (A) and 1.32 s (B) |
| Real-time factor, median | 0.72 | 0.36 |
| Slowest clip | 3.3 s (first, warm-up) | A: 9.7 s (see below); B: 1.56 s (first) |
| Model load | 5.3 s | 0.66 s (A), 0.42 s (B) |
| Peak memory | 2207 MB (audio and planner in one engine) | A: 2421 MB (Moonshine and planner); B: 611 MB (Moonshine alone) |
| Extra download | none | 142.3 MB |
| Licence | Apache-2.0 | MIT |

- **Errors differ in kind.** Gemma heard "pay Karthik" as "Hey Karthik", so the planner drafted a message instead of opening a payment. Moonshine heard "carthic", so the action was right but the payee was misspelled. Both are one wrong word out of four.
- **One stall.** In run A, clip 10 took 9.7 s and clip 11 took 2.9 s while the others took 1.1 to 1.5 s. It did not repeat in run B (all 12 clips 0.56 to 1.56 s). The cause is not known; contention with the GPU planner in the same process is one suspect, and it needs more runs before it is called a pattern.
- **Limit check (proposed, not frozen): 1500 ms from release to final text.** Moonshine run B is at 1.32 s median and 1.56 s p95 (the first clip), so it is marginal here; Gemma at 2.75 s is not met. Both numbers are for a whole-clip call after the speech ends. Moonshine's streaming mode transcribes while the person is still speaking; it is measured in the next section.
- **Not tested:** real human or accented speech, noise, biasing with names.

## Third measurement: Moonshine v2 Small with a simulated live feed (measured 2026-10-24)

Each clip is given to Moonshine's streaming API (`createStream`, `addAudioToStream`, `stopStream`) in 100 ms chunks, each chunk delivered no earlier than the speech it contains would exist, so the engine works while the clip "plays". The clock for the wait starts when the audio ends. Same 12 clips and phone. Run C: Moonshine alone. Run D: the Gemma planner also loaded and run on each transcript.

| | Whole clip after it ends (run B) | Live feed (run C) | Live feed with planner loaded (run D) |
| :-- | :-- | :-- | :-- |
| Wait from the end of the audio to the final text, median | 1.32 s | 0.39 s | 0.39 s |
| Same, 95th percentile (slowest of 12) | 1.56 s | 0.91 s | 0.91 s |
| Same, fastest clip | 0.56 s | 0.14 s | 0.09 s |
| WER | not scored in this run | 1.1% (1 error: "carthic") | 1.1% |
| Entity error (names) | n/a | 4.3% (10%) | 4.3% (10%) |
| Right action / details, end to end | n/a | n/a (planner off) | 12 of 12 / 11 of 12 |
| Peak memory | 611 MB | 609 MB | 2439 MB |

- **Against the proposed, not yet frozen, limit** of 1500 ms from release to final text: met (p95 0.91 s), where the whole-clip call was marginal (1.56 s) and Gemma audio is not met (2.7 s median).
- **Why the wait is not near zero:** the time from the last chunk to the final text is about 5 ms for most clips, but on some clips the engine fell behind the speech (a pass takes longer than the audio it covers), and that backlog is cleared only after the audio ends. The slowest clips (0.6 to 0.9 s) are the ones that fell behind.
- **Streaming changed one transcript:** "Murugan Idli" was right in the live feed and "Murgan Idli" in one whole-clip run. With 12 clips that is noise, not a finding.
- **Limits of this test:** the feed is on one thread, so a long pass delays the next chunk; a real app would capture on its own thread but would have the same work left at release. It does not include end-of-speech detection, microphone capture, or screen delays. Chunk sizes other than 100 ms are covered in the next section. Still synthetic US-English speech.

## Order and chunk-size checks (measured 2026-10-24, same 12 clips)

- **Gemma audio placed after the text** (planner loaded): transcription median 2.75 s, the same as audio first (2.75 s). WER 2.3% (audio first: 3.4%), entity error 4.3% (8.7%), right action 11 of 12 (same), right details 11 of 12 (10 of 12). Both orders hear "pay Karthik" as "Hey Karthik". The transcript differences are one or two words out of 88, which is noise at this sample size; the order does not change the speed.
- **Moonshine live feed, chunk size** (planner off), wait from the end of the audio to the final text:

  | Chunk | Median | 95th percentile (slowest of 12) | Peak memory |
  | :-- | :-- | :-- | :-- |
  | 20 ms | 0.26 s | 0.86 s | 624 MB |
  | 50 ms | 0.31 s | 0.81 s | 623 MB |
  | 100 ms | 0.39 s | 0.91 s | 609 MB |
  | 200 ms | 0.29 s | 1.00 s | 613 MB |

  There is no clear trend: the medians sit between 0.26 and 0.39 s and the slowest clip between 0.81 and 1.00 s, and with 12 clips the ranges overlap. With 200 ms chunks the time from the last chunk to the final text is larger (median 0.21 s, against about 5 ms for the smaller chunks) because the last pass covers more audio. Chunk size is not a lever worth tuning on this evidence.

## Name biasing (measured 2026-10-24, same 12 clips, planner loaded)

The command set mentions 14 names and merchants (8 first names, 6 shops). Three conditions: no hints, the 14 terms, and the 14 plus 60 common first names that never occur in the clips ("decoys", a stand-in for a bigger contact list; some sound close to real ones, for example Arjun and Arun). Moonshine gets the terms through `setKeyterms` (default strength) in the live feed with 100 ms chunks; Gemma gets them as one sentence in the transcription prompt.

| Run | WER | Name errors | Entity error | Right action / details | Transcription time, median / slowest |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Moonshine, no hints | 1.1% | 10% | 4.3% | 12 / 11 of 12 | 0.39 s / 0.91 s |
| Moonshine, 14 terms | 1.1% | 10% | 4.3% | 12 / 11 of 12 | 0.25 s / 1.33 s |
| Moonshine, 14 + 60 decoys | 1.1% | 10% | 4.3% | 12 / 11 of 12 | 0.35 s / 0.76 s |
| Gemma, no hints | 3.4% | 20% | 8.7% | 11 / 10 of 12 | 2.75 s / 3.30 s |
| Gemma, 14 names in the prompt | 1.1% | 0% | 0.0% | 11 / 11 of 12 | 2.52 s / 3.14 s |
| Gemma, 14 + 60 decoys in the prompt | 1.1% | 0% | 0.0% | 11 / 11 of 12 | 2.94 s / 3.93 s |

- **Gemma:** the hint fixed both "Idly" spellings of Murugan Idli, with or without decoys. It did not fix "pay Karthik" heard as "Hey Karthik", because the wrong word is "pay", not a name. The 60 decoys cost about 0.2 to 0.4 s more per clip (a longer prompt; the GPU prompt step is about 0.41 s per 128 tokens, from the earlier measurement). A contact list of hundreds of names would cost more; that is an estimate, not measured.
- **Moonshine:** the transcripts did not change at all. "Karthik" still came out as "carthic" with the term in the list. The call succeeded without an error, so I do not know whether the bias did not apply here or was too weak for this voice; I did not try other strengths (`keyterm_boost`) or passing the terms at load time.
- **Limits:** one clip per name, the hint list contains exactly the names in the test (an easy case), synthetic US-English speech. Real speakers and real contact lists may behave differently.

## Real recordings: speaker S03, 34 takes (measured 2026-10-24)

First real speech: one speaker (S03), the whole 34-take sheet read into the phone's own microphone through the recorder app (2.6 minutes of audio, 16 kHz mono). Same setup as before: Gemma 4 E2B audio on the file form with the planner on GPU; Moonshine v2 Small in the simulated live feed with 100 ms chunks; each with and without the 14 names and merchants as hints. The other five speakers are not recorded yet, so everything below is one voice and one session.

| Run | WER | Entity error (amount / name / time) | Right action / details, end to end | Transcription time, median / 95th percentile |
| :-- | :-- | :-- | :-- | :-- |
| Gemma, no hints | 9.7% | 14.1% (10.0% / 21.4% / 7.7%) | 28 / 25 of 34 (82.4% / 73.5%) | 2.67 s / 4.21 s |
| Gemma, names in the prompt | 9.0% | 4.7% (0.0% / 3.6% / 7.7%) | 27 / 27 of 34 (79.4% / 79.4%) | 2.57 s / 3.62 s |
| Moonshine live, no hints | 27.0% | 29.7% (10.0% / 53.6% / 11.5%) | 23 / 20 of 34 (67.6% / 58.8%) | 0.43 s / 1.68 s after the audio ends |
| Moonshine live, key terms | 22.3% | 17.2% (10.0% / 25.0% / 11.5%) | 24 / 21 of 34 (70.6% / 61.8%) | 0.42 s / 1.41 s after the audio ends |

For comparison, the same planner on typed text gets 98 to 99% right action and details on the full English command set (measured earlier). So the voice penalty on this speaker is about 16 points of action and 24 points of details for Gemma, and about 31 and 39 points for Moonshine. On the synthetic clips both were near the typed result.

- **Gemma is clearly more accurate than Moonshine on this voice** (WER 9.7% against 27.0%), and the key terms helped Moonshine much more here than on the synthetic clips (name errors 53.6% to 25.0%), but it is still far behind. Moonshine is about six times faster.
- **Name hints work for Gemma on real speech too:** name errors 21.4% to 3.6%. They did not raise the end-to-end action rate (28 to 27 of 34), because the main failures are elsewhere.
- **The main failure is the word "pay".** By command type (right action / right details, Gemma without hints): reminders 7 / 7 of 8, calendar events 5 / 5 of 5, messages 5 / 5 of 5, expenses 5 / 3 of 5, tasks 2 / 2 of 2, but payments only 2 / 1 of 5. In four of the five payment takes the word was heard as "Per", "P eh", "P" or "Please" or dropped, so the planner chose a note or a task instead (for example "Per Ravi 840 rupees" became a note). With hints Gemma got 1 of 5 payments. Moonshine got 0 of 5 without hints and 1 of 5 with. Payments are the riskiest command, so this is the result to act on first.
- **Other errors:** "Murugan Idli" and "Meena" in several spellings, "Uber" as "yuber", "Karthik" and "Arun" as "around" or "Kall"; one take (the 6 AM reminder for Vivek, 6.5 s, far block) came out as "Hey, are you there?" in both models, so it may not contain the sentence at all. I have not listened to the files.
- **Not read from these numbers:** the quiet, noisy and far blocks differ in sentences as well as conditions (Gemma WER 10.2%, 2.2% and 16.2%), and I do not know how noisy the noisy block was, so the condition split is not a finding.
- **Against the proposed, not yet frozen, limits:** end-to-end action 90% and details 85% are not met by any run (best 82.4% and 79.4%); the entity error limit of 8% is met by Gemma with names (4.7%); the 1.5 s wait limit is met by Moonshine (1.41 s to 1.68 s at the 95th percentile, marginal) and not by Gemma (2.6 s median).

## Command-word hint and one-call speech to action (measured 2026-10-24, speaker S03, 34 takes)

Two changes to Gemma 4 E2B, same 34 takes, GPU, file form, planner on GPU:

- **Command-word hint:** one extra sentence in the transcription prompt: "The speaker is giving a command to a phone assistant. Commands often start with these words: pay, remind me, schedule, message, save a note, add a task, I spent." It is used alone and together with the 14 names.
- **One call:** Gemma hears the audio and answers with the one-line action directly (the same command prompt the planner uses), so there is no transcript and no second call.

| Run | WER | Entity error (amount / name / time) | Right action / details, end to end | Transcription time, median / 95th percentile |
| :-- | :-- | :-- | :-- | :-- |
| Gemma, no hints | 9.7% | 14.1% (10.0% / 21.4% / 7.7%) | 28 / 25 of 34 (82.4% / 73.5%) | 2.67 s / 4.21 s |
| Gemma, names | 9.0% | 4.7% (0.0% / 3.6% / 7.7%) | 27 / 27 of 34 (79.4% / 79.4%) | 2.57 s / 3.62 s |
| Gemma, command words | 6.1% | 10.9% (0.0% / 17.9% / 7.7%) | 32 / 30 of 34 (94.1% / 88.2%) | 2.57 s / 3.67 s |
| Gemma, names and command words | 4.7% | 4.7% (0.0% / 3.6% / 7.7%) | 32 / 32 of 34 (94.1% / 94.1%) | 2.69 s / 3.63 s |
| Moonshine live, names and command words as key terms | 20.5% | 17.2% (10.0% / 25.0% / 11.5%) | 24 / 20 of 34 (70.6% / 58.8%) | 0.42 s / 1.33 s after the audio ends |
| One call, no hints | not scored | not scored | 27 / 21 of 34 (79.4% / 61.8%) | 3.81 s / 7.07 s |
| One call, names | not scored | not scored | 28 / 24 of 34 (82.4% / 70.6%) | 3.94 s / 5.19 s |

The times are for the audio step alone. The planner call adds about 1 to 3 s after the transcript (a rough figure from the typed-text runs, not measured per take here), so transcribe-then-plan is about 4 to 6 s end to end.

- **The command-word hint fixes "pay".** Payments (right action / details, 5 takes): 2 / 1 without hints, 1 / 1 with names, 5 / 4 with command words, 5 / 5 with names and command words. Every payment take was heard as "Pay <name> <amount> rupees" in the last run. End-to-end action went from 82.4% to 94.1%, the first time a run met the proposed 90% limit; details reached 94.1% against the proposed 85%, and the entity error 4.7% against 8%.
- **The two hints add up.** Command words repair the verb, names repair the nouns. Command words alone left name errors at 17.9%; names alone left "pay" broken.
- **Command words as Moonshine key terms did not help.** The seven command words were added to the 14 names in `setKeyterms` (default strength). Word error rate moved from 22.3% to 20.5%, but end-to-end action stayed at 70.6%, details fell from 61.8% to 58.8% (one take), and payments stayed at 1 of 5 right ("Pray Ravi", "Pace Rish", "Pay around"). One take of 34 is noise, so the honest reading is no effect, as with the names on the synthetic clips. Moonshine also sometimes writes amounts as words ("eight forty rupees"), which the planner read as 84 once. I did not try `keyterm_boost` or load-time terms. Moonshine stays at 0.4 s after the audio ends, but its accuracy on this voice is not close to Gemma's.
- **One call is worse and not clearly faster.** Action 79.4% to 82.4% against 94.1%, details 61.8% to 70.6% against 94.1%, and the audio step alone takes 3.8 s to 3.9 s, which is not far below transcribe-then-plan once the planner call is added (an estimate). Its errors were a payment planned as an expense or a task, one wrong amount, and relative dates resolved to the wrong day (for example "day after tomorrow"). It also gives no transcript to show the user before acting, which the product wants for confirming a payment. Not worth pursuing on this evidence.
- **What is left in the best run (names and command words):** two takes. S03-cmd-059-f was heard as "Hey, are you there?" and planned as no action (the take may not contain the sentence; I have not listened). S03-cmd-284-f "How much did I spend on food last week?" was planned as a note instead of a memory question. The second sentence was transcribed correctly, so it is a planner problem, not a speech problem.
- **Limits:** one speaker, one session, 34 takes. The hint lists exactly the command words and names of the test sentences, an easy case; a real contact list is bigger and a real user's phrasing wider. The runs are made at different times and the quiet, noisy and far blocks differ in sentences, so differences of one or two takes are not findings. The 1.5 s limit on the wait is still not met by Gemma (median 2.6 s for the audio step alone).

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
