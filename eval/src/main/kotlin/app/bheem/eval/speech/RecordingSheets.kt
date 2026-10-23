package app.bheem.eval.speech

object RecordingSheets {
    fun speakerSheet(plan: RecordingPlan, speaker: String): String {
        val takes = plan.forSpeaker(speaker)
        val blocks = takes.fold(mutableListOf<MutableList<Take>>()) { acc, take ->
            if (acc.lastOrNull()?.first()?.condition == take.condition) acc.last() += take else acc += mutableListOf(take)
            acc
        }
        return buildString {
            appendLine("# Recording sheet $speaker")
            appendLine()
            appendLine("Speaker ID: $speaker (use this ID, not your name). Do not write your name on this sheet.")
            appendLine()
            appendLine("Native language: ____________________")
            appendLine("Gender: ____________________")
            appendLine("Age band (18-30, 31-45, 46-60, 60+): ____________________")
            appendLine("Phone and how it is held: ____________________")
            appendLine()
            appendLine("## Consent")
            appendLine()
            appendLine(
                "I agree that my voice recordings of the sentences below are used only to test speech recognition for Bheem, " +
                    "kept on the project computer, not shared with anyone else, and deleted when the study ends. " +
                    "I can stop at any time. My signature is kept separately by the project owner, not on this sheet.",
            )
            appendLine()
            appendLine("## How to record")
            appendLine()
            appendLine("- Read each sentence once, in your normal speaking voice and pace, the way you would say it to a phone assistant.")
            appendLine("- Say numbers the way you normally would, for example \"eight forty rupees\". Do not spell them out digit by digit.")
            appendLine("- Record one file per sentence: 16 kHz mono WAV, named <take ID>.wav (for example ${takes.first().id}.wav).")
            appendLine("- If you make a mistake, record the sentence again under the same name. The new file replaces the old one.")
            appendLine("- Do the blocks in the order shown. Set up the conditions before you start each block.")
            for ((index, block) in blocks.withIndex()) {
                val condition = block.first().condition
                appendLine()
                appendLine("## Block ${index + 1} of ${blocks.size}: ${condition.name.lowercase()}")
                appendLine()
                appendLine(condition.instruction)
                appendLine()
                appendLine("| # | Take ID | Sentence to read | Done |")
                appendLine("| --- | --- | --- | --- |")
                for (take in block) appendLine("| ${take.position} | `${take.id}` | ${take.text.replace("|", "\\|")} | [ ] |")
            }
        }
    }

    fun readme(plan: RecordingPlan): String = buildString {
        appendLine("# Recording sheets for the English speech-to-text study")
        appendLine()
        appendLine("Generated from the English commands in `eval/data/commands.jsonl`. The sentences are synthetic, so no real person's data is involved.")
        appendLine()
        appendLine("## Plan")
        appendLine()
        appendLine(plan.summary().trim())
        appendLine()
        appendLine("Every command is read by ${plan.readersPerCommand} different speakers. Each speaker reads about the same number of sentences of each kind, and every speaker records the same share of each condition. Sentence order inside a block is shuffled with a fixed seed.")
        appendLine()
        appendLine("## Files")
        appendLine()
        appendLine("- `manifest.jsonl`: one line per take (take ID, speaker, command ID, sentence, condition, position on the sheet).")
        for (s in plan.speakers) appendLine("- `sheet-$s.md`: the printable sheet for speaker $s.")
        appendLine("- `audio/<take ID>.wav`: the recordings, 16 kHz mono WAV. Never commit audio or signed consent forms; `audio/` is git-ignored.")
        appendLine()
        appendLine("## Handling")
        appendLine()
        appendLine("- Each speaker is known only by their ID. Keep the list that links IDs to people, and the signed consent, outside the repository.")
        appendLine("- Record on the phone being tested, with its own microphone, so the audio matches real use.")
        appendLine("- Delete all recordings when the study ends.")
        appendLine()
        appendLine("## Running the probe on the phone")
        appendLine()
        appendLine("1. Put the recordings in `eval/recordings/audio/`, one `<take ID>.wav` per take. If your recorder writes m4a or mp3, name each file by its take ID and convert: `.\\scripts\\convert-recordings.ps1 -InDir <folder>`. Then see what is missing or in the wrong format: `.\\gradlew.bat :eval:checkRecordings`.")
        appendLine("   No recordings yet? A few seconds of Windows synthetic speech can smoke-test the audio path: `.\\scripts\\make-synthetic-audio.ps1`, then use `-Recordings eval\\recordings-synthetic` in step 3. It is clean US English, so it says nothing about accents or noise.")
        appendLine("2. Build the app: `.\\gradlew.bat :bench:assembleDebug`.")
        appendLine(
            "3. Quick probe of 12 takes in every audio form: `.\\scripts\\bench-speech.ps1 -Device midrange-a -ModelId gemma-4-e2b " +
                "-ModelFile models-local\\gemma-4-e2b\\gemma-4-E2B-it.litertlm -Recordings eval\\recordings -AudioForms all -Limit 12`. " +
                "The forms are `file` (the path is handed to the library), `wav` (the file bytes) and `pcm` (the raw samples). " +
                "The probe shows which one the library accepts. Then drop `-Limit` and keep one form for the full study.",
        )
        appendLine("4. Report: `.\\gradlew.bat :eval:speechReport -PspeechResult=<result.json> -PspeechReportOut=<report.md>`.")
        appendLine()
        appendLine("## Scoring")
        appendLine()
        appendLine("The scorer takes each take's transcript, normalizes it, and compares it with the sentence for word error rate. It reads the expected action in `commands.jsonl` to find the amounts, names and times that must be heard, and reports an entity error rate for them.")
    }
}
