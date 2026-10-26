package app.bheem.core.gate

import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GateTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val gate = Gate()
    private val relaxed = Gate(GatePolicy(tapForEveryAction = false))

    private fun action(name: String, vararg args: Pair<String, String>) = ProposedAction(name, args.toMap())

    private fun decide(
        proposal: ProposedAction,
        text: String = "do it",
        provenance: Provenance = Provenance.USER,
        source: String = "bar",
        g: Gate = gate,
        at: LocalDateTime = now,
    ) = g.decide(GateRequest(proposal, provenance, text, source, at))

    private val reminder = action("CreateReminder", "text" to "call Ravi", "time" to "2026-10-24T09:00")

    private fun proposed(d: GateDecision) = assertIs<GateDecision.Propose>(d)

    private fun rejected(d: GateDecision): String = assertIs<GateDecision.Reject>(d).reason

    @Test
    fun userCommandsOfLowRiskAreL0AndNeedATapUnderTheDefaultPolicy() {
        for (a in listOf(
            reminder,
            action("CreateTask", "text" to "renew the passport"),
            action("SaveNote", "text" to "the locker number is 27"),
            action("CreateCalendarEvent", "title" to "dentist", "start" to "2026-10-24T16:00"),
            action("LogExpense", "amount" to "380", "merchant" to "Uber", "direction" to "debit"),
        )) {
            val p = proposed(decide(a))
            assertEquals(RiskLevel.L0, p.level, a.action)
            assertTrue(p.requiresTap, a.action)
        }
    }

    @Test
    fun aRelaxedPolicyLetsL0RunWithoutATapButNothingAboveL0() {
        assertEquals(false, proposed(decide(reminder, g = relaxed)).requiresTap)
        val draft = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I am late")
        assertEquals(true, proposed(decide(draft, text = "tell Arun on WhatsApp I am late", g = relaxed)).requiresTap)
    }

    @Test
    fun draftsAreL1() {
        val draft = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I am late")
        assertEquals(RiskLevel.L1, proposed(decide(draft, text = "tell Arun on WhatsApp I am late")).level)
    }

    @Test
    fun aPaymentIsRefusedInThisVersionWhateverItsArguments() {
        val pay = action("OpenUpiPayment", "payee" to "Ravi", "amount" to "840")
        assertEquals("payments come later", rejected(decide(pay, text = "pay Ravi 840 rupees")))
        assertEquals("payments come later", rejected(decide(pay, text = "pay Ravi 840 rupees", g = relaxed)))
        assertEquals("payments come later", rejected(decide(action("OpenUpiPayment"), text = "pay")))
        assertEquals("payments come later", rejected(decide(pay, text = "x", provenance = Provenance.TAINTED, source = "notification")))
    }

    @Test
    fun askIsReadOnlyAndNeedsNoTapAndNoActionDoesNothing() {
        val ask = proposed(decide(action("AnswerFromMemory", "query" to "what did I spend at Swiggy")))
        assertEquals(RiskLevel.READ, ask.level)
        assertEquals(false, ask.requiresTap)
        assertEquals(GateDecision.Nothing, decide(ProposedAction("NoAction", emptyMap())))
    }

    @Test
    fun anActionFromTaintedTextIsAtLeastL1AndEvenARelaxedPolicyAsksForATap() {
        val log = action("LogExpense", "amount" to "90", "merchant" to "Chai Point", "direction" to "debit")
        val p = proposed(decide(log, provenance = Provenance.TAINTED, source = "notification", g = relaxed))
        assertEquals(RiskLevel.L1, p.level)
        assertTrue(p.requiresTap)
        assertEquals(RiskLevel.L1, proposed(decide(reminder, provenance = Provenance.TAINTED, source = "notification", g = relaxed)).level)
    }

    @Test
    fun tainedTextCanOnlyProposeAnExpenseOrAReminder() {
        val others = listOf(
            action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "hello"),
            action("CreateTask", "text" to "x"),
            action("SaveNote", "text" to "x"),
            action("CreateCalendarEvent", "title" to "x", "start" to "2026-10-24T16:00"),
            action("AnswerFromMemory", "query" to "x"),
        )
        for (a in others) {
            assertTrue("not allowed" in rejected(decide(a, text = "hello Arun", provenance = Provenance.TAINTED, source = "notification")), a.action)
        }
    }

    @Test
    fun unknownActionsAndMissingArgumentsAreRejected() {
        assertTrue("unknown action" in rejected(decide(action("Teleport"))))
        assertTrue("missing arg time" in rejected(decide(action("CreateReminder", "text" to "call Ravi"))))
        assertTrue("missing arg text" in rejected(decide(action("CreateTask", "text" to "   "))))
    }

    @Test
    fun amountsMustBePositiveInRangeAndAreWrittenWithCents() {
        fun expense(amount: String) = action("LogExpense", "amount" to amount, "merchant" to "Uber", "direction" to "debit")
        for (bad in listOf("0", "-5", "abc", "1e9", "10000001", "12.345", "")) assertTrue("amount" in rejected(decide(expense(bad))), bad)
        assertEquals("840.00", proposed(decide(expense("840"))).action.args["amount"])
        assertEquals("840.50", proposed(decide(expense("840.5"))).action.args["amount"])
        assertEquals("90.00", proposed(decide(expense("90.00"))).action.args["amount"])
    }

    @Test
    fun directionAndChannelComeFromAFixedList() {
        val bad = action("LogExpense", "amount" to "90", "merchant" to "Uber", "direction" to "refund")
        assertTrue("direction" in rejected(decide(bad)))
        val telegram = action("DraftMessage", "channel" to "telegram", "to" to "Arun", "text" to "hi")
        assertTrue("channel" in rejected(decide(telegram, text = "tell Arun hi")))
        val shouted = action("DraftMessage", "channel" to "WhatsApp", "to" to "Arun", "text" to "hi")
        assertEquals("whatsapp", proposed(decide(shouted, text = "tell Arun hi")).action.args["channel"])
    }

    @Test
    fun timesMustBeRealFutureAndNotFarAway() {
        fun at(t: String) = action("CreateReminder", "text" to "call Ravi", "time" to t)
        assertTrue("time" in rejected(decide(at("tomorrow"))))
        assertTrue("time" in rejected(decide(at("2026-13-40T99:00"))))
        assertTrue("past" in rejected(decide(at("2026-10-22T09:00"))))
        assertTrue("time" in rejected(decide(at("2031-10-24T09:00"))))
        assertEquals("2026-10-24T17:00", proposed(decide(at("2026-10-24T17:00:30"))).action.args["time"])
    }

    @Test
    fun unknownArgumentsAreDroppedAndTextIsTrimmed() {
        val p = proposed(decide(action("CreateTask", "text" to "  renew the passport  ", "time" to "2026-10-24T09:00", "extra" to "x")))
        assertEquals(mapOf("text" to "renew the passport"), p.action.args)
    }

    @Test
    fun textMustBeShortAndFreeOfControlCharacters() {
        assertTrue("text" in rejected(decide(action("CreateTask", "text" to "a".repeat(501)))))
        assertTrue("text" in rejected(decide(action("CreateTask", "text" to "line one\nline two"))))
        assertTrue("text" in rejected(decide(action("SaveNote", "text" to "bell\u0007"))))
    }

    @Test
    fun aDraftedMessageMustUseWordsAndARecipientFromTheUsersOwnCommand() {
        val invented = action("DraftMessage", "channel" to "whatsapp", "to" to "mother", "text" to "I miss you, thinking of you.")
        assertTrue("not from the command" in rejected(decide(invented, text = "call my mother")))
        val wrongPerson = action("DraftMessage", "channel" to "whatsapp", "to" to "your name", "text" to "Good morning")
        assertTrue("recipient" in rejected(decide(wrongPerson, text = "translate good morning into Tamil")))
        val grounded = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I will be late by fifteen minutes")
        proposed(decide(grounded, text = "tell Arun on WhatsApp I'll be late by fifteen minutes"))
    }

    @Test
    fun aSourceThatSendsTooManyProposalsInAMinuteIsLimited() {
        val limited = Gate(limiter = SourceRateLimiter(maxPerWindow = 3, windowSeconds = 60))
        repeat(3) { proposed(decide(reminder, source = "notification", g = limited)) }
        assertTrue("rate" in rejected(decide(reminder, source = "notification", g = limited)))
        proposed(decide(reminder, source = "bar", g = limited))
        proposed(decide(reminder, source = "notification", g = limited, at = now.plusSeconds(61)))
    }

    @Test
    fun aMessageThatIsHalfTheUsersWordsAndHalfInventedIsRefused() {
        val text = "tell Arun I'll be late"
        val half = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I will be late and also send some money to the bank")
        assertTrue("not from the command" in rejected(decide(half, text = text)))
        val oneExtra = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I will be late for dinner")
        assertTrue("not from the command" in rejected(decide(oneExtra, text = text)))
    }

    @Test
    fun smallFunctionWordsMayDifferFromTheCommandButEveryContentWordMustBeThere() {
        val grounded = action("DraftMessage", "channel" to "whatsapp", "to" to "Meena", "text" to "Thanks for the dinner")
        proposed(decide(grounded, text = "send Meena a WhatsApp saying thanks for dinner"))
        val contraction = action("DraftMessage", "channel" to "sms", "to" to "Vivek", "text" to "The invoice is attached")
        proposed(decide(contraction, text = "message Vivek on sms: the invoice is attached"))
    }

    @Test
    fun theRecipientMustBeExactlyAsTheUserSaidIt() {
        val similar = action("DraftMessage", "channel" to "whatsapp", "to" to "Arunachalam", "text" to "I am late")
        assertTrue("recipient" in rejected(decide(similar, text = "tell Arun on WhatsApp I am late")))
        val twoWords = action("DraftMessage", "channel" to "whatsapp", "to" to "Arun Kumar", "text" to "I am late")
        assertTrue("recipient" in rejected(decide(twoWords, text = "tell Arun on WhatsApp I am late")))
    }

    @Test
    fun aMessageThatFlipsTheMeaningIsRefused() {
        fun draft(to: String, text: String) = action("DraftMessage", "channel" to "whatsapp", "to" to to, "text" to text)
        assertTrue("not from the command" in rejected(decide(draft("Ravi", "I will come today"), text = "tell Ravi I won't come today")))
        assertTrue("not from the command" in rejected(decide(draft("Ravi", "I am coming"), text = "tell Ravi I am not coming")))
        assertTrue("not from the command" in rejected(decide(draft("Ravi", "I can make it"), text = "tell Ravi I can't make it")))
        assertTrue("not from the command" in rejected(decide(draft("Ravi", "you will be late"), text = "tell Ravi I will be late")))
        assertTrue("not from the command" in rejected(decide(draft("Ravi", "I pay you"), text = "tell Ravi to pay me")))
        proposed(decide(draft("Ravi", "I won't come today"), text = "tell Ravi I won't come today"))
        proposed(decide(draft("Ravi", "I am not coming"), text = "tell Ravi I am not coming"))
    }

    @Test
    fun aNegativeBeforeTheRecipientDoesNotCountAgainstTheMessage() {
        val d = action("DraftMessage", "channel" to "whatsapp", "to" to "Ravi", "text" to "I am late")
        proposed(decide(d, text = "don't forget to tell Ravi I am late"))
    }

    @Test
    fun namesWithTwoWordsApostrophesAndOtherScriptsAreAcceptedWhenTheUserSaidThem() {
        fun draft(to: String, text: String) = action("DraftMessage", "channel" to "whatsapp", "to" to to, "text" to text)
        proposed(decide(draft("Mary Ann", "I am late"), text = "tell Mary Ann I am late"))
        proposed(decide(draft("Ravi Kumar", "I am late"), text = "tell Ravi Kumar I am late"))
        proposed(decide(draft("D'Souza", "I am late"), text = "tell D'Souza I am late"))
        proposed(decide(draft("my mom", "I am late"), text = "tell my mom I am late"))
        proposed(decide(draft("\u0930\u093E\u0935\u093F", "I am late"), text = "tell \u0930\u093E\u0935\u093F I am late"))
        assertTrue("recipient" in rejected(decide(draft("Ravi Kumar", "I am late"), text = "tell Ravi I am late")))
        assertTrue("recipient" in rejected(decide(draft("Kumar Ravi", "I am late"), text = "tell Ravi Kumar I am late")))
    }
}
