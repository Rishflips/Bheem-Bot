package app.bheem.eval.gen

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction

object AssistantCommands {
    private fun reminder(text: String, what: String, time: String) = text to ExpectedAction("CreateReminder", mapOf("text" to what, "time" to time))
    private fun event(text: String, title: String, start: String) = text to ExpectedAction("CreateCalendarEvent", mapOf("title" to title, "start" to start))
    private fun task(text: String, what: String) = text to ExpectedAction("CreateTask", mapOf("text" to what))
    private fun note(text: String, what: String) = text to ExpectedAction("SaveNote", mapOf("text" to what))
    private fun message(text: String, to: String, what: String) =
        text to ExpectedAction("DraftMessage", mapOf("channel" to "whatsapp", "to" to to, "text" to what))
    private fun ask(text: String) = text to ExpectedAction("AnswerFromMemory", mapOf("query" to "*"))
    private fun spent(text: String, amount: String, merchant: String) =
        text to ExpectedAction("LogExpense", mapOf("amount" to amount, "merchant" to merchant, "direction" to "debit"))
    private fun pay(text: String, payee: String, amount: String) = text to ExpectedAction("OpenUpiPayment", mapOf("payee" to payee, "amount" to amount))
    private fun outOfScope(text: String) = text to ExpectedAction("NoAction")

    private val written = listOf(
        reminder("can you remind me to take my tablets at 9 tonight", "take my tablets", "2026-10-23T21:00"),
        reminder("don't let me forget the parcel pickup tomorrow at 10 AM", "parcel pickup", "2026-10-24T10:00"),
        reminder("ping me in two hours to check the oven", "check the oven", "2026-10-23T10:00"),
        reminder("set a reminder for next Monday at 8 AM to submit the timesheet", "submit the timesheet", "2026-10-26T08:00"),
        reminder("remind me tomorrow at 7 PM about Meena's recital", "Meena's recital", "2026-10-24T19:00"),
        reminder("I need a nudge at 6 PM today to buy milk", "buy milk", "2026-10-23T18:00"),
        event("put the dentist appointment on the calendar tomorrow at 4 PM", "dentist appointment", "2026-10-24T16:00"),
        event("book a call with Suresh next Monday at 11 AM", "call with Suresh", "2026-10-26T11:00"),
        event("add lunch with Priya to my calendar today at 1 PM", "lunch with Priya", "2026-10-23T13:00"),
        event("block tomorrow at 9 AM for the quarterly review", "quarterly review", "2026-10-24T09:00"),
        task("I have to renew the car insurance, add that to my to do list", "renew the car insurance"),
        task("add buy printer paper to my tasks", "buy printer paper"),
        task("put call the plumber on my list", "call the plumber"),
        task("remember to water the plants, add it as a task", "water the plants"),
        note("note down that the locker number is 27", "the locker number is 27"),
        note("jot this down: Anita prefers tea without sugar", "Anita prefers tea without sugar"),
        note("save a note that the plumber's visit is on Saturday", "the plumber's visit is on Saturday"),
        note("keep a note: I lent Ravi the blue umbrella", "I lent Ravi the blue umbrella"),
        message("tell Arun on WhatsApp I'll be late by fifteen minutes", "Arun", "I will be late by fifteen minutes"),
        message("send Meena a WhatsApp saying thanks for dinner", "Meena", "thanks for dinner"),
        message("text Karthik on WhatsApp to bring the charger", "Karthik", "bring the charger"),
        message("message Vivek on WhatsApp: the invoice is attached", "Vivek", "the invoice is attached"),
        ask("what did I spend at Swiggy last month"),
        ask("when is Ravi's birthday"),
        ask("which tasks did I add this week"),
        ask("what notes did I save about Anita"),
        ask("where did I keep the scooter key"),
        ask("what was the locker number I noted"),
        ask("how many tasks are still pending"),
        ask("show me everything I spent on travel this week"),
        spent("I paid 380 rupees to Uber just now", "380.00", "Uber"),
        spent("log 250 at Cafe Coffee Day", "250.00", "Cafe Coffee Day"),
        pay("send 1500 rupees to Suresh", "Suresh", "1500"),
        outOfScope("what's the weather like in Chennai"),
        outOfScope("explain how photosynthesis works"),
        outOfScope("translate good morning into Tamil"),
        outOfScope("play some music"),
        outOfScope("call my mother"),
    )

    fun cases(): List<CommandCase> =
        written.mapIndexed { i, (text, expected) -> CommandCase("ast-%03d".format(i + 1), "en", text, expected) }
}
