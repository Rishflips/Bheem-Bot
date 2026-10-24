package app.bheem.bench

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.bheem.eval.data.Jsonl
import app.bheem.eval.speech.RecorderState
import app.bheem.eval.speech.RecordingQuality
import app.bheem.eval.speech.Take
import app.bheem.models.WavWriter
import java.io.File
import java.util.Locale

/** Records the sentences of the speech study straight into correctly named 16 kHz mono WAV files. */
class RecordActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private lateinit var audioDir: File
    private var takes: List<Take> = emptyList()
    private var speaker: String? = null
    private var state: RecorderState? = null
    private var capture: Capture? = null
    private var status = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        actionBar?.hide()
        setContentView(
            ScrollView(this).apply {
                addView(content)
                // Android 15 and later draw app content under the system bars (targetSdk 36), so pad by the bar sizes.
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    WindowInsets.CONSUMED
                }
            },
        )
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
        val recordings = File(getExternalFilesDir(null) ?: return, "recordings")
        audioDir = File(recordings, "audio").apply { mkdirs() }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        val manifest = File(recordings, "manifest.jsonl")
        if (!manifest.exists()) {
            content.addView(label("No recording list on the phone. Run scripts\\prepare-recorder.ps1 on the computer first.", 18f))
            return
        }
        takes = Jsonl.read<Take>(manifest.toPath())
        showSpeakers()
    }

    override fun onDestroy() {
        capture?.stop()
        super.onDestroy()
    }

    private fun doneIds(): Set<String> =
        audioDir.listFiles().orEmpty().filter { it.name.endsWith(".wav") && it.length() > 44 }.map { it.name.removeSuffix(".wav") }.toSet()

    private fun showSpeakers() {
        speaker = null
        content.removeAllViews()
        content.addView(label("Who is reading?", 24f, bold = true))
        val done = doneIds()
        for (name in RecorderState.speakers(takes)) {
            val state = RecorderState.forSpeaker(takes, name, done)
            content.addView(
                Button(this).apply {
                    text = "$name: ${state.doneCount} of ${state.total} recorded"
                    textSize = 20f
                    setOnClickListener { speaker = name; this@RecordActivity.state = state; status = ""; render() }
                },
            )
        }
    }

    private fun render() {
        val current = state ?: return
        content.removeAllViews()
        val take = current.current
        content.addView(label("$speaker   take ${current.index + 1} of ${current.total}   ${current.doneCount} recorded", 16f))
        if (take == null || current.finished) {
            content.addView(label("All takes are recorded. Thank you.", 24f, bold = true))
        }
        if (take != null) {
            content.addView(label(current.blockLabel() + if (current.startsBlock) "   (new block: set up now)" else "", 16f, bold = current.startsBlock))
            content.addView(label(take.condition.instruction, 14f))
            content.addView(label(take.text, 30f, bold = true).apply { setPadding(0, 48, 0, 48) })
            content.addView(label(status, 16f))
            content.addView(
                Button(this).apply {
                    text = "Hold to record"
                    textSize = 24f
                    minimumHeight = 320
                    setOnTouchListener { _, event -> onRecordButton(event, take) }
                },
            )
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Button(this).apply { text = "Previous"; setOnClickListener { state = current.move(-1); render() } })
        row.addView(Button(this).apply { text = "Next"; setOnClickListener { state = current.move(1); render() } })
        row.addView(Button(this).apply { text = "Change speaker"; setOnClickListener { showSpeakers() } })
        content.addView(row)
    }

    private fun onRecordButton(event: MotionEvent, take: Take): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startCapture(take)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.postDelayed({ capture?.stop() }, TAIL_MS)
        }
        return true
    }

    private fun startCapture(take: Take) {
        if (capture != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return setStatus("The microphone needs permission. Allow it, then try again.")
        }
        try {
            capture = Capture { samples -> main.post { capture = null; finish(take, samples) } }
            setStatus("Recording... keep holding while you read.")
        } catch (e: Exception) {
            capture = null
            setStatus("The microphone could not start: ${e.message}")
        }
    }

    private fun finish(take: Take, samples: ShortArray) {
        val quality = RecordingQuality.check(samples)
        if (!quality.ok) {
            return setStatus(RecordingQuality.message(quality.verdict) + String.format(Locale.ROOT, " (%.1f s, peak %.2f)", quality.durationMs / 1000.0, quality.peak))
        }
        val target = File(audioDir, "${take.id}.wav")
        val partial = File(audioDir, "${take.id}.wav.part")
        partial.writeBytes(WavWriter.speech(samples))
        partial.renameTo(target)
        state = state?.markDone(take.id)
        status = String.format(Locale.ROOT, "Saved %s (%.1f s).", target.name, quality.durationMs / 1000.0)
        render()
    }

    private fun setStatus(text: String) {
        status = text
        render()
    }

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(null, Typeface.BOLD)
        visibility = View.VISIBLE
    }

    private class Capture(private val onFinished: (ShortArray) -> Unit) {
        @Volatile private var running = true
        private val chunks = ArrayList<ShortArray>()
        private val record: AudioRecord

        init {
            val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, RATE * 2),
            )
            check(record.state == AudioRecord.STATE_INITIALIZED) { "the recorder is not ready" }
            record.startRecording()
            Thread {
                val buffer = ShortArray(RATE / 10)
                var total = 0
                while (running && total < MAX_SAMPLES) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read > 0) {
                        chunks += buffer.copyOf(read)
                        total += read
                    }
                }
                record.stop()
                record.release()
                onFinished(ShortArray(total).also { out ->
                    var at = 0
                    for (chunk in chunks) {
                        chunk.copyInto(out, at)
                        at += chunk.size
                    }
                })
            }.start()
        }

        fun stop() {
            running = false
        }
    }

    private companion object {
        const val RATE = 16_000
        const val MAX_SAMPLES = RATE * 30
        const val TAIL_MS = 250L
    }
}
