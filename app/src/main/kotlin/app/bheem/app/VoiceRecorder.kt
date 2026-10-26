package app.bheem.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.bheem.models.speech.Levels
import app.bheem.models.speech.RecordingBuffer

/** Records 16 kHz mono speech into memory only. Nothing is written to disk. */
class VoiceRecorder {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var buffer: RecordingBuffer? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start(onLevel: (Float) -> Unit, onFull: () -> Unit): Boolean {
        if (running) return false
        val minimum = AudioRecord.getMinBufferSize(RecordingBuffer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        val created = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RecordingBuffer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, CHUNK * 4),
            )
        } catch (_: Exception) {
            return false
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }
        val into = RecordingBuffer()
        buffer = into
        record = created
        running = true
        created.startRecording()
        worker = Thread {
            val chunk = ShortArray(CHUNK)
            while (running) {
                val read = created.read(chunk, 0, chunk.size)
                if (read <= 0) break
                val room = into.append(chunk, read)
                onLevel(Levels.level(chunk, read))
                if (!room) {
                    running = false
                    onFull()
                }
            }
        }.also { it.start() }
        return true
    }

    /** Stops recording and returns what was heard. Safe to call more than once. */
    fun stop(): ShortArray {
        running = false
        worker?.join(JOIN_MILLIS)
        worker = null
        release()
        return buffer?.samples() ?: ShortArray(0)
    }

    fun cancel() {
        stop()
        buffer = null
    }

    private fun release() {
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    private companion object {
        const val CHUNK = 1_600
        const val JOIN_MILLIS = 500L
    }
}
