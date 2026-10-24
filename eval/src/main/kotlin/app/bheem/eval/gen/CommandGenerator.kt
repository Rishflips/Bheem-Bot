package app.bheem.eval.gen

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import kotlin.random.Random

object CommandGenerator {
    private val langs = listOf("hi-latn", "en", "hi-deva")

    private class Day(val offset: Long, val hi: String, val en: String, val dv: String)
    private class Slot(val hour: Int, val hi: String, val en: String, val dv: String)
    private class Tri(val hi: String, val en: String, val dv: String) {
        fun of(lang: Int) = when (lang) { 0 -> hi; 1 -> en; else -> dv }
    }

    private val days = listOf(
        Day(0, "aaj", "today", "आज"),
        Day(1, "kal", "tomorrow", "कल"),
        Day(2, "parso", "the day after tomorrow", "परसों"),
    )
    private val slots = listOf(
        Slot(9, "subah 9 baje", "9 AM", "सुबह 9 बजे"),
        Slot(14, "dopahar 2 baje", "2 PM", "दोपहर 2 बजे"),
        Slot(18, "shaam 6 baje", "6 PM", "शाम 6 बजे"),
        Slot(21, "raat 9 baje", "9 PM", "रात 9 बजे"),
        Slot(6, "subah 6 baje", "6 AM", "सुबह 6 बजे"),
    )
    private val people = listOf("Arun", "Vivek", "Priya", "Karthik", "Anita", "Ravi", "Meena", "Suresh")
    private val merchants = listOf("Swiggy", "Zomato", "Murugan Idli", "Apollo Pharmacy", "Big Basket", "Uber", "Reliance Fresh", "Cafe Coffee Day")
    private val amounts = listOf(120, 175, 450, 840, 999, 1250, 2300, 5000)
    private val tasks = listOf(
        Tri("bijli ka bill bharna", "pay the electricity bill", "बिजली का बिल भरना"),
        Tri("passport renew karna", "renew the passport", "पासपोर्ट रिन्यू करना"),
        Tri("gas cylinder book karna", "book a gas cylinder", "गैस सिलेंडर बुक करना"),
        Tri("car ki service karwana", "get the car serviced", "गाड़ी की सर्विस करवाना"),
        Tri("school fees jama karna", "submit the school fees", "स्कूल की फीस जमा करना"),
        Tri("dawai ka stock check karna", "check the medicine stock", "दवाई का स्टॉक चेक करना"),
    )
    private val messages = listOf(
        Tri("main das minute mein pahunch raha hoon", "I will reach in ten minutes", "मैं दस मिनट में पहुँच रहा हूँ"),
        Tri("aaj raat khana mat banana", "do not cook dinner tonight", "आज रात खाना मत बनाना"),
        Tri("meeting ek ghante late shuru hogi", "the meeting will start an hour late", "मीटिंग एक घंटा देर से शुरू होगी"),
    )
    private val notes = listOf(
        Tri("wifi password router ke neeche likha hai", "the wifi password is written under the router", "वाईफाई पासवर्ड राउटर के नीचे लिखा है"),
        Tri("Ravi ka birthday 12 December ko hai", "Ravi's birthday is on 12 December", "रवि का जन्मदिन 12 दिसंबर को है"),
        Tri("scooter ki chaabi almirah mein rakhi hai", "the scooter key is kept in the cupboard", "स्कूटर की चाबी अलमारी में रखी है"),
        Tri("doctor ne subah khali pet jaanch bola hai", "the doctor asked for a fasting test in the morning", "डॉक्टर ने सुबह खाली पेट जांच बोली है"),
    )
    private val questions = listOf(
        Tri("pichle hafte khane pe kitna kharch hua", "how much did I spend on food last week", "पिछले हफ्ते खाने पर कितना खर्च हुआ"),
        Tri("is mahine Swiggy pe kitna gaya", "how much went to Swiggy this month", "इस महीने स्विगी पर कितना गया"),
        Tri("meri pending tasks kya hain", "what are my pending tasks", "मेरे पेंडिंग टास्क क्या हैं"),
    )
    private val smallTalk = listOf(
        "aaj mausam kaisa hai" to "hi-latn", "tum kaun ho" to "hi-latn", "what is the capital of France" to "en",
        "tell me a joke" to "en", "kya haal hai" to "hi-latn", "good morning" to "en",
        "dhanyavaad" to "hi-latn", "आप कैसे हैं" to "hi-deva", "sing me a song" to "en",
        "cricket ka score kya hai" to "hi-latn", "how are you" to "en", "आज का दिन कैसा रहेगा" to "hi-deva",
    )

    private val plan = linkedMapOf(
        "CreateReminder" to 69, "LogExpense" to 45, "CreateCalendarEvent" to 45, "DraftMessage" to 45,
        "OpenUpiPayment" to 45, "CreateTask" to 18, "SaveNote" to 12, "AnswerFromMemory" to 9, "NoAction" to 12,
    )

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private fun iso(day: Day, slot: Slot) =
        ReferenceClock.TODAY.plusDays(day.offset).atTime(slot.hour, 0).format(ReferenceClock.isoMinute)

    private fun pastToday(day: Day, slot: Slot) = day.offset == 0L && slot.hour <= ReferenceClock.NOW.hour

    fun generate(seed: Long = 20261023L): List<CommandCase> {
        val random = Random(seed)
        val out = mutableListOf<CommandCase>()
        val seen = mutableSetOf<String>()
        var n = 0
        for ((action, target) in plan) {
            if (action == "NoAction") {
                for ((text, lang) in smallTalk) {
                    seen += text
                    out += CommandCase("cmd-%03d".format(++n), lang, text, ExpectedAction("NoAction"))
                }
                continue
            }
            var made = 0
            var attempts = 0
            while (made < target) {
                check(++attempts < 20_000) { "cannot reach $target distinct $action commands" }
                val lang = made % 3
                val case = make(action, lang, random) ?: continue
                if (!seen.add(case.first)) continue
                out += CommandCase("cmd-%03d".format(++n), langs[lang], case.first, case.second)
                made++
            }
        }
        return out
    }

    private fun make(action: String, lang: Int, r: Random): Pair<String, ExpectedAction>? {
        val day = r.pick(days)
        val slot = r.pick(slots)
        val who = r.pick(people)
        val merchant = r.pick(merchants)
        val amount = r.pick(amounts)
        return when (action) {
            "CreateReminder" -> {
                if (pastToday(day, slot)) return null
                val (text, phrase) = when (lang) {
                    0 -> "${day.hi} ${slot.hi} $who ko call karna yaad dilana" to "$who ko call karna"
                    1 -> "remind me ${day.en} at ${slot.en} to call $who" to "call $who"
                    else -> "${day.dv} ${slot.dv} $who को फोन करना याद दिलाना" to "$who को फोन करना"
                }
                text to ExpectedAction(action, mapOf("text" to phrase, "time" to iso(day, slot)))
            }
            "CreateCalendarEvent" -> {
                if (pastToday(day, slot)) return null
                val (text, title) = when (lang) {
                    0 -> "${day.hi} ${slot.hi} $who ke saath meeting calendar mein daalo" to "$who ke saath meeting"
                    1 -> "schedule a meeting with $who ${day.en} at ${slot.en}" to "meeting with $who"
                    else -> "${day.dv} ${slot.dv} $who के साथ मीटिंग कैलेंडर में डालो" to "$who के साथ मीटिंग"
                }
                text to ExpectedAction(action, mapOf("title" to title, "start" to iso(day, slot)))
            }
            "LogExpense" -> {
                val text = when (lang) {
                    0 -> "$merchant mein $amount rupaye kharch hue"
                    1 -> "I spent $amount rupees at $merchant"
                    else -> "$merchant में $amount रुपये खर्च हुए"
                }
                text to ExpectedAction(action, mapOf("amount" to "$amount.00", "merchant" to merchant, "direction" to "debit"))
            }
            "OpenUpiPayment" -> {
                val text = when (lang) {
                    0 -> "$who ko $amount rupaye bhejo"
                    1 -> "pay $who $amount rupees"
                    else -> "$who को $amount रुपये भेजो"
                }
                text to ExpectedAction(action, mapOf("payee" to who, "amount" to "$amount"))
            }
            "DraftMessage" -> {
                val msg = r.pick(messages)
                val text = when (lang) {
                    0 -> "$who ko whatsapp pe bolo ki ${msg.hi}"
                    1 -> "message $who on WhatsApp that ${msg.en}"
                    else -> "$who को व्हाट्सएप पर बोलो कि ${msg.dv}"
                }
                text to ExpectedAction(action, mapOf("channel" to "whatsapp", "to" to who, "text" to msg.of(lang)))
            }
            "CreateTask" -> {
                val t = r.pick(tasks)
                val text = when (lang) { 0 -> "task add karo: ${t.hi}"; 1 -> "add a task to ${t.en}"; else -> "टास्क जोड़ो: ${t.dv}" }
                text to ExpectedAction(action, mapOf("text" to t.of(lang)))
            }
            "SaveNote" -> {
                val t = r.pick(notes)
                val text = when (lang) { 0 -> "note kar lo: ${t.hi}"; 1 -> "save a note: ${t.en}"; else -> "नोट कर लो: ${t.dv}" }
                text to ExpectedAction(action, mapOf("text" to t.of(lang)))
            }
            "AnswerFromMemory" -> {
                val q = r.pick(questions)
                q.of(lang) to ExpectedAction(action, mapOf("query" to "*"))
            }
            else -> error("unknown action $action")
        }
    }
}
