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

- On unclear audio the model read the hint list back ("pay remind me schedule message save a note add a task I spent Ravi Priya ..."), and the planner turned that into a ₹10 Expense card. A transcript that holds three or more of the command words is now treated as nothing heard ("I did not catch that. Try again."), with tests for the repeated list and for a real command that uses two of the words. Confirmed on the phone with a noise-only press.

## Not done or not verified

- The 6 s release-to-card target is not met for 6 to 9 s utterances and not measured for short ones.
- Live accuracy is two presses, both heard correctly according to the speaker; there is no live word error rate, only the 34 replayed takes.
- Names heard from the hint list were not tested live with a name outside the user's records.
- Hold-to-talk with the screen turned off or while another app is in front was not tested; recording stops when the app is paused (covered by a test of the controller logic, not run on the phone).
- English only. No other language was tried.
