package app.bheem.app

import android.content.Context
import android.os.SystemClock
import app.bheem.core.pipeline.Outcome
import app.bheem.core.speech.VoiceResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug builds only: replays the WAV files in the app's `voice-replay` folder through the same path as a spoken command
 * (bytes in memory, no copy on disk) and writes the transcripts and outcomes to `results/voice-replay.json`.
 */
object VoiceReplay {
    fun maybeRun(activity: MainActivity) {
        if (!activity.intent.getBooleanExtra("voice_replay", false)) return
        activity.intent.removeExtra("voice_replay")
        val app = activity.application as BheemApp
        CoroutineScope(Dispatchers.Default).launch {
            val ready = app.session.first { it is Session.Ready } as Session.Ready
            run(activity.applicationContext, ready.controller)
        }
    }

    private suspend fun run(context: Context, controller: AppController) {
        val base = context.getExternalFilesDir(null)
        val folder = File(base, "voice-replay")
        val out = File(base, "results/voice-replay.json")
        out.parentFile?.mkdirs()
        val rows = JSONArray()
        for (file in folder.listFiles { f -> f.extension == "wav" }.orEmpty().sortedBy { it.name }) {
            val wav = file.readBytes()
            val started = SystemClock.elapsedRealtime()
            val row = JSONObject().put("take", file.nameWithoutExtension)
            try {
                when (val heard = controller.heardFromBytes(wav)) {
                    is VoiceResult.Heard -> {
                        row.put("transcript", heard.transcript)
                        when (val outcome = heard.outcome) {
                            is Outcome.Card -> {
                                row.put("outcome", "card").put("action", outcome.action.action).put("args", JSONObject(outcome.action.args))
                                controller.dismissQuietly(outcome.proposalId)
                            }
                            is Outcome.Question -> row.put("outcome", "question").put("action", "AnswerFromMemory")
                            Outcome.Nothing -> row.put("outcome", "nothing").put("action", "NoAction")
                            is Outcome.Refused -> row.put("outcome", "refused").put("action", "NoAction").put("reason", outcome.reason)
                            is Outcome.ModelUnavailable -> row.put("outcome", "unavailable").put("reason", outcome.reason)
                        }
                    }
                    VoiceResult.NothingHeard -> row.put("outcome", "nothing-heard")
                    is VoiceResult.Unavailable -> row.put("outcome", "unavailable").put("reason", heard.reason)
                }
            } catch (e: Exception) {
                row.put("outcome", "error").put("reason", e::class.java.simpleName)
            }
            row.put("ms", SystemClock.elapsedRealtime() - started)
            rows.put(row)
            out.writeText(JSONObject().put("complete", false).put("takes", rows).toString())
        }
        out.writeText(JSONObject().put("complete", true).put("takes", rows).toString())
    }
}
