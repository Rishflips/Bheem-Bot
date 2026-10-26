# Stage 7 evidence: voice end to end

Phone: Moto g86 power (Android 16), debug build, 2026-10-27. Numbers are measured on this one phone unless marked target. Gemma 4 E2B hears the audio itself (no separate speech model), with a hint that lists the command words and names taken from the user's own records.

## Replay of the benchmark takes through the app path

The 34 recorded takes of the speech benchmark (`gemma-audio-file+commands` set, one speaker) were pushed to the phone and played through the same code as a spoken command: bytes in memory, `GemmaAudioSpeech`, hints, `CommandPipeline` with source `voice`. A debug-only replay (`VoiceReplay`) did this; the release build has an empty stub.

| Measure | Benchmark (earlier, file path) | App path (this stage) |
| :-- | :-- | :-- |
| Takes | 34 | 34 |
| Action correct end to end | 32 of 34 (94.1%) | 33 of 34 (97.1%) |
| Payment takes | answered as expenses | 5 refused with "payments come later" (by design since stage 6) |
| Per take, hearing plus planning | not comparable | median 6.4 s, p95 8.6 s, max 10.2 s |

- The one miss in the app path is take `S03-cmd-059-f` ("Hey, are you there?" heard instead of the payment command), the same take that missed in the benchmark. The difference of one take is within noise on 34 takes of one speaker; it is not evidence that the app path is better.
- Dropping "pay" from the command-word hint made one payment take read as "Hey ..."; "pay" is back in the list and has a test.
- These takes were recorded before the app existed, so they test the model and the pipeline, not the microphone path.

## Live spoken commands on the phone

| Check | Result |
| :-- | :-- |
| Microphone permission denied | Plain message: "The microphone is off for Bheem, so talking does not work. Turn it on in the app settings, or type instead." |
| Hold the button, speak "Remind me to call Arun tomorrow at 5 pm" (model cold, 5.9 s of audio) | Heard correctly, Reminder card "call Arun, Wed 28 Oct, 5:00 PM", 14.1 s from release to card |
| Warm model, 8.9 s of audio; the speaker changed the sentence | Heard "Remind me to call Shankar tomorrow at 10 a.m. Okay." and the speaker confirmed this is what was said; Reminder card "call Shankar, Wed 28 Oct, 10:00 AM" after 12.9 s |
| Almost no speech (held button, quiet room) | 46 ms to "I could not hear anything" (peak level below the silence limit); no model call |
| Background noise only | The model repeated the hint list instead of transcribing; before the guard below this became an Expense card |

- Release to card is over the 6 s target (target) on every live press with speech: 12.9 s for 8.9 s of audio warm, 14.1 s for 5.9 s cold. The time grows with the length of the audio and with an unclear clip that makes the model write up to 128 tokens. No live press with a short clip (about 3 s) was measured, so the 6 s target is not shown met or missed for short commands.
- The audio is recorded into memory (16 kHz mono, 10 s cap) and passed to the model as bytes. A search of the app's private and external folders after the sessions found no wav, pcm, 3gp, m4a, amr or raw file; the `recordings/audio` folder from the earlier benchmark is empty. The pushed fixtures and the replay results file were deleted from the phone.

## Found on the phone and fixed

- On unclear audio the model read the hint list back ("pay remind me schedule message save a note add a task I spent Ravi Priya ..."), and the planner turned that into a ₹10 Expense card. A transcript that repeats the hint list is now treated as nothing heard ("I did not catch that. Try again."). Confirmed on the phone with a noise-only press.
- The first version of the guard dropped any transcript with three command words, which the Opus review showed would reject real commands such as "Remind me to pay the bill and message Ravi". The guard now needs at least two command words and at most 20% of the words outside the hint list (command words and the user's names), or the prompt's own lead-in phrase. Tests cover the echoed list, a partial echo and four real commands.

## After the Opus review

The review (PASS WITH CHANGES, no Critical findings) led to these changes and checks.

| Check | Result (measured) |
| :-- | :-- |
| Assistant development set, 100 cases, GPU, prompt v3.8, audio encoder loaded (as the app ships) against the earlier text-only run | 100 of 100 raw answers identical; action 92.0% and args 78.0% raw in both; JSON validity 100.0% in both; median 2267 ms against 2295 ms per answer |
| Load time, GPU | 38.3 s with the audio encoder against 40.9 s without (two separate runs; the gap is within what a single run varies by, so treat it as no difference) |
| Memory, same runs | peak resident 2340 MB against 2262 MB (+78 MB); proportional set after load 2240 MB against 2216 MB |
| Leave the app (Home key) while the Talk button is held | `dumpsys audio` showed the recording active during the hold and no active recording after Home; no result was logged and no card was made |
| One benchmark clip replayed through the app path | Heard "Remind me today at 6 PM to call Ravi", Reminder card, 11.9 s from start to card. The app folders (cache, files, no_backup, and the external cache) gained no file during the replay except a rewrite of `model-host.json`; no wav, pcm, raw, m4a, amr or aac file exists under the app folders. Logcat for the replay held none of the hint names or command words |

- The text-only comparison file is from the earlier Stage 6 run; the audio-encoder run was a separate run of the same cases, so a 100 of 100 match shows the encoder does not change text answers on this set, not that a repeat run would be identical to the byte.
- The held-out set was not run with the audio encoder; I did not open it. The development result is the evidence.
- A first attempt with the older v3.5 prompt (`line`) was discarded, because it is not the prompt the app ships.

Code changes from the review:

- Hint names come only from proposals with user provenance and the records those proposals created (a test shows tainted ones are ignored).
- The hold-to-talk logic is now `TalkSession` with a recorder interface and ten tests (double start, busy model, leaving the screen, release racing the 10 s cap, too-short and silent clips, a microphone that will not open). The release-and-cap race is closed by a single atomic state change. The recorder is synchronized, stops the microphone before waiting for its thread, and reports a microphone it could not start.
- When a spoken command makes no card, the message now begins with what was heard.
- If the audio encoder fails to load, the engine loads again without it so typed commands keep working, and voice then reports an error. This fallback is not tested (the engine needs the native library) and has not been exercised on a phone.

Deferred review points (not fixed, none is a Critical or Important finding): voice hints read every record of a kind before keeping 100 (cap the query); recording starts even when the model cannot run, so the user speaks and then hears that voice needs the model; hearing and planning acquire the model separately; the eval keeps its own copy of the transcription prompt text; `AudioPayloads` ignores the form when bytes are present; dragging a finger off the Talk button still sends; no "microphone on, press again" hint after the first permission grant; `GemmaAudioSpeech` is not specific to Gemma by name. The debug replay can be started by another app through an intent extra in debug builds only; the release build has an empty stub.

## Not done or not verified

- The 6 s release-to-card target is not met for 6 to 9 s utterances and not measured for short ones.
- Live accuracy is two presses, both heard correctly according to the speaker; there is no live word error rate, only the 34 replayed takes.
- Names heard from the hint list were not tested live with a name outside the user's records.
- The talk logic is tested on the JVM with a fake recorder; the real `AudioRecord` path was exercised only by hand-held presses on one phone.
- Hold-to-talk with the screen turned off was not tested.
- English only. No other language was tried.
