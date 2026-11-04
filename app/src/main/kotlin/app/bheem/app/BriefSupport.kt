package app.bheem.app

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.bheem.core.assistant.Turn
import app.bheem.core.brief.Brief
import app.bheem.core.brief.BriefInput
import app.bheem.core.brief.BriefResult
import app.bheem.core.brief.BriefTime
import app.bheem.core.brief.WaitingCard
import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedStatus
import app.bheem.core.feed.todayRows
import app.bheem.core.gate.Provenance
import app.bheem.core.memory.RecordKind
import app.bheem.storage.Storage
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Builds the brief's input from the owner's own records only: a record that did not come from a USER proposal never reaches it. */
object BriefInputs {
    fun from(storage: Storage, now: LocalDateTime): BriefInput {
        val trusted = { id: String -> storage.proposals.get(id)?.provenance == Provenance.USER }
        val reminders = (storage.reminders.scheduled() + storage.reminders.missed()).filter { trusted(it.proposalId) }
        val tasks = storage.memory.ofKind(RecordKind.TASK).filter { trusted(it.id) && it.doneAt == null }
        val waiting = Feed.items(storage.feed.todayRows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS)
            .filter { it.status == FeedStatus.PENDING }
            .map { WaitingCard(it.id, "${it.title}: ${it.detail}", it.createdAt) }
        return BriefInput(reminders, tasks, waiting)
    }

    private const val FEED_ROWS = 100
}

object BriefService {
    /** Composes today's brief and keeps it in the thread. A brief already kept for today is returned as it is unless [force]. */
    fun composeAndStore(storage: Storage, now: LocalDateTime, force: Boolean = false): BriefResult? {
        val stored = storage.turns.recent(RECENT).any { it is Turn.Brief && it.at.toLocalDate() == now.toLocalDate() }
        if (stored && !force) return null
        val result = Brief.compose(now, ZoneId.systemDefault(), BriefInputs.from(storage, now))
        storage.turns.add(Turn.Brief(result.text, result.citations, now))
        return result
    }

    private const val RECENT = 30
}

object BriefAlarm {
    private const val REQUEST = 7301
    private const val DEBUG_FILE = "debug-brief-minutes"

    fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val minutes = runCatching { File(context.noBackupFilesDir, DEBUG_FILE).readText().trim().toLong() }.getOrNull()
        val at = minutes?.let { now.plusMinutes(it) } ?: BriefTime.next(now)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), intent(context))
    }

    /** A developer sets the file with adb to fire the alarm in a few minutes and to compose a brief even when today's exists. */
    fun isDebug(context: Context): Boolean = File(context.noBackupFilesDir, DEBUG_FILE).exists()

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, REQUEST, Intent(context, BriefReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class BriefReceiver : AsyncReceiver() {
    override suspend fun handle(context: Context, intent: Intent) {
        val now = LocalDateTime.now()
        BriefAlarm.schedule(context, now)
        val result = BriefService.composeAndStore(SharedStorage.get(context), now, force = BriefAlarm.isDebug(context)) ?: return
        BriefNotifications.post(context, result.headline)
    }
}

object BriefNotifications {
    private const val ID = 3

    /** Only the counts go in the notification: the text of the brief stays in the app, and the lock screen shows a plain line. */
    fun post(context: Context, headline: String) {
        Notifications.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java).setData(Uri.parse("bheem-brief")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, Notifications.BRIEF)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(headline)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, Notifications.BRIEF)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(context.getString(R.string.notification_brief_hidden))
                    .build(),
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java).notify(ID, notification)
    }
}
