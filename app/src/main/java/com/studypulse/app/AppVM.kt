package com.studypulse.app

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class AppVM(app: Application) : AndroidViewModel(app) {
    /** The engine of the signed in account. Replaced when the account changes. */
    var e: Engine = Engine.get(app)
        private set

    /** Screens read this so they redraw after any change. Call bump() after every edit. */
    var tick by mutableIntStateOf(0)
        private set
    fun bump() { tick++ }

    // ------------------------------------------------------------------ busy screen (see Busy.kt)
    /** Name of the action being performed. Empty when nothing is running. */
    var busyLabel by mutableStateOf("")
        private set
    /** The task log on the busy screen: what the app is doing, step by step. See Busy.kt. */
    val busyLines = mutableStateListOf<BusyLine>()
    private var busyFrom = 0L
    private var busyHide: Job? = null

    private fun onMain(r: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) r() else Handler(Looper.getMainLooper()).post { r() }
    }

    private fun closeRunning(state: Int) {
        val i = busyLines.lastIndex
        if (i >= 0 && busyLines[i].state == 0) {
            val l = busyLines[i]
            busyLines[i] = l.copy(state = state, ms = SystemClock.elapsedRealtime() - l.at)
        }
    }

    /** Starts a new step in the task log; the step before it is marked done. Safe to call from any thread. */
    fun busyStep(text: String) {
        onMain {
            if (busyLabel.isNotEmpty()) {
                closeRunning(1)
                busyLines.add(BusyLine(text, 0, SystemClock.elapsedRealtime(), 0L))
            }
        }
    }

    /** Adds a result line under the current step (for example "43 topics found"). Safe to call from any thread. */
    fun busyNote(text: String) {
        onMain { if (busyLabel.isNotEmpty()) busyLines.add(BusyLine(text, 3, SystemClock.elapsedRealtime(), 0L)) }
    }

    /** Marks the running step as failed and shows the reason. */
    fun busyFail(text: String) {
        onMain {
            if (busyLabel.isNotEmpty()) {
                closeRunning(2)
                busyLines.add(BusyLine(text, 3, SystemClock.elapsedRealtime(), 0L))
            }
        }
    }

    fun busyBegin(label: String) {
        busyHide?.cancel()
        busyHide = null
        if (busyLabel.isEmpty()) {
            busyFrom = SystemClock.elapsedRealtime()
            busyLines.clear()
        }
        busyLabel = label
        closeRunning(1)
        busyLines.add(BusyLine(label, 0, SystemClock.elapsedRealtime(), 0L))
    }

    /** Hides the busy screen once the action is done AND it has been shown for BUSY_MIN_MS. */
    fun busyFinish() {
        if (busyLabel.isEmpty()) return
        busyHide?.cancel()
        closeRunning(1)
        val failed = busyLines.any { it.state == 2 }
        if (!failed && busyLines.lastOrNull()?.text != "Done") busyLines.add(BusyLine("Done", 1, SystemClock.elapsedRealtime(), 0L))
        val left = BUSY_MIN_MS - (SystemClock.elapsedRealtime() - busyFrom)
        busyHide = viewModelScope.launch {
            if (left > 0) delay(left)
            busyLabel = ""
        }
    }

    /** The topic whose video the Video button was tapped for; the video screen reads it. */
    var videoItem: Item? = null

    /** The Video button: opens the video screen, which writes the title and then opens the YouTube results. */
    fun openVideoSearch(item: Item, web: (String) -> Unit) {
        if (busyLabel.isNotEmpty()) return
        videoItem = item
        web("sp://video")
    }

    /** Runs on the video screen: topic, title, then the YouTube results address. Each step shows in the strip at the top. */
    suspend fun findVideoUrl(): String {
        val item = videoItem ?: return "https://m.youtube.com"
        val a = app()
        busyBegin("Finding your video")
        var url: String
        try {
            busyNote("Topic: " + TopicLinkData.query(item).take(70))
            delay(600)
            busyStep("Writing a video title")
            val title = TopicLinkData.videoTitle(a, item)
            busyNote("Title: $title")
            delay(700)
            busyStep("Opening YouTube")
            delay(500)
            url = TopicLinkData.searchUrl(title)
        } catch (ex: Exception) {
            url = TopicLinkData.video(item)
        } finally {
            busyFinish()
        }
        return url
    }

    /** Pause so each step of a task can be read before the next one starts (background threads only). */
    private fun pace(text: String) {
        if (text.startsWith("Text recognition: page")) return
        try { Thread.sleep(550) } catch (ex: InterruptedException) { }
    }

    /** A detail line under the current step, then a short pause (main thread, inside a coroutine). */
    private suspend fun slow(text: String) {
        busyNote(text)
        delay(500)
    }

    /** Runs a quick action behind the busy screen. */
    fun <T> busyDo(label: String, work: () -> T): T {
        busyBegin(label)
        try { return work() } finally { bump(); busyFinish() }
    }


    var status by mutableStateOf("")

    // ------------------------------------------------------------------ account
    var authed by mutableStateOf(Account.signedIn(app))
        private set
    private var authBusyState by mutableStateOf(false)
    private var authLabel = "Signing in"
    var authBusy: Boolean
        get() = authBusyState
        set(v) {
            authBusyState = v
            if (v) busyBegin(authLabel) else busyFinish()
        }
    var authError by mutableStateOf("")
    var phoneStage by mutableIntStateOf(0)          // 0 enter number, 1 enter code
    var phoneShown by mutableStateOf("")
    var resendAt by mutableLongStateOf(0L)
    var autoCode by mutableStateOf("")
    var instantReady by mutableStateOf(false)       // number verified by the device, waiting for the person to tap Continue
        private set
    private var instantCred: PhoneAuthCredential? = null
    private var verificationId = ""
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    init {
        // Old builds let people in without an account. That is no longer allowed: they must sign in.
        if (Account.method(app) == "guest") { Account.clear(app); authed = false }
        // A session that Firebase kept but the app lost (cleared app data) is picked up again.
        val u = Auth.current()
        if (u != null && !authed) adopt(u, if (u.phoneNumber.isNullOrEmpty()) "google" else "phone")
    }

    private fun app(): Application = getApplication()

    private fun adopt(u: FirebaseUser, method: String) {
        val a = app()
        Account.set(
            a, u.uid, u.displayName ?: "", u.email ?: "", u.phoneNumber ?: "",
            u.photoUrl?.toString() ?: "", method
        )
        Account.adoptLegacy(a)
        Account.adoptGuest(a)
        e = Engine.get(a)
        authed = true
        authBusy = false
        authError = ""
        phoneStage = 0
        Reminders.scheduleAll(a)
        bump()
        refreshPlan()
    }

    fun signInGoogle(act: Activity) {
        if (authBusy) return
        authLabel = "Signing in"
        authBusy = true
        authError = ""
        viewModelScope.launch {
            try {
                adopt(Auth.google(act), "google")
            } catch (ex: Exception) {
                authError = Auth.friendly(ex)
            } finally {
                authBusy = false
            }
        }
    }

    private fun phoneFlow() = Auth.PhoneFlow(
        onCode = { id, token ->
            verificationId = id
            resendToken = token
            phoneStage = 1
            authBusy = false
            resendAt = System.currentTimeMillis() + 30_000L
        },
        onDone = { u -> adopt(u, "phone") },
        onInstant = { c ->
            phoneStage = 1
            val sms = c.smsCode
            if (!sms.isNullOrEmpty()) { autoCode = sms; authBusy = false }   // fills the box, the person taps Verify
            else { instantCred = c; instantReady = true; authBusy = false }
        },
        onError = { m -> authError = m; authBusy = false }
    )

    fun sendCode(act: Activity, raw: String, resend: Boolean = false) {
        if (authBusy) return
        val num = Auth.e164(raw)
        if (num == null) { authError = "Enter a valid phone number with its country code."; return }
        authLabel = "Sending your code"
        authBusy = true
        authError = ""
        phoneShown = num
        Auth.sendCode(act, num, if (resend) resendToken else null, phoneFlow())
    }

    fun verifyCode(code: String) {
        if (authBusy) return
        val c = code.trim()
        if (c.length < 6) { authError = "Enter the 6 digit code."; return }
        authLabel = "Signing in"
        authBusy = true
        authError = ""
        Auth.verify(verificationId, c, phoneFlow())
    }

    fun confirmInstant() {
        val c = instantCred ?: return
        if (authBusy) return
        authLabel = "Signing in"
        authBusy = true
        Auth.confirm(c, phoneFlow())
    }

    fun changeNumber() { instantCred = null; instantReady = false; autoCode = ""; phoneStage = 0; authError = ""; verificationId = ""; resendToken = null }

    fun signOut(act: Activity?) {
        val a = app()
        if (e.activeSession() != null) e.finishSession(act, false)
        busyBegin("Signing out")
        viewModelScope.launch {
            Auth.signOut(a)
            Reminders.cancelAll(a)
            Account.clear(a)
            e = Engine.get(a)
            authed = false
            phoneStage = 0
            authError = ""
            importStage = "idle"
            bump()
            busyFinish()
        }
    }

    fun setDisplayName(n: String) { Account.setName(app(), n); bump() }

    // ------------------------------------------------------------------ premium
    var paywall by mutableStateOf(false)
    var payBusy by mutableStateOf(false)
    var payMsg by mutableStateOf("")

    init {
        PlayBilling.start(app()) { msg, active ->
            payBusy = false
            busyFinish()
            if (msg.isNotEmpty()) payMsg = msg
            if (active) paywall = false
            bump()
        }
    }

    fun refreshPlan() {
        if (authed) PlayBilling.refresh()
    }

    fun startCheckout(act: Activity, tier: PlanTier) {
        if (payBusy) return
        if (!PlayBilling.available(tier)) {
            payMsg = "Subscriptions are available in the version installed from Google Play."
            return
        }
        payBusy = true
        payMsg = ""
        busyBegin("Opening Google Play")
        if (!PlayBilling.launch(act, tier)) { payBusy = false; busyFinish(); payMsg = "Google Play could not open the purchase screen." }
    }

    // ------------------------------------------------------------------ plan upload
    var importStage by mutableStateOf("idle")        // idle, reading, preview, error
    var importPlan by mutableStateOf<ParsedPlan?>(null)
    var importName by mutableStateOf("")
    var importError by mutableStateOf("")
    var importMode by mutableStateOf("tomorrow")     // file, tomorrow, custom
    var importCustom by mutableLongStateOf(0L)

    private fun tomorrow(): Long = LocalDate.now().toEpochDay() + 1

    fun importStart(): Long = when (importMode) {
        "file" -> importPlan?.firstDate ?: tomorrow()
        "custom" -> importCustom
        else -> tomorrow()
    }

    var importNotes by mutableStateOf<List<String>>(emptyList())
    var importEngine by mutableStateOf("")
    var importBuilt by mutableStateOf(false)         // plan was built from edited topics
    var draftSubject by mutableStateOf("")
    var draftEngine by mutableStateOf("")
    val draft = mutableStateListOf<Topic>()
    var setupExam by mutableLongStateOf(0L)
    var setupDaily by mutableIntStateOf(120)
    var checkinIds by mutableStateOf<List<String>>(emptyList())

    fun inImportFlow(): Boolean = importStage == "preview" || importStage == "topics" || importStage == "setup"

    fun cancelImport() {
        importStage = "idle"; importPlan = null; importError = ""
        importNotes = emptyList(); importBuilt = false; draft.clear()
    }

    // ---- topic editing (step between reading a syllabus and building the plan)
    fun draftEdit(i: Int, title: String, minutes: Int?) {
        val t = draft.getOrNull(i) ?: return
        val nt = title.trim()
        if (nt.length < 3) return
        draft[i] = t.copy(title = nt.take(160), minutes = (minutes ?: t.minutes).coerceIn(DraftCheck.MIN_M, DraftCheck.MAX_M))
    }
    fun draftMinutes(i: Int, delta: Int) {
        val t = draft.getOrNull(i) ?: return
        draft[i] = t.copy(minutes = (t.minutes + delta).coerceIn(DraftCheck.MIN_M, DraftCheck.MAX_M))
    }
    fun draftDelete(i: Int) { if (i in draft.indices && draft.size > 3) draft.removeAt(i) }
    fun draftMove(i: Int, dir: Int) {
        val j = i + dir
        if (i !in draft.indices || j !in draft.indices) return
        val a = draft[i]
        draft[i] = draft[j]
        draft[j] = a
    }
    fun draftMerge(i: Int) {
        if (i <= 0 || i !in draft.indices) return
        val a = draft[i - 1]
        val b = draft[i]
        draft[i - 1] = a.copy(
            title = (a.title + "; " + b.title).take(160),
            minutes = (a.minutes + b.minutes).coerceIn(DraftCheck.MIN_M, DraftCheck.MAX_M),
            advanced = a.advanced || b.advanced
        )
        draft.removeAt(i)
    }
    fun draftAdd(unit: String, title: String) {
        val nt = title.trim()
        if (nt.length < 3) return
        draft.add(Topic(unit.trim().ifEmpty { "Topics" }.take(80), nt.take(160), 45, false))
    }
    fun draftDropLast(n: Int) { repeat(n) { if (draft.size > 3) draft.removeAt(draft.size - 1) } }
    fun toSetup() { if (draft.isNotEmpty()) importStage = "setup" }

    /** The code, not the AI, decides the schedule: topics are placed day by day within the daily time. */
    fun buildFromDraft() {
        if (draft.isEmpty() || busyLabel.isNotEmpty()) return
        val topics = draft.toList()
        busyBegin("Building your plan")
        viewModelScope.launch {
            try {
                busyStep("Reading your settings")
                delay(400)
                slow("Study time: " + fmtH(setupDaily) + " a day")
                slow(if (setupExam > 0L) "Exam date: " + fmtFullDate(setupExam) else "No exam date set")
                val st = e.store
                st.dailyMin = setupDaily
                st.examDate = setupExam
                busyStep("Placing " + topics.size + " topics on days")
                delay(450)
                val start = tomorrow()
                val name = draftSubject.trim().ifEmpty { importName }
                val plan = Scheduler.build(name, topics, start, setupDaily, importNotes)
                slow("Plan covers " + plan.days.size + " days, starting " + fmtFullDate(start))
                busyStep("Checking the result")
                delay(450)
                slow(plan.topicCount.toString() + " study items placed, none above " + fmtH(setupDaily) + " a day")
                importPlan = plan
                importName = name
                importBuilt = true
                importMode = "file"
                importCustom = start
                importStage = "preview"
            } catch (ex: Exception) {
                busyFail(ex.message ?: "The plan could not be built.")
            } finally {
                busyFinish()
            }
        }
    }

    // ---- quiz and revision
    var quizFor by mutableStateOf("")                // topic id; empty when no quiz is open
    var quizState by mutableStateOf("idle")          // loading, error, run or result
    var quizError by mutableStateOf("")
    var quizLimitHit by mutableStateOf(false)
    var quizSet by mutableStateOf<QuizSet?>(null)
    var quizIdx by mutableIntStateOf(0)
    var quizPick by mutableIntStateOf(-1)
    var quizCorrect by mutableIntStateOf(0)
    var quizResultLine by mutableStateOf("")

    fun quizLimit(): Int = if (Entitlement.tier(app()) == PlanTier.FREE) Quiz.FREE_DAILY else Quiz.PREMIUM_DAILY
    fun quizLeft(): Int = (quizLimit() - e.store.quizUsedToday()).coerceAtLeast(0)

    fun startQuiz(id: String) {
        val item = Plan.item(id) ?: return
        quizFor = id
        quizState = "loading"
        quizError = ""
        quizLimitHit = false
        quizSet = null
        quizIdx = 0
        quizPick = -1
        quizCorrect = 0
        quizResultLine = ""
        val cached = e.store.quizCache(id)
        if (cached.isNotEmpty()) {
            val q = QuizCheck.parse(cached, item.text, false)
            if (q != null) { quizSet = q; quizState = "run"; return }
        }
        if (!AiExtract.available()) {
            quizState = "error"
            quizError = "Quizzes need the smart (AI) engine. Add GEMINI_API_KEY to local.properties and rebuild (see FEATURES.md)."
            return
        }
        if (quizLeft() <= 0) {
            quizState = "error"
            quizLimitHit = true
            quizError = "You have used today's ${quizLimit()} quizzes. They reset tomorrow."
            return
        }
        e.store.quizCountOne()
        val trackName = Plan.tracks.firstOrNull { it.id == item.track }?.name ?: ""
        viewModelScope.launch {
            try {
                val q = withContext(Dispatchers.IO) { QuizApi.generate(item.text, trackName) }
                if (quizFor != id) return@launch
                e.store.setQuizCache(id, QuizCheck.toJson(q))
                quizSet = q
                quizState = "run"
            } catch (ex: Exception) {
                e.store.quizUndoOne()
                if (quizFor == id) {
                    quizState = "error"
                    quizError = "The quiz could not be written (" + (ex.message ?: "try again later") + "). It did not use up your daily quiz."
                }
            }
        }
    }

    fun quizAnswer(i: Int) {
        val q = quizSet ?: return
        if (quizPick >= 0) return
        quizPick = i
        if (q.mcqs[quizIdx].answer == i) quizCorrect++
    }

    /** After the last question the score sets the next review date (SM-2 style). */
    fun quizNext() {
        val q = quizSet ?: return
        if (quizIdx < q.mcqs.size - 1) { quizIdx++; quizPick = -1; return }
        val n = q.mcqs.size
        val pct = (quizCorrect * 100 + n / 2) / n
        val r = Sm2.update(e.store.review(quizFor), pct, e.today())
        e.store.setReview(quizFor, r)
        e.store.setLastScore(quizFor, pct)
        quizResultLine =
            if (pct < Sm2.PASS_PCT) "Below ${Sm2.PASS_PCT}%, so this topic is scheduled for revision tomorrow."
            else "Good. Next review in ${r.interval} ${if (r.interval == 1) "day" else "days"} (${fmtFullDate(r.due)})."
        quizState = "result"
        bump()
    }

    fun closeQuiz() {
        quizFor = ""
        quizState = "idle"
        quizSet = null
        bump()
    }

    // ---- check-in after a study session
    fun askCheckin(ids: List<String>) { if (ids.isNotEmpty()) checkinIds = ids }
    fun skipCheckin() { checkinIds = emptyList() }
    fun saveCheckin(finished: Boolean, feel: String) {
        for (id in checkinIds) {
            e.store.setFeel(id, feel)
            if (finished) e.store.setDone(id, true)
        }
        checkinIds = emptyList()
        bump()
    }

    fun readPlanFile(uri: Uri) {
        if (busyLabel.isNotEmpty()) return
        if (!Entitlement.canGenerate(app())) {
            importStage = "error"
            importError = "The free plan includes ${Entitlement.FREE_PLANS_PER_MONTH} new plans a month. Upgrade for unlimited plans."
            paywall = true
            return
        }
        importStage = "reading"
        importError = ""
        busyBegin("Reading your plan")
        viewModelScope.launch {
            try {
                val tr = Trace({ busyStep(it); pace(it) }, { busyNote(it); pace(it) })
                val got = withContext(Dispatchers.IO) {
                    val src = PlanSource.read(app(), uri, tr)
                    Pair(src.name, Extractor.run(src, LocalDate.now().year, e.store.engineMode, e.store, tr))
                }
                busyStep("Preparing your topics")
                delay(400)
                importName = got.first
                val r = got.second
                importNotes = r.notes
                importEngine = r.engine
                importBuilt = false
                val plan = r.plan
                val dr = r.draft
                slow("Read with: " + r.engine)
                if (plan != null) {
                    slow(plan.days.size.toString() + " days, " + plan.topicCount + " topics")
                    if (plan.tracks.isNotEmpty()) slow("Tracks: " + plan.tracks.take(4).joinToString(", "))
                    plan.firstDate?.let { slow("First dated day: " + fmtFullDate(it)) }
                    slow("About " + fmtH(plan.days.sumOf { d -> d.items.sumOf { it.minutes } }) + " of study in total (estimate)")
                    plan.days.firstOrNull()?.items?.firstOrNull()?.let { slow("First topic: " + it.text.take(80)) }
                } else if (dr != null) {
                    val units = dr.topics.map { it.unit }.filter { it.isNotBlank() }.distinct()
                    slow(dr.topics.size.toString() + " topics in " + units.size + " units")
                    if (units.isNotEmpty()) slow("Units: " + units.take(4).joinToString(", ") + (if (units.size > 4) " and " + (units.size - 4) + " more" else ""))
                    slow("About " + fmtH(dr.topics.sumOf { it.minutes }) + " of study in total (estimate)")
                    dr.topics.firstOrNull()?.let { slow("First topic: " + it.title.take(80)) }
                }
                if (plan != null) {
                    importPlan = plan
                    val first = plan.firstDate
                    importMode = if (plan.hasDates && first != null && first >= LocalDate.now().toEpochDay()) "file" else "tomorrow"
                    importCustom = tomorrow()
                    importStage = "preview"
                } else if (dr != null) {
                    draftSubject = dr.subject.ifBlank { got.first }
                    draftEngine = dr.engine
                    draft.clear()
                    draft.addAll(dr.topics)
                    val exam = e.store.examDate
                    setupExam = if (exam > tomorrow()) exam else 0L
                    setupDaily = e.store.dailyMin
                    importStage = "topics"
                } else {
                    throw IllegalArgumentException("This file could not be read.")
                }
            } catch (ex: Exception) {
                importError = ex.message ?: "This file could not be read."
                importStage = "error"
                busyFail(importError)
            } finally {
                busyFinish()
            }
        }
    }

    private fun endSessionIfAny() {
        if (e.activeSession() != null) e.finishSession(null, false)
    }

    fun confirmImport(onDone: () -> Unit = {}) {
        if (busyLabel.isNotEmpty()) return
        val p = importPlan ?: return
        val a = app()
        busyBegin("Building your schedule")
        viewModelScope.launch {
            try {
                busyStep("Preparing " + p.days.size + " days and " + p.topicCount + " topics")
                delay(450)
                endSessionIfAny()
                val json = PlanParser.toJson(p, importName, importStart())
                slow("Plan starts " + fmtFullDate(importStart()))
                slow("About " + fmtH(p.days.sumOf { d -> d.items.sumOf { it.minutes } }) + " of study in total (estimate)")
                busyStep("Clearing progress of the old plan")
                delay(450)
                Entitlement.recordGeneration(a)
                e.store.resetForNewPlan()
                slow("Old progress cleared")
                busyStep("Saving the plan on this phone")
                delay(450)
                Plan.saveJson(a, json)
                e = Engine.get(a)
                slow("Saved " + p.days.size + " days")
                busyStep("Setting study reminders")
                delay(450)
                Reminders.scheduleAll(a)
                slow("A reminder is set for each study day")
                importStage = "idle"
                importPlan = null
                bump()
                onDone()
            } catch (ex: Exception) {
                busyFail(ex.message ?: "The plan could not be saved.")
            } finally {
                busyFinish()
            }
        }
    }

    fun useSamplePlan(onDone: () -> Unit = {}) {
        if (busyLabel.isNotEmpty()) return
        val a = app()
        busyBegin("Building your schedule")
        viewModelScope.launch {
            try {
                busyStep("Clearing progress of the old plan")
                delay(450)
                endSessionIfAny()
                e.store.resetForNewPlan()
                busyStep("Saving the sample plan on this phone")
                delay(450)
                Plan.installSample(a)
                e = Engine.get(a)
                e.store.offset = (tomorrow() - Plan.baseStart).toInt()
                slow("Plan starts " + fmtFullDate(tomorrow()))
                busyStep("Setting study reminders")
                delay(450)
                Reminders.scheduleAll(a)
                slow("A reminder is set for each study day")
                importStage = "idle"
                bump()
                onDone()
            } catch (ex: Exception) {
                busyFail(ex.message ?: "The sample plan could not be saved.")
            } finally {
                busyFinish()
            }
        }
    }

    /** Index of a book that was just saved or found; the app opens it in the reader straight away. */
    var pendingOpen by mutableIntStateOf(-1)
    private val main = Handler(Looper.getMainLooper())

    private fun toast(m: String) {
        Toast.makeText(getApplication<Application>(), m, Toast.LENGTH_LONG).show()
    }

    private fun booksDir(): File {
        val d = File(getApplication<Application>().filesDir, "books")
        d.mkdirs()
        return d
    }

    fun bookFile(b: Book): File = File(booksDir(), b.file)

    private fun looksLikePdf(f: File): Boolean = try {
        f.inputStream().use { ins ->
            val buf = ByteArray(1024)
            val n = ins.read(buf)
            n > 4 && String(buf, 0, n, Charsets.ISO_8859_1).contains("%PDF")
        }
    } catch (ex: Exception) { false }

    private fun nameFromUrl(url: String): String {
        val seg = try {
            URLDecoder.decode(url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/'), "UTF-8")
        } catch (ex: Exception) { "" }
        val n = seg.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').replace('-', ' ').trim()
        return if (n.isEmpty()) "PDF book" else n
    }

    fun addBookFromUri(uri: Uri) {
        status = "Importing..."
        busyBegin("Importing PDF")
        Thread {
            var msg: String
            var opened = -1
            try {
                val cr = getApplication<Application>().contentResolver
                var name = "PDF book"
                busyStep("Opening the file")
                pace("")
                cr.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) name = (c.getString(i) ?: name).removeSuffix(".pdf").removeSuffix(".PDF")
                }
                busyNote("Name: $name")
                busyStep("Copying it into the app")
                pace("")
                val f = File(booksDir(), "${System.currentTimeMillis()}.pdf")
                cr.openInputStream(uri)?.use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
                busyNote(String.format(java.util.Locale.US, "%.1f MB copied", f.length() / 1_000_000.0))
                busyStep("Checking that it is a PDF")
                pace("")
                if (!looksLikePdf(f)) { f.delete(); throw RuntimeException("that file is not a PDF") }
                busyNote("PDF header found")
                busyStep("Adding it to My books")
                pace("")
                e.store.addBook(Book(name, f.name, 0))
                msg = "Added: $name"
                opened = e.store.books().size - 1
            } catch (ex: Exception) {
                msg = "Import failed: ${ex.message}"
                busyFail(msg)
            }
            main.post { busyFinish(); status = msg; toast(msg); bump(); if (opened >= 0) pendingOpen = opened }
        }.start()
    }

    private fun isPdfUrl(u: String) = u.substringBefore('?').substringBefore('#').lowercase().endsWith(".pdf")

    /** A link tap: PDFs load straight into the reader, everything else opens in the in-app browser. */
    fun openLink(url: String, web: (String) -> Unit) {
        if (isPdfUrl(url)) downloadBook(url) else web(url)
    }

    fun downloadBook(url: String, ua: String? = null, cookie: String? = null) {
        val have = e.store.books().indexOfFirst { it.src == url && bookFile(it).exists() }
        if (have >= 0) { pendingOpen = have; return }
        status = "Downloading..."
        toast("Downloading PDF...")
        busyBegin("Downloading PDF")
        Thread {
            var msg: String
            var opened = -1
            try {
                var u = url
                busyStep("Connecting to " + (try { URL(u).host } catch (x: Exception) { "the server" }))
                pace("")
                var conn: HttpURLConnection = URL(u).openConnection() as HttpURLConnection
                var hops = 0
                while (true) {
                    conn = URL(u).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    if (!ua.isNullOrEmpty()) conn.setRequestProperty("User-Agent", ua)
                    if (!cookie.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookie)
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 6) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (loc == null) throw RuntimeException("bad redirect")
                        u = URL(URL(u), loc).toString()
                        hops++
                        continue
                    }
                    if (code !in 200..299) throw RuntimeException("HTTP $code")
                    break
                }
                busyNote("Server answered: OK" + (if (hops > 0) " (after $hops redirects)" else ""))
                busyStep("Downloading the PDF")
                val f = File(booksDir(), "${System.currentTimeMillis()}.pdf")
                conn.inputStream.use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
                conn.disconnect()
                busyNote(String.format(java.util.Locale.US, "%.1f MB downloaded", f.length() / 1_000_000.0))
                busyStep("Checking that it is a PDF")
                pace("")
                if (!looksLikePdf(f)) { f.delete(); throw RuntimeException("that link is not a PDF") }
                busyNote("PDF header found")
                busyStep("Saving it to My books")
                pace("")
                val name = nameFromUrl(url)
                e.store.addBook(Book(name, f.name, 0, url))
                msg = "Saved to My books: $name"
                opened = e.store.books().size - 1
            } catch (ex: Exception) {
                msg = "Download failed: ${ex.message}"
                busyFail(msg)
            }
            main.post { busyFinish(); status = msg; toast(msg); bump(); if (opened >= 0) pendingOpen = opened }
        }.start()
    }

    fun deleteBook(idx: Int) {
        val b = e.store.books().getOrNull(idx) ?: return
        try { bookFile(b).delete() } catch (ex: Exception) {}
        e.store.removeBook(idx)
        bump()
    }
}
