# Stage 13 evidence: voice and load speed (in progress)

Date 2026-11-03. JVM results unless marked. Numbers are measured or are targets.

## Done so far (JVM tests green, not yet run on the phone)

- **Transcription is capped at 48 tokens (was 128) and stops at the first line.** `GenerationRequest.stopAtNewline` is a new contract field (default off). `LlmAudioSpeech` sets it with the 48-token cap. `FirstLine` (in `models`) collects streamed text up to the first non-blank line and drops the rest; the LiteRT-LM engine stops collecting the stream at that point and returns only that line. Tests: `FirstLineTest`, `ContractsTest`, `SpeechTest.aTranscriptionIsCappedAtFortyEightTokensAndStopsAtTheFirstLine`.
- Quality gate unchanged and green (`:eval:gate`): the planner path was not touched.

## Not done or not verified

- **Whether cancelling the stream really stops native decoding in LiteRT-LM 0.18.1 is not verified.** The JVM tests cover the helper and the request shape only. If the engine keeps decoding after collection stops, the newline stop saves nothing and only the 48-token cap helps. Needs the phone.
- Ten live presses with p50 at or under 6 s and p95 at or under 8 s (targets): not run (needs the phone and the owner speaking).
- One acquire per press: not changed. A press takes two acquires (transcribe, then plan) back to back and the idle unload waits 180 s, so the second acquire never reloads the model. The expected gain is only a mutex hand-off, so it needs a measurement before it is worth an interface change.
- "Now" moved out of the system prompt (prefix reuse), the prefill-reuse measurement, the CPU versus GPU study, the idle window and the first token in the card header: not started. Moving "now" changes the prompt, so it needs a new prompt version and fresh goldens on the phone.
- Findings for `docs/MODELS.md`: not written yet.
