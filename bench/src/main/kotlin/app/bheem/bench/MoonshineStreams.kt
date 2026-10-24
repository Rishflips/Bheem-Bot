package app.bheem.bench

import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptLine
import app.bheem.eval.speech.AudioStream
import app.bheem.eval.speech.StreamLines
import ai.moonshine.voice.Transcriber as MoonshineEngine

class MoonshineStreams(private val engine: MoonshineEngine) {
    private val lines = StreamLines()

    init {
        engine.addListener { event -> record(event) }
    }

    fun open(): AudioStream {
        val handle = engine.createStream()
        engine.startStream(handle)
        return object : AudioStream {
            override fun add(samples: FloatArray, sampleRate: Int) = engine.addAudioToStream(handle, samples, sampleRate)

            override fun finish(): String {
                engine.stopStream(handle)
                return lines.take(handle)
            }

            override fun close() {
                lines.take(handle)
                engine.freeStream(handle)
            }
        }
    }

    private fun record(event: TranscriptEvent) {
        when (event) {
            is TranscriptEvent.LineStarted -> note(event.streamHandle, event.line)
            is TranscriptEvent.LineUpdated -> note(event.streamHandle, event.line)
            is TranscriptEvent.LineTextChanged -> note(event.streamHandle, event.line)
            is TranscriptEvent.LineCompleted -> note(event.streamHandle, event.line)
            else -> Unit
        }
    }

    private fun note(stream: Int, line: TranscriptLine) = lines.update(stream, line.id, line.text.orEmpty())
}
