package app.bheem.eval.gen

import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.data.NotificationCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

object NotificationGenerator {
    private const val SMS = "com.google.android.apps.messaging"
    private const val PHONEPE = "com.phonepe.app"

    private val merchants = listOf("Murugan Idli", "Swiggy", "Zomato", "Apollo Pharmacy", "Big Basket", "Reliance Fresh", "Cafe Coffee Day", "Uber")
    private val people = listOf("Arun Kumar", "Priya Nair", "Vivek Rao", "Meena Iyer", "Suresh Babu", "Anita Shah")
    private val billers = listOf("Airtel", "Jio", "Vi", "Tata Power", "BESCOM")
    private val amounts = listOf(60, 120, 450, 840, 999, 1250, 2140, 12500, 48000, 125000)
    private val billAmounts = listOf(299, 599, 1850, 2140, 4310)
    private val shortDates = listOf("dd-MM-yy", "dd/MM/yy", "ddMMMyy")

    fun indian(n: Int): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val head = s.dropLast(3)
        val groups = head.reversed().chunked(2).joinToString(",").reversed()
        return "$groups,${s.takeLast(3)}"
    }

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private fun fmt(date: LocalDate, pattern: String) =
        date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

    fun generate(seed: Long = 20261024L): List<NotificationCase> {
        val r = Random(seed)
        val out = mutableListOf<NotificationCase>()
        val seen = mutableSetOf<String>()
        var n = 0

        fun add(source: String, text: String, expected: ExpectedAction): Boolean {
            if (!seen.add(text)) return false
            out += NotificationCase("ntf-%03d".format(++n), source, text, expected)
            return true
        }

        fun digits(count: Int) = (1..count).joinToString("") { r.nextInt(10).toString() }

        var made = 0
        while (made < 120) {
            val amount = r.pick(amounts)
            val shown = indian(amount)
            val date = fmt(ReferenceClock.TODAY.minusDays(r.nextLong(0, 3)), r.pick(shortDates))
            val last4 = digits(4)
            val ref = digits(12)
            val merchant = r.pick(merchants)
            val upper = merchant.uppercase()
            val vpa = merchant.lowercase().replace(' ', '.') + "@ybl"
            val debit = mapOf("amount" to "$amount.00", "merchant" to merchant, "direction" to "debit")
            val ok = when (made % 4) {
                0 -> add(SMS, "Rs.$shown.00 debited from A/c XX$last4 on $date to VPA $vpa (UPI Ref No $ref). If not done by you, report immediately.", ExpectedAction("LogExpense", debit))
                1 -> add(SMS, "Sent Rs.$shown.00 From Bank A/C *$last4 To $upper On $date Ref $ref", ExpectedAction("LogExpense", debit))
                2 -> add(SMS, "Dear Customer, INR $shown.00 spent on Card XX$last4 at $upper on $date. Avl Lmt INR 85,000.00", ExpectedAction("LogExpense", debit))
                else -> {
                    val person = r.pick(people)
                    add(PHONEPE, "Your a/c no. XX$last4 is credited with Rs.$shown.00 on $date by ${person.uppercase()} (UPI Ref $ref).",
                        ExpectedAction("LogExpense", mapOf("amount" to "$amount.00", "merchant" to person, "direction" to "credit")))
                }
            }
            if (ok) made++
        }

        made = 0
        val dueFormats = listOf("dd-MM-yyyy", "dd MMM yyyy", "dd/MM/yy")
        while (made < 40) {
            val amount = r.pick(billAmounts)
            val due = ReferenceClock.TODAY.plusDays(r.nextLong(2, 14))
            val dueText = fmt(due, r.pick(dueFormats))
            val time = due.atTime(9, 0).format(ReferenceClock.isoMinute)
            val ok = if (made % 2 == 0) {
                add(SMS, "Your electricity bill of Rs.${indian(amount)} for consumer no. ${digits(8)} is due on $dueText. Pay now to avoid late fee.",
                    ExpectedAction("CreateReminder", mapOf("text" to "electricity bill", "time" to time)))
            } else {
                val biller = r.pick(billers)
                add(SMS, "Reminder: your $biller postpaid bill Rs.${indian(amount)} is due on $dueText.",
                    ExpectedAction("CreateReminder", mapOf("text" to "$biller bill", "time" to time)))
            }
            if (ok) made++
        }

        made = 0
        while (made < 40) {
            val text = when (made % 4) {
                0 -> "Your OTP is ${digits(6)}. Do not share it with anyone. Valid for 10 minutes."
                1 -> "Get ${r.nextInt(5, 30)}% cashback on your next recharge. Offer ends ${fmt(ReferenceClock.TODAY.plusDays(r.nextLong(1, 10)), "dd MMM")}."
                2 -> "Avl Bal in A/c XX${digits(4)} is Rs.${indian(r.nextInt(1000, 90000))}.00 as on ${fmt(ReferenceClock.TODAY, "dd-MM-yy")}."
                else -> "Your ${r.pick(merchants)} order #${digits(7)} is out for delivery and arrives in ${r.nextInt(10, 40)} minutes."
            }
            if (add(SMS, text, ExpectedAction("NoAction"))) made++
        }
        return out
    }
}
