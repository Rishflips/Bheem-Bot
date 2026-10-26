package app.bheem.core.reminder

import java.time.Instant
import java.time.ZoneId

enum class ReminderState { SCHEDULED, FIRED, MISSED, MISSED_SEEN, CANCELLED }

data class Reminder(
    val id: Long,
    val proposalId: String,
    val text: String,
    val fireAtEpochMs: Long,
    val zoneId: String,
    val state: ReminderState,
)

interface ReminderStore {
    fun add(proposalId: String, text: String, fireAtEpochMs: Long, zoneId: String): Reminder

    fun byProposal(proposalId: String): Reminder?

    fun get(id: Long): Reminder?

    fun scheduled(): List<Reminder>

    fun missed(): List<Reminder>

    fun transition(id: Long, from: ReminderState, to: ReminderState): Boolean

    fun transitionAll(from: ReminderState, to: ReminderState)

    fun reschedule(id: Long, fireAtEpochMs: Long, zoneId: String)
}

class InMemoryReminderStore : ReminderStore {
    private val rows = linkedMapOf<Long, Reminder>()
    private var next = 1L

    @Synchronized
    override fun add(proposalId: String, text: String, fireAtEpochMs: Long, zoneId: String): Reminder {
        val row = Reminder(next++, proposalId, text, fireAtEpochMs, zoneId, ReminderState.SCHEDULED)
        rows[row.id] = row
        return row
    }

    @Synchronized
    override fun byProposal(proposalId: String): Reminder? = rows.values.lastOrNull { it.proposalId == proposalId }

    @Synchronized
    override fun get(id: Long): Reminder? = rows[id]

    @Synchronized
    override fun scheduled(): List<Reminder> = inState(ReminderState.SCHEDULED)

    @Synchronized
    override fun missed(): List<Reminder> = inState(ReminderState.MISSED)

    private fun inState(state: ReminderState) = rows.values.filter { it.state == state }.sortedWith(compareBy({ it.fireAtEpochMs }, { it.id }))

    @Synchronized
    override fun transition(id: Long, from: ReminderState, to: ReminderState): Boolean {
        val row = rows[id] ?: return false
        if (row.state != from) return false
        rows[id] = row.copy(state = to)
        return true
    }

    @Synchronized
    override fun transitionAll(from: ReminderState, to: ReminderState) {
        rows.replaceAll { _, row -> if (row.state == from) row.copy(state = to) else row }
    }

    @Synchronized
    override fun reschedule(id: Long, fireAtEpochMs: Long, zoneId: String) {
        val row = rows[id] ?: return
        if (row.state == ReminderState.SCHEDULED) rows[id] = row.copy(fireAtEpochMs = fireAtEpochMs, zoneId = zoneId)
    }
}

enum class AlarmKind { Exact, Inexact }

interface AlarmPort {
    fun canScheduleExact(): Boolean

    fun arm(reminder: Reminder, kind: AlarmKind)

    fun cancel(id: Long)
}

data class Scheduled(val reminder: Reminder, val exact: Boolean, val note: String?)

enum class RearmReason { Boot, Other }

data class RearmReport(val rearmed: List<Reminder>, val missed: List<Reminder>, val failed: List<Reminder> = emptyList())

class ReminderScheduler(private val store: ReminderStore, private val alarms: AlarmPort) {
    fun schedule(proposalId: String, text: String, fireAtEpochMs: Long, zoneId: String): Scheduled {
        val existing = store.byProposal(proposalId)?.takeIf { it.state == ReminderState.SCHEDULED }
        val row = existing ?: store.add(proposalId, text, fireAtEpochMs, zoneId)
        val exact = alarms.canScheduleExact()
        var usedExact = exact
        try {
            try {
                alarms.arm(row, if (exact) AlarmKind.Exact else AlarmKind.Inexact)
            } catch (e: SecurityException) {
                if (!exact) throw e
                usedExact = false
                alarms.arm(row, AlarmKind.Inexact)
            }
        } catch (e: Exception) {
            alarms.cancel(row.id)
            store.transition(row.id, ReminderState.SCHEDULED, ReminderState.CANCELLED)
            throw e
        }
        return Scheduled(row, usedExact, if (usedExact) null else INEXACT_NOTE)
    }

    fun cancel(id: Long): Boolean {
        alarms.cancel(id)
        if (store.transition(id, ReminderState.SCHEDULED, ReminderState.CANCELLED)) return true
        return store.get(id)?.state == ReminderState.CANCELLED
    }

    fun cancelForProposal(proposalId: String): Boolean {
        val row = store.byProposal(proposalId) ?: return false
        return cancel(row.id)
    }

    fun cancelAll() {
        for (row in store.scheduled()) cancel(row.id)
    }

    fun fired(id: Long): Reminder? {
        if (!store.transition(id, ReminderState.SCHEDULED, ReminderState.FIRED)) return null
        return store.get(id)
    }

    fun unseenMissed(): List<Reminder> = store.missed()

    fun acknowledgeMissed() {
        store.transitionAll(ReminderState.MISSED, ReminderState.MISSED_SEEN)
    }

    fun rearm(nowMs: Long, zone: ZoneId, reason: RearmReason = RearmReason.Other): RearmReport {
        val rearmed = mutableListOf<Reminder>()
        val missed = mutableListOf<Reminder>()
        val failed = mutableListOf<Reminder>()
        var firedAtOnce = 0
        val exact = alarms.canScheduleExact()
        for (row in store.scheduled()) {
            val current = keepWallClock(row, zone)
            val overdue = nowMs - current.fireAtEpochMs
            val limit = if (reason == RearmReason.Boot) GRACE_MS else STALE_MS
            val tooLate = overdue >= limit || (overdue > 0 && firedAtOnce >= MAX_AT_ONCE)
            if (tooLate) {
                if (store.transition(row.id, ReminderState.SCHEDULED, ReminderState.MISSED)) missed += current
                continue
            }
            try {
                alarms.arm(current, if (exact) AlarmKind.Exact else AlarmKind.Inexact)
                rearmed += current
                if (overdue > 0) firedAtOnce++
            } catch (e: Exception) {
                failed += current
            }
        }
        return RearmReport(rearmed, missed, failed)
    }

    private fun keepWallClock(row: Reminder, zone: ZoneId): Reminder {
        if (row.zoneId == zone.id) return row
        val wall = Instant.ofEpochMilli(row.fireAtEpochMs).atZone(ZoneId.of(row.zoneId)).toLocalDateTime()
        val moved = wall.atZone(zone).toInstant().toEpochMilli()
        store.reschedule(row.id, moved, zone.id)
        return row.copy(fireAtEpochMs = moved, zoneId = zone.id)
    }

    companion object {
        const val GRACE_MS = 120_000L
        const val STALE_MS = 24 * 3_600_000L
        const val MAX_AT_ONCE = 10
        const val INEXACT_NOTE = "Exact alarms are off for Bheem, so this reminder may arrive late. Allow exact alarms in settings."
    }
}

object RearmTriggers {
    fun reasonFor(action: String?): RearmReason? = when (action) {
        "android.intent.action.BOOT_COMPLETED" -> RearmReason.Boot
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        -> RearmReason.Other
        else -> null
    }
}

data class Notice(val title: String, val text: String)

object CatchUp {
    private const val LISTED = 3

    fun notice(missed: List<Reminder>): Notice {
        val newestFirst = missed.sortedByDescending { it.fireAtEpochMs }
        if (newestFirst.size == 1) return Notice("You missed a reminder", newestFirst.single().text)
        val listed = newestFirst.take(LISTED).joinToString(", ") { it.text }
        val rest = newestFirst.size - LISTED
        return Notice("You missed ${newestFirst.size} reminders", if (rest > 0) "$listed and $rest more" else listed)
    }
}

enum class ReminderIssue { NotificationsOff, ExactAlarmsOff, BatteryRestricted }

object ReminderHealth {
    /** Reminder problems are shown only once the person has a reminder, so no card asks for something before its benefit is clear. */
    fun shown(issues: List<ReminderIssue>, hasReminders: Boolean): List<ReminderIssue> = if (hasReminders) issues else emptyList()

    fun issues(notificationsOn: Boolean, exactOn: Boolean, ignoringBatteryOptimizations: Boolean): List<ReminderIssue> =
        listOfNotNull(
            ReminderIssue.NotificationsOff.takeIf { !notificationsOn },
            ReminderIssue.ExactAlarmsOff.takeIf { !exactOn },
            ReminderIssue.BatteryRestricted.takeIf { !ignoringBatteryOptimizations },
        )
}

object OemHint {
    fun text(manufacturer: String): String = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" -> "On Xiaomi phones, turn on Autostart for Bheem and set its battery saver to No restrictions, or reminders can be skipped."
        "samsung" -> "On Samsung phones, add Bheem to Never sleeping apps in Battery settings, or reminders can be skipped."
        "realme", "oppo", "oneplus" -> "On this phone, allow Bheem to run in the background and turn off battery optimisation for it, or reminders can be skipped."
        else -> "Some phones stop background apps to save battery. Allow Bheem to run without battery limits so that reminders arrive on time."
    }
}
