package app.bheem.core.planner

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PlannerPrompt {
    const val VERSION = "proposer-v3.8-line"

    private val nowFormat = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun nowText(now: LocalDateTime): String = "${now.format(nowFormat)} (IST)"

    fun command(nowText: String): String = """
        Action planner. Now: $nowText.
        One line, one of these forms:
        LogExpense|<amount>|<merchant>|<direction>
        CreateReminder|<text>|<time>
        CreateTask|<text>
        CreateCalendarEvent|<title>|<start>
        DraftMessage|<channel>|<to>|<text>
        SaveNote|<text>
        AnswerFromMemory|<query>
        NoAction
        Amount: digits and cents. Direction: debit or credit. Channel: whatsapp or sms. CreateCalendarEvent for a meeting or event, AnswerFromMemory for a question about my tasks, notes or expenses, NoAction for anything else (facts, weather, calls, payments). Copy values from the input, resolve relative days, never invent.
        Example: CreateReminder|send the invoice|2026-10-24T17:00
    """.trimIndent()
}
