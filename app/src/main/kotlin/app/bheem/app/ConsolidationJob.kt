package app.bheem.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Once a day, only while the phone is charging and idle, Bheem reads what the owner said that day and stores up to five guesses about lasting facts.
 * A guess is never used until the owner confirms it on the What Bheem knows screen. If anything goes wrong the job ends and tries again tomorrow.
 */
class ConsolidationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as BheemApp
        running = scope.launch {
            val failed = runCatching {
                val ready = withTimeoutOrNull(READY_WAIT_MS) { app.session.first { it is Session.Ready } } as? Session.Ready
                val count = ready?.controller?.learnFromToday() ?: 0
                Log.i("BheemLearn", "overnight guesses stored: $count")
            }.isFailure
            jobFinished(params, false)
            if (failed) Log.i("BheemLearn", "overnight learning failed, trying again tomorrow")
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    companion object {
        private const val JOB_ID = 7302
        private const val READY_WAIT_MS = 120_000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, ConsolidationJob::class.java))
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(24))
                .build()
            scheduler.schedule(info)
        }
    }
}
