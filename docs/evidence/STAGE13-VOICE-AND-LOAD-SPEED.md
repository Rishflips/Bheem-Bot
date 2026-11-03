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

- Five warm presses: median 4.0 s, worst 5.7 s (the 5.5 s clip). All five are under the 6 s target and the 8 s limit (targets). The earlier Stage 7 figures were 12.9 to 14.1 s for 6 to 9 s of audio (measured, older build, longer clips), so the two sets are not directly comparable, but the warm times are about a third of those.
- **This is not the Stage 13 exit test.** The exit needs ten warm presses; five were logged. Clips were mostly shorter than the 5 s the target is defined for, and the transcripts were not scored against what was said. The backend (GPU or CPU) was not recorded for these presses.
- **The gain cannot be split between the 48-token cap and the newline stop**: there was no run of the old build for comparison, and nothing logs the number of tokens decoded per press.

## Not done or not verified

- **Whether cancelling the stream really stops native decoding in LiteRT-LM 0.18.1 is not verified.** The JVM tests cover the helper and the request shape only; the press times above are short, but no token count was logged to show where the saving comes from.
- Ten warm live presses with p50 at or under 6 s and p95 at or under 8 s (targets): five were logged (above), all within the targets; the full ten and the 5 s clip length are still to do.
- One acquire per press: not changed. A press takes two acquires (transcribe, then plan) back to back and the idle unload waits 180 s, so the second acquire never reloads the model. The expected gain is only a mutex hand-off, so it needs a measurement before it is worth an interface change.
- "Now" moved out of the system prompt (prefix reuse), the prefill-reuse measurement, the CPU versus GPU study, the idle window and the first token in the card header: not started. Moving "now" changes the prompt, so it needs a new prompt version and fresh goldens on the phone.
- Findings for `docs/MODELS.md`: not written yet.
