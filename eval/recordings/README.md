# Recording sheets for the English speech-to-text study

Generated from the English commands in `eval/data/commands.jsonl`. The sentences are synthetic, so no real person's data is involved.

## Plan

202 takes, 6 speakers, 2 readers per command
Takes per speaker: S01 34, S02 34, S03 34, S04 34, S05 33, S06 33
Takes per condition: quiet 68, noisy 67, far 67

Every command is read by 2 different speakers. Each speaker reads about the same number of sentences of each kind, and every speaker records the same share of each condition. Sentence order inside a block is shuffled with a fixed seed.

## Files

- `manifest.jsonl`: one line per take (take ID, speaker, command ID, sentence, condition, position on the sheet).
- `sheet-S01.md`: the printable sheet for speaker S01.
- `sheet-S02.md`: the printable sheet for speaker S02.
- `sheet-S03.md`: the printable sheet for speaker S03.
- `sheet-S04.md`: the printable sheet for speaker S04.
- `sheet-S05.md`: the printable sheet for speaker S05.
- `sheet-S06.md`: the printable sheet for speaker S06.
- `audio/<take ID>.wav`: the recordings, 16 kHz mono WAV. Never commit audio or signed consent forms; `audio/` is git-ignored.

## Handling

- Each speaker is known only by their ID. Keep the list that links IDs to people, and the signed consent, outside the repository.
- Record on the phone being tested, with its own microphone, so the audio matches real use.
- Delete all recordings when the study ends.

## Recording with the phone app (recommended)

1. Build the app: `.\gradlew.bat :bench:assembleDebug`.
2. Connect the phone and run `.\scripts\prepare-recorder.ps1`. It installs the app, puts the list of takes on the phone, allows the microphone and opens Bheem Recorder.
3. Pick the speaker ID from the sheet. Each sentence is shown in turn. Hold the big button while reading and release when done. A take that is too short, too quiet or has no speech is rejected with a message; a good take is saved under the right name and the next sentence appears.
4. Do the blocks in the order shown, setting up the room as each block says.
5. When the session is over, copy the files to the computer with `.\scripts\pull-recordings.ps1`, then check them with `.\gradlew.bat :eval:checkRecordings`.

Recordings stay on the phone between sessions, so the list of takes can be pushed again at any time. Copy them to the computer before running the probe with other audio.

## Running the probe on the phone

1. Put the recordings in `eval/recordings/audio/`, one `<take ID>.wav` per take. If your recorder writes m4a or mp3, name each file by its take ID and convert: `.\scripts\convert-recordings.ps1 -InDir <folder>`. Then see what is missing or in the wrong format: `.\gradlew.bat :eval:checkRecordings`.
   No recordings yet? A few seconds of Windows synthetic speech can smoke-test the audio path: `.\scripts\make-synthetic-audio.ps1`, then use `-Recordings eval\recordings-synthetic` in step 3. It is clean US English, so it says nothing about accents or noise.
2. Build the app: `.\gradlew.bat :bench:assembleDebug`.
3. Quick probe of 12 takes in every audio form: `.\scripts\bench-speech.ps1 -Device midrange-a -ModelId gemma-4-e2b -ModelFile models-local\gemma-4-e2b\gemma-4-E2B-it.litertlm -Recordings eval\recordings -AudioForms all -Limit 12`. The forms are `file` (the path is handed to the library), `wav` (the file bytes) and `pcm` (the raw samples). The probe shows which one the library accepts. Then drop `-Limit` and keep one form for the full study.
4. Report: `.\gradlew.bat :eval:speechReport -PspeechResult=<result.json> -PspeechReportOut=<report.md>`.

## Scoring

The scorer takes each take's transcript, normalizes it, and compares it with the sentence for word error rate. It reads the expected action in `commands.jsonl` to find the amounts, names and times that must be heard, and reports an entity error rate for them.
