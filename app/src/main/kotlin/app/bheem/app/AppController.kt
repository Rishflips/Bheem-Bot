package app.bheem.app

import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedSections
import app.bheem.core.feed.todayRows
import app.bheem.core.privacy.Diagnostics
import app.bheem.core.feed.LedgerLine
import app.bheem.core.feed.LedgerLines
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.assistant.Clarifier
import app.bheem.core.assistant.Interview
import app.bheem.core.assistant.OfferCheck
import app.bheem.core.memory.MemoryBlock
import app.bheem.core.memory.InterviewFactExtractor
import app.bheem.core.memory.MemoryExtractor
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.device.DeviceQuestions
import app.bheem.core.assistant.FollowUp
import app.bheem.core.assistant.PlanRunner
import app.bheem.core.assistant.Proactive
import app.bheem.core.assistant.ProactiveInput
import app.bheem.core.assistant.ProactiveState
import app.bheem.core.assistant.ProactiveStateCodec
import app.bheem.core.assistant.SpeakMode
import app.bheem.core.assistant.SpeechPolicy
import app.bheem.core.assistant.SpeechSituation
import app.bheem.core.assistant.SpokenText
import app.bheem.core.assistant.Thread
import app.bheem.core.assistant.ThreadEntry
import app.bheem.core.assistant.ThreadLayout
import app.bheem.core.assistant.ToolResult
import app.bheem.core.brief.BriefTime
import app.bheem.core.memory.RecordKind
import app.bheem.core.gate.Provenance
import app.bheem.core.assistant.Turn
import app.bheem.core.assistant.Nudge
import app.bheem.core.pipeline.Outcome
import app.bheem.core.speech.TalkSession
import app.bheem.core.speech.VoiceResult
import app.bheem.core.speech.VoiceState
import app.bheem.core.proposal.ApprovalResult
import android.os.SystemClock
import android.util.Log
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineExceptionHandler
import app.bheem.models.host.ModelState
import app.bheem.models.host.SupplyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

const val LEDGER_LIMIT = 300

class AppController(private val graph: AppGraph) {
    private val failures = CoroutineExceptionHandler { _, error ->
        _message.value = "Something went wrong: ${error.message ?: error::class.java.simpleName}."
        _busy.value = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + failures)
    private val _sections = MutableStateFlow(FeedSections(emptyList(), emptyList(), emptyList()))
    private val _ledger = MutableStateFlow<List<LedgerLine>?>(null)
    private val _diagnostics = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)
    private val thread by lazy { Thread(store = graph.storage.turns) }
    private val sensor by lazy { AndroidDeviceSensor(graph.appContext) }
    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    private val _doneTasks = MutableStateFlow<Set<String>>(emptySet())
    private val _partial = MutableStateFlow<String?>(null)
    private var speakerInstance: Speaker? = null
    private val _speaking = MutableStateFlow(false)
    private val _reading = MutableStateFlow<String?>(null)
    private val _speechAvailable = MutableStateFlow(true)
    private val _engineName = MutableStateFlow<String?>(null)
    private val _speakMode = MutableStateFlow(SpeakMode.VOICE)

    @Volatile
    private var lastInputSpoken = false

    val turns: StateFlow<List<Turn>> = _turns
    val doneTasks: StateFlow<Set<String>> = _doneTasks
    val speakMode: StateFlow<SpeakMode> = _speakMode
    val speaking: StateFlow<Boolean> = _speaking

    /** The text of the bubble being read aloud, or null. */
    val reading: StateFlow<String?> = _reading
    val speechAvailable: StateFlow<Boolean> = _speechAvailable
    val engineName: StateFlow<String?> = _engineName

    private fun preferences() = graph.appContext.getSharedPreferences(MainActivity.PREFERENCES, android.content.Context.MODE_PRIVATE)

    /** The speech engine is only created when something is first spoken, so a phone that never speaks never binds one. */
    @Synchronized
    private fun speaker(): Speaker = speakerInstance ?: Speaker(
        graph.appContext,
        onSpeaking = { on ->
            _speaking.value = on
            if (!on) _reading.value = null
        },
        onAvailable = { _speechAvailable.value = it },
        onEngine = { _engineName.value = it },
    ).also { speakerInstance = it }

    private fun stopSpeaking() {
        speakerInstance?.stop()
        _reading.value = null
    }

    fun setSpeakMode(mode: SpeakMode) {
        _speakMode.value = mode
        scope.launch(Dispatchers.IO) { preferences().edit().putString(SPEAK_KEY, mode.name).apply() }
        if (mode == SpeakMode.OFF) stopSpeaking()
    }

    /** Reads one bubble aloud; pressing the button of the bubble being read stops it. */
    fun readAloud(text: String) {
        if (_reading.value == text) {
            stopSpeaking()
            return
        }
        val spoken = SpokenText.of(text) ?: return
        _reading.value = text
        speaker().speak(spoken)
    }

    private fun situation(): SpeechSituation {
        val context = graph.appContext
        val audioMode = context.getSystemService(android.media.AudioManager::class.java).mode
        return SpeechSituation(
            inCall = audioMode == android.media.AudioManager.MODE_IN_CALL || audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION,
            screenReaderOn = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled,
            locked = context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked,
        )
    }

    private fun maybeSpeak(turn: Turn) {
        val text = when (turn) {
            is Turn.Answer -> turn.text
            is Turn.Chat -> turn.text
            is Turn.Notice -> turn.text
            else -> return
        }
        if (!SpeechPolicy.shouldSpeak(_speakMode.value, lastInputSpoken, turn, situation())) return
        readAloud(text)
    }

    /** The model's chat answer while it is being written; null when no chat answer is in progress. */
    val partial: StateFlow<String?> = _partial
    val sections: StateFlow<FeedSections> = _sections

    /** The whole conversation, cards under the turns that made them. */
    val entries: StateFlow<List<ThreadEntry>> = combine(_turns, _sections) { turns, sections ->
        ThreadLayout.build(turns, sections.needsYou + sections.upcoming + sections.recent)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val ledger: StateFlow<List<LedgerLine>?> = _ledger
    val diagnostics: StateFlow<String?> = _diagnostics
    val message: StateFlow<String?> = _message
    /** Busy while a command runs or the model is writing a chat answer. */
    val busy: StateFlow<Boolean> = combine(_busy, _partial) { working, writing -> working || writing != null }.stateIn(scope, SharingStarted.Eagerly, false)
    val model: StateFlow<ModelState> = graph.modelHost.state
    val supply: StateFlow<SupplyState> = graph.supply.state
    val reminderServices: ReminderServices = graph.reminderServices
    private val _lastLoadSeconds = MutableStateFlow<Long?>(null)
    val lastLoadSeconds: StateFlow<Long?> = _lastLoadSeconds

    private val _draft = MutableStateFlow<String?>(null)
    private val talk = TalkSession(
        VoiceRecorder(), graph.voice, scope, _busy, { LocalDateTime.now() }, { _message.value = it }, ::voiceMessage, SystemClock::elapsedRealtime,
    ) { millis, audioMillis, result ->
        val speechMillis = (result as? VoiceResult.Heard)?.speechMillis ?: -1L
        Log.i("BheemVoice", "release to result: $millis ms, $audioMillis ms of audio, speech step $speechMillis ms")
    }

    val voice: StateFlow<VoiceState> = talk.state
    val draft: StateFlow<String?> = _draft

    private var submitJob: Job? = null
    private var chatJob: Job? = null

    @Volatile
    private var interview: Interview? = null

    @Volatile
    private var pendingGapQuestion: Int? = null

    @Volatile
    private var nudgeThisForeground = false

    init {
        scope.launch { _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds } }
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { SpeakMode.valueOf(preferences().getString(SPEAK_KEY, null).orEmpty()) }.getOrNull()?.let { _speakMode.value = it }
                _turns.value = thread.turns()
                BriefAlarm.schedule(graph.appContext)
                if (BriefTime.isDue(LocalDateTime.now())) BriefService.composeAndStore(graph.storage, LocalDateTime.now())
            }
            graph.storage.changes.onStart { emit(Unit) }.collectLatest {
                thread.reload()
                _turns.value = thread.turns()
                _memory.value = withContext(Dispatchers.IO) { (graph.memory.ofKind(RecordKind.FACT) + graph.memory.ofKind(RecordKind.GUESS)).sortedByDescending { it.createdAt } }
                _doneTasks.value = withContext(Dispatchers.IO) { graph.memory.ofKind(RecordKind.TASK).filter { it.doneAt != null }.map { it.id }.toSet() }
                maybeStartInterview()
                showFeed()
            }
        }
        scope.launch {
            graph.modelHost.state.collect { state ->
                if (state is ModelState.Ready) _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds }
            }
        }
    }

    private suspend fun showFeed() {
        while (true) {
            val now = LocalDateTime.now()
            val items = withContext(Dispatchers.Default) { Feed.items(graph.feed.todayRows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }
            _sections.value = FeedSections.of(items, now)
            if (items.none { it.canUndo }) return
            delay((AppGraph.UNDO_WINDOW_SECONDS + 1) * 1000)
        }
    }

    fun submit(text: String) {
        if (text.isBlank() || busy.value) return
        stopSpeaking()
        lastInputSpoken = false
        submitJob = scope.launch {
            _busy.value = true
            _message.value = null
            try {
                val now = LocalDateTime.now()
                val typed = text.trim()
                val history = _turns.value
                add(Turn.User(typed, spoken = false, now))
                val session = interview
                val gap = pendingGapQuestion
                when {
                    startsInterview(typed) -> {
                        pendingGapQuestion = null
                        beginInterview(now)
                    }
                    session != null && session.isActive() -> answerInterview(session, typed, now, history)
                    gap != null -> {
                        pendingGapQuestion = null
                        pendingClarify = null
                        offer = null
                        answerGap(gap, typed, now)
                    }
                    else -> {
                        val waiting = pendingClarify
                        pendingClarify = null
                        val confirming = FollowUp.isConfirmation(typed)
                        val offered = offer
                        offer = null
                        val chat = DeviceQuestions.answer(typed, sensor.read())
                        when {
                            chat != null -> add(Turn.Answer(chat, emptyList(), now))
                            confirming -> confirm(offered, now)
                            waiting != null && Clarifier.combine(waiting, typed) != null -> handle(Clarifier.combine(waiting, typed)!!, now, history)
                            else -> handle(typed, now, history)
                        }
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun startsInterview(text: String): Boolean =
        text.trim().trimEnd('.', '!', '?', ' ').lowercase() in INTERVIEW_START

    /** One nudge at most per foreground: the app open and each return to the chat screen ask once. */
    fun onChatOpened() = requestNudge()

    fun onChatResumed() = requestNudge()

    fun onChatPaused() {
        nudgeThisForeground = false
    }

    private fun requestNudge() {
        if (nudgeThisForeground) return
        nudgeThisForeground = true
        scope.launch { maybeNudge() }
    }

    private suspend fun maybeNudge() {
        if (interview?.isActive() == true) return
        if (graph.memory.ofKind(RecordKind.FACT).isEmpty() && !preferences().getBoolean(INTERVIEW_DONE, false)) return
        val now = LocalDateTime.now()
        val state = proactiveState()
        val input = withContext(Dispatchers.IO) { proactiveInput() }
        val nudge = Proactive.next(now, input, state) ?: return
        saveProactiveState(state.record(nudge, now))
        pendingGapQuestion = if (nudge.kind == Nudge.Kind.GAP) nudge.questionIndex else null
        add(Turn.Nudge(nudge.text, now))
    }

    private fun proactiveInput(): ProactiveInput {
        val trusted = { id: String -> graph.proposals.get(id)?.provenance == Provenance.USER }
        val facts = graph.memory.ofKind(RecordKind.FACT).map { it.text }
        return ProactiveInput(
            reminders = graph.storage.reminders.scheduled().filter { trusted(it.proposalId) },
            tasks = graph.memory.ofKind(RecordKind.TASK).filter { trusted(it.id) },
            facts = facts,
            keptQuestions = Proactive.answeredQuestions(facts),
        )
    }

    private fun proactiveState(): ProactiveState =
        ProactiveStateCodec.decode(preferences().getString(PROACTIVE_STATE, "").orEmpty())

    private fun saveProactiveState(state: ProactiveState) {
        preferences().edit().putString(PROACTIVE_STATE, ProactiveStateCodec.encode(state)).apply()
    }

    /** The owner's next message answers the one question Bheem just asked; it becomes one ordinary fact card. */
    private suspend fun answerGap(index: Int, text: String, now: LocalDateTime) {
        val question = Interview.QUESTIONS.getOrNull(index) ?: return
        val answer = text.trim()
        if (answer.isEmpty() || answer.lowercase() in GAP_SKIP) return
        val pair = question to answer
        val existing = withContext(Dispatchers.IO) {
            (graph.memory.ofKind(RecordKind.FACT) + graph.memory.ofKind(RecordKind.GUESS)).map { it.text }.toSet()
        }
        val facts = try {
            InterviewFactExtractor(graph.modelHost).facts(listOf(pair), existing)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Interview.fallbackFacts(listOf(pair))
        }
        val candidate = (facts.ifEmpty { Interview.fallbackFacts(listOf(pair)) }).firstOrNull() ?: return
        val plan = graph.pipeline.planFacts(listOf(candidate), now)
        if (plan.cards.isNotEmpty()) record(plan, now, graph.appContext.getString(R.string.interview_plan_title))
    }

    /** Starts the first-chat interview, or starts it again from Settings, at question one. */
    fun startInterview() {
        lastInputSpoken = false
        scope.launch { beginInterview(LocalDateTime.now()) }
    }

    private fun beginInterview(now: LocalDateTime) {
        val session = Interview().also { it.start() }
        interview = session
        askInterview(session, now)
    }

    private fun askInterview(session: Interview, now: LocalDateTime) {
        session.currentQuestion()?.let { add(Turn.Notice(it, now)) }
    }

    private fun maybeStartInterview() {
        if (interview?.isActive() == true) return
        if (preferences().getBoolean(INTERVIEW_DONE, false)) return
        if (_memory.value.any { it.kind == RecordKind.FACT }) return
        beginInterview(LocalDateTime.now())
    }

    private suspend fun answerInterview(session: Interview, text: String, now: LocalDateTime, history: List<Turn>) {
        when (session.answer(text)) {
            Interview.Result.COMMAND -> {
                handle(text, now, history)
                askInterview(session, now)
            }
            Interview.Result.ANSWERED, Interview.Result.SKIPPED ->
                if (session.isDone()) finishInterview(session, now) else askInterview(session, now)
            Interview.Result.STOPPED -> finishInterview(session, now)
        }
    }

    /**
     * Turns the answers, and suggestions from the owner's own notes, into one review card. The model only proposes fact lines;
     * every line still becomes an ordinary RememberFact proposal and waits for a tap. The flag is set even if the owner stops early.
     */
    private suspend fun finishInterview(session: Interview, now: LocalDateTime) {
        if (interview === session) interview = null
        preferences().edit().putBoolean(INTERVIEW_DONE, true).apply()
        val pairs = session.pairs()
        if (pairs.isEmpty()) {
            add(Turn.Notice(THANKS_NONE, now))
            return
        }
        val existing = withContext(Dispatchers.IO) {
            (graph.memory.ofKind(RecordKind.FACT) + graph.memory.ofKind(RecordKind.GUESS)).map { it.text }.toSet()
        }
        val extractor = InterviewFactExtractor(graph.modelHost)
        val facts = try {
            extractor.facts(pairs, existing)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Interview.fallbackFacts(pairs)
        }
        val notes = withContext(Dispatchers.IO) {
            graph.memory.ofKind(RecordKind.NOTE)
                .filter { graph.proposals.get(it.id)?.provenance == Provenance.USER }
                .sortedByDescending { it.createdAt }
                .map { it.text }
        }
        val suggestions = if (notes.isEmpty()) {
            emptyList()
        } else {
            try {
                extractor.suggestions(notes, existing + facts)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        val candidates = (facts + suggestions).distinctBy { it.lowercase() }
        if (candidates.isEmpty()) {
            add(Turn.Notice(thanks(nameIn(candidates)), now))
            return
        }
        if (suggestions.isNotEmpty()) {
            add(Turn.Notice("The last ${suggestions.size} of ${candidates.size} are suggestions from your notes. Keep only the ones that are right.", now))
        }
        val plan = graph.pipeline.planFacts(candidates, now)
        if (plan.cards.isEmpty()) {
            add(Turn.Notice(thanks(nameIn(candidates)), now))
            return
        }
        record(plan, now, graph.appContext.getString(R.string.interview_plan_title))
        add(Turn.Notice(thanks(nameIn(candidates)), now))
    }

    private fun thanks(name: String?): String = if (name.isNullOrBlank()) THANKS_NONE else "Thank you, $name. Tap on anything you want me to remember."

    private fun nameIn(facts: List<String>): String? = facts.firstNotNullOfOrNull { fact ->
        Regex("""^my name is (.+)$""", RegexOption.IGNORE_CASE).find(fact)?.groupValues?.get(1)?.trim()?.take(60)?.takeIf { it.isNotEmpty() }
    }

    /** One sentence through the pipeline. What no action fits goes to the model as a plain question, which can only answer in words. */
    private suspend fun handle(text: String, now: LocalDateTime, history: List<Turn>) {
        when (val outcome = graph.pipeline.handle(text, now)) {
            Outcome.Nothing -> chat(text, now, history)
            is Outcome.Question -> answerFromRecords(outcome.query, now, history)
            else -> record(outcome, now)
        }
    }

    private suspend fun chat(text: String, now: LocalDateTime, history: List<Turn>) {
        _partial.value = ""
        try {
            val started = SystemClock.elapsedRealtime()
            var firstMillis = -1L
            val facts = withContext(Dispatchers.IO) { graph.memory.ofKind(RecordKind.FACT) }
            val reply = graph.agent.reply(history, text, sensor.read().nowLine(), MemoryBlock.build(facts, text)) {
                if (firstMillis < 0) firstMillis = SystemClock.elapsedRealtime() - started
                _partial.value = it
            }
            val totalMillis = SystemClock.elapsedRealtime() - started
            Log.i("BheemChat", "first text $firstMillis ms, answer $totalMillis ms")
            add(Turn.Chat(reply.text, LocalDateTime.now()))
            reply.offer?.let { offerCard(it, text) }
            reply.fact?.let { factCard(it, text, now) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i("BheemChat", "chat cancelled")
            throw e
        } catch (e: app.bheem.models.host.ModelUnavailable) {
            add(Turn.Notice("${e.message.orEmpty().replaceFirstChar { it.uppercase() }}. Until the model is ready I can still do: remind me to ... at ..., note ..., task ...", now))
        } catch (e: Exception) {
            add(Turn.Notice("I could not answer that. Try again.", now))
        } finally {
            _partial.value = null
        }
    }

    /** A records question is grounded in the recall result, so the answer and the exact records come from the same persona. */
    private suspend fun answerFromRecords(query: String, now: LocalDateTime, history: List<Turn>) {
        val recalled = graph.recall.answer(query, now)
        _partial.value = ""
        try {
            val facts = withContext(Dispatchers.IO) { graph.memory.ofKind(RecordKind.FACT) }
            val reply = graph.agent.reply(
                history, query, sensor.read().nowLine(), MemoryBlock.build(facts, query),
                grounding = ToolResult(recalled.text, recalled.citations),
            ) { _partial.value = it }
            add(Turn.Chat(reply.text, LocalDateTime.now()))
            if (recalled.citations.isNotEmpty()) add(Turn.Answer(recalled.text, recalled.citations, now))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            add(Turn.Answer(recalled.text, recalled.citations, now))
        } finally {
            _partial.value = null
        }
    }

    /**
     * The model may end a reply with a command the owner could give. It becomes a card only if it reuses the owner's own words and only for a plain
     * record; anything else that the pipeline made of it is dismissed at once, so the owner never sees a card the model invented.
     */
    private suspend fun offerCard(command: String, ownerText: String) {
        if (!OfferCheck.grounded(command, ownerText)) return
        val at = LocalDateTime.now()
        val outcome = runCatching { graph.pipeline.handle(command, at) }.getOrNull() ?: return
        val cards = when (outcome) {
            is Outcome.Card -> listOf(outcome)
            is Outcome.Plan -> outcome.cards
            else -> return
        }
        if (cards.all { OfferCheck.allows(it.action.action) }) {
            record(outcome, at)
        } else {
            cards.forEach { dismissQuietly(it.proposalId) }
        }
    }

    /** A lasting fact the chat model suggested becomes a card only when its words come from the owner; anything else is dropped silently. */
    private fun factCard(fact: String, ownerText: String, now: LocalDateTime) {
        if (!OfferCheck.factGrounded(fact, ownerText)) return
        val plan = runCatching { graph.pipeline.planFacts(listOf(fact), now) }.getOrNull() ?: return
        if (plan.cards.isNotEmpty()) record(plan, now)
    }

    private val _memory = MutableStateFlow<List<MemoryRecord>>(emptyList())

    /** What Bheem knows: the owner's confirmed facts and Bheem's unconfirmed guesses, newest first. */
    val memory: StateFlow<List<MemoryRecord>> = _memory

    fun deleteMemory(id: String) {
        scope.launch(Dispatchers.IO) { graph.memory.remove(id) }
    }

    /** The owner confirms a guess: it becomes a fact the model may use. */
    fun confirmGuess(id: String) {
        scope.launch(Dispatchers.IO) {
            val guess = graph.memory.ofKind(RecordKind.GUESS).firstOrNull { it.id == id } ?: return@launch
            graph.memory.add(MemoryRecord("fact-${java.util.UUID.randomUUID()}", RecordKind.FACT, guess.text, LocalDateTime.now()))
            graph.memory.remove(id)
        }
    }

    fun forgetGuesses() {
        scope.launch(Dispatchers.IO) { graph.memory.ofKind(RecordKind.GUESS).forEach { graph.memory.remove(it.id) } }
    }

    /** Reads the owner's own words from the last day and stores up to five guesses. Run overnight while the phone charges. */
    suspend fun learnFromToday(): Int = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val turns = graph.storage.turns.recent(DAY_TURNS).filter { it.at.isAfter(now.minusHours(24)) }
        val existing = (graph.memory.ofKind(RecordKind.FACT) + graph.memory.ofKind(RecordKind.GUESS)).map { it.text }.toSet()
        val found = MemoryExtractor(graph.modelHost).extract(turns, existing)
        found.forEach { graph.memory.add(MemoryRecord("guess-${java.util.UUID.randomUUID()}", RecordKind.GUESS, it, now)) }
        found.size
    }

    private fun add(turn: Turn) {
        thread.add(turn)
        _turns.value = thread.turns()
        maybeSpeak(turn)
    }

    @Volatile
    private var pendingClarify: String? = null

    private sealed interface Offer {
        data class OfPlan(val steps: List<String>) : Offer

        data class OfCard(val id: String) : Offer
    }

    /** What the last answer put in front of the owner. Only that can be approved by "yes"; an older plan or card cannot. */
    @Volatile
    private var offer: Offer? = null

    private fun waitingIds(): Set<String> = _sections.value.needsYou.filter { it.status == FeedStatus.PENDING }.map { it.id }.toSet()

    /** "yes" approves the plan or the card the last answer offered, if it is still waiting. Anything else is left to a tap. */
    private suspend fun confirm(offered: Offer?, now: LocalDateTime) {
        val waiting = waitingIds()
        when (offered) {
            is Offer.OfPlan -> {
                val steps = offered.steps.filter { it in waiting }
                if (steps.isEmpty()) add(Turn.Notice("There is nothing waiting for your tap.", now)) else runPlan(steps, now)
            }
            is Offer.OfCard -> if (offered.id in waiting) act { graph.coordinator.approve(offered.id, now) } else add(Turn.Notice("There is nothing waiting for your tap.", now))
            null -> add(Turn.Notice("Tap the button on the card you mean.", now))
        }
    }

    fun doAll(steps: List<String>) {
        lastInputSpoken = false
        scope.launch {
            val pending = waitingIds()
            val ready = steps.filter { it in pending }
            if (ready.isNotEmpty()) runPlan(ready, LocalDateTime.now())
        }
    }

    private suspend fun runPlan(steps: List<String>, now: LocalDateTime) {
        val runner = PlanRunner(
            approve = { id -> graph.coordinator.approve(id, LocalDateTime.now()) },
            isHandOff = { id -> graph.proposals.get(id)?.action?.action in HAND_OFFS },
        )
        val result = runner.approveAll(steps)
        val left = steps.size - result.done.size
        val text = when {
            result.stoppedAt == null -> "Done: ${result.done.size} of ${steps.size}."
            else -> "Done ${result.done.size} of ${steps.size}. $left still wait for your tap (${result.reason})."
        }
        add(Turn.Notice(text, now))
    }

    private fun record(outcome: Outcome, now: LocalDateTime, title: String = PLAN_TITLE) {
        when (outcome) {
            is Outcome.Plan -> {
                offer = Offer.OfPlan(outcome.cards.map { it.proposalId })
                add(Turn.Plan(title, outcome.cards.map { it.proposalId }, outcome.skipped, now))
            }
            is Outcome.Clarify -> {
                pendingClarify = outcome.original
                add(Turn.Notice(outcome.question, now))
            }
            is Outcome.Card -> offer = Offer.OfCard(outcome.proposalId)
            is Outcome.Question -> graph.recall.answer(outcome.query, now).let { add(Turn.Answer(it.text, it.citations, now)) }
            Outcome.Nothing -> Unit
            is Outcome.Refused -> add(Turn.Notice("I could not do that: ${sentence(outcome.reason)}", now))
            is Outcome.ModelUnavailable ->
                add(Turn.Notice("${outcome.reason.replaceFirstChar { it.uppercase() }}. Until the model is ready I can still do: remind me to ... at ..., note ..., task ...", now))
        }
    }

    private fun voiceMessage(result: VoiceResult): String? = when (result) {
        is VoiceResult.Heard -> {
            val now = LocalDateTime.now()
            val history = _turns.value
            lastInputSpoken = true
            add(Turn.User(result.transcript, spoken = true, now))
            val session = interview
            val gap = pendingGapQuestion
            if (session != null && session.isActive()) {
                when (session.answer(result.transcript)) {
                    Interview.Result.COMMAND -> {
                        when (val outcome = result.outcome) {
                            Outcome.Nothing -> chatJob = scope.launch { chat(result.transcript, now, history) }
                            is Outcome.Question -> chatJob = scope.launch { answerFromRecords(outcome.query, now, history) }
                            else -> record(outcome, now)
                        }
                        askInterview(session, now)
                    }
                    Interview.Result.STOPPED -> chatJob = scope.launch { finishInterview(session, now) }
                    Interview.Result.ANSWERED, Interview.Result.SKIPPED ->
                        if (session.isDone()) chatJob = scope.launch { finishInterview(session, now) } else askInterview(session, now)
                }
            } else if (gap != null) {
                pendingGapQuestion = null
                pendingClarify = null
                offer = null
                chatJob = scope.launch { answerGap(gap, result.transcript, now) }
            } else {
                val outcome = result.outcome
                val chat = DeviceQuestions.answer(result.transcript, sensor.read())
                when {
                    chat != null -> add(Turn.Answer(chat, emptyList(), now))
                    outcome === Outcome.Nothing -> chatJob = scope.launch { chat(result.transcript, now, history) }
                    outcome is Outcome.Question -> chatJob = scope.launch { answerFromRecords(outcome.query, now, history) }
                    else -> record(outcome, now)
                }
            }
            null
        }
        VoiceResult.NothingHeard -> "I did not catch that. Try again."
        is VoiceResult.Unavailable -> "Voice needs the language model: ${sentence(result.reason)}"
    }

    fun startListening() {
        stopSpeaking()
        talk.start()
    }

    fun stopListening() = talk.stop()

    fun cancelListening() = talk.cancel()

    fun say(text: String) {
        _message.value = text
    }

    fun editHeard(item: FeedItem) {
        val text = item.heardAs ?: return
        dismiss(item.id)
        _draft.value = text
    }

    fun draftTaken() {
        _draft.value = null
    }

    internal suspend fun heardFromBytes(wav: ByteArray): VoiceResult = graph.voice.handle(wav, LocalDateTime.now())

    internal suspend fun dismissQuietly(id: String) {
        graph.coordinator.dismiss(id, LocalDateTime.now())
    }

    fun refreshFeed() {
        scope.launch {
            val now = LocalDateTime.now()
            _sections.value = FeedSections.of(withContext(Dispatchers.Default) { Feed.items(graph.feed.todayRows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }, now)
        }
    }

    private var diagnosticsJob: Job? = null

    /** An empty report means the diagnostics could not be read. */
    fun loadDiagnostics() {
        diagnosticsJob?.cancel()
        _diagnostics.value = null
        diagnosticsJob = scope.launch {
            _diagnostics.value = withContext(Dispatchers.IO) {
                runCatching { Diagnostics.report(Diagnostics.refusals(graph.storage.ledger)) }.getOrDefault("")
            }
        }
    }

    fun loadLedger() {
        _ledger.value = null
        scope.launch { _ledger.value = withContext(Dispatchers.IO) { LedgerLines.of(graph.feed.rows(LEDGER_LIMIT)) } }
    }

    fun retryModel() {
        scope.launch {
            runCatching {
                graph.modelHost.acquire()
                graph.modelHost.release()
            }
        }
    }

    fun getModel() = graph.supply.start()

    fun useMobileData() = graph.supply.useCellular()

    fun cancelDownload() = graph.supply.cancel()

    fun refreshSupply() = graph.supply.refresh()

    fun cancelSubmit() {
        stopSpeaking()
        Log.i("BheemChat", "stop pressed")
        submitJob?.cancel()
        chatJob?.cancel()
        _busy.value = false
    }

    fun approve(id: String) = act { graph.coordinator.approve(id, LocalDateTime.now()) }

    fun undo(id: String) = act { graph.coordinator.undo(id, LocalDateTime.now()) }

    fun completeTask(id: String) {
        scope.launch(Dispatchers.IO) { graph.memory.markDone(id, LocalDateTime.now()) }
    }

    fun reopenTask(id: String) {
        scope.launch(Dispatchers.IO) { graph.memory.markDone(id, null) }
    }

    fun dismiss(id: String) {
        scope.launch { graph.coordinator.dismiss(id, LocalDateTime.now()) }
    }

    suspend fun close() {
        talk.cancel()
        speakerInstance?.shutdown()
        scope.cancel()
        graph.modelHost.shutdown()
        graph.close()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(block: suspend () -> ApprovalResult) {
        lastInputSpoken = false
        scope.launch {
            _message.value = when (val r = block()) {
                ApprovalResult.Executed, ApprovalResult.Undone -> null
                is ApprovalResult.Failed -> "That did not work: ${sentence(r.reason)}"
                is ApprovalResult.Refused -> if (r.reason.startsWith(ALREADY_DONE)) null else "Not done: ${sentence(r.reason)}"
            }
        }
    }

    private fun sentence(reason: String) = if (reason.endsWith(".")) reason else "$reason."

    private companion object {
        const val FEED_ROWS = 100
        const val DAY_TURNS = 200
        const val SPEAK_KEY = "speak_mode"
        const val PLAN_TITLE = "plan"
        const val INTERVIEW_DONE = "interview_done"
        const val PROACTIVE_STATE = "proactive_state"
        const val THANKS_NONE = "Thank you. Tap on anything you want me to remember."
        val INTERVIEW_START = setOf("get to know me", "interview me", "learn about me", "update what you know about me")
        val GAP_SKIP = setOf("skip", "next", "stop", "later", "cancel")
        val HAND_OFFS = setOf("CreateCalendarEvent", "DraftMessage", "PlaceCall", "OpenRoute", "SetTimer", "OpenApp")
        const val ALREADY_DONE = "proposal is already"
    }
}
