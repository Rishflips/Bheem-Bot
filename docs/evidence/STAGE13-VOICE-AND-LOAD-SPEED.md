# Stage 13 evidence: voice and load speed (in progress)

Date 2026-11-03. JVM results unless marked. Numbers are measured or are targets.

## Done so far (JVM tests green, then five warm presses on the phone)

- **Transcription is capped at 48 tokens (was 128) and stops at the first line.** `GenerationRequest.stopAtNewline` is a new contract field (default off). `LlmAudioSpeech` sets it with the 48-token cap. `FirstLine` (in `models`) collects streamed text up to the first non-blank line and drops the rest; the LiteRT-LM engine stops collecting the stream at that point and returns only that line. Tests: `FirstLineTest`, `ContractsTest`, `SpeechTest.aTranscriptionIsCappedAtFortyEightTokensAndStopsAtTheFirstLine`.
- Quality gate unchanged and green (`:eval:gate`): the planner path was not touched.

## Live presses on the phone (2026-11-03, Moto g86 power, warm model, measured)

Offline debug build of this commit, Gemma 4 E2B imported by `adb push`, owner speaking, times from the app's own `BheemVoice` log line (release to card).

| Press | Audio | Release to card |
| --: | --: | --: |
| 1 (cold, model load included) | 7.0 s | 17.2 s |
| 2 | 5.5 s | 5.7 s |
| 3 | 2.5 s | 3.9 s |
| 4 | 4.1 s | 4.0 s |
| 5 | 3.1 s | 4.0 s |
| 6 | 3.2 s | 3.9 s |

- Round 1, five warm presses: median 4.0 s, worst 5.7 s (the 5.5 s clip). The earlier Stage 7 figures were 12.9 to 14.1 s for 6 to 9 s of audio (measured, older build, longer clips), so the two sets are not directly comparable.

More presses in two later rounds (same logic; the second build only added the speech-step time to the log line):

| Round | Audio | Release to card | Speech step | Planning step (total minus speech) |
| :-- | --: | --: | --: | --: |
| 2 | 7.2 s | 11.3 s | not logged | not logged |
| 2 | 8.1 s | 11.9 s | not logged | not logged |
| 2 | 7.3 s | 8.0 s | not logged | not logged |
| 3 | 6.1 s | 4.8 s | 3.0 s | 1.8 s |
| 3 | 10.0 s | 6.4 s | 4.5 s | 1.9 s |

- **All ten warm presses together** (rounds 1 to 3): sorted 3.9, 3.9, 4.0, 4.0, 4.8, 5.7, 6.4, 8.0, 11.3, 11.9 s. Median 5.3 s (at or under the 6 s target: met). Worst 11.9 s, so p95 is above the 8 s limit (target: **not met**). The slow ones are the 7 to 8 s clips of round 2; the 10 s clip in round 3 took only 6.4 s, so length alone does not explain them.
- The tail is **unexplained**: round 2 predates the speech-step log, so it is not known whether those slow presses were slow in the speech step or in planning. Round 3 shows the planning step costs about 1.8 to 1.9 s and the speech step is the larger part (3.0 s for 6.1 s of audio, 4.5 s for 10 s).
- Not the Stage 13 exit test: clips were mixed lengths (most under or over the 5 s the target is defined for), transcripts were not scored, the backend was not recorded, and the old build was not run for comparison, so the effect of the 48-token cap and the newline stop is not separated.

## Not done or not verified

- **Whether cancelling the stream really stops native decoding in LiteRT-LM 0.18.1 is not verified.** The JVM tests cover the helper and the request shape only; the press times above are short, but no token count was logged to show where the saving comes from.
- Ten warm live presses: ten are logged across three rounds (above): p50 5.3 s meets the 6 s target, p95 (11.9 s worst) does not meet 8 s. The slow tail needs more presses with the speech-step log to explain.
- One acquire per press: not changed. A press takes two acquires (transcribe, then plan) back to back and the idle unload waits 180 s, so the second acquire never reloads the model. The expected gain is only a mutex hand-off, so it needs a measurement before it is worth an interface change.
- "Now" moved out of the system prompt (prefix reuse), the prefill-reuse measurement, the CPU versus GPU study, the idle window and the first token in the card header: not started. Moving "now" changes the prompt, so it needs a new prompt version and fresh goldens on the phone.
- Findings for `docs/MODELS.md`: not written yet.
