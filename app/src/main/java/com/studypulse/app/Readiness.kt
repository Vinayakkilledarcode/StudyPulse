package com.studypulse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How the plan is going: progress, time left against the exam, missed topics, weak spots, streak and badges. */
@Composable
fun ReadinessScreen(vm: AppVM, onClose: () -> Unit) {
    BackHandler { onClose() }
    val tick = vm.tick
    val e = vm.e
    val s = e.store
    val today = e.today()
    var pick by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }

    val items = Plan.items
    val total = items.size
    val done = items.count { e.isDone(it) }
    val frac = if (total == 0) 0f else done.toFloat() / total
    val remaining = items.filter { !e.isDone(it) }
    val remMin = remaining.sumOf { e.left(it) }
    val overdue = e.overdue()
    val streak = e.streak()
    val weekDays = e.weekDaysStudied()
    if (streak > s.bestStreak) s.bestStreak = streak
    if (weekDays >= s.weeklyGoal) s.goalMet = true
    val weekEnd = e.weekStart(today) + 6
    val weekNeed = remaining.filter { e.sched(it) <= weekEnd }.sumOf { e.left(it) }
    val ahead = overdue.isEmpty() && done > 0
    val status = when {
        total == 0 -> "No plan yet"
        overdue.isEmpty() -> "On track"
        overdue.size <= 3 -> "A little behind"
        else -> "Behind"
    }
    val fit = Scheduler.fit(remMin, today, s.examDate, s.dailyMin, remaining.sortedBy { e.sched(it) }.map { e.left(it) })

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Gap(18)
        Row(Modifier.fillMaxWidth()) { Pill("Back", C.ink) { onClose() } }
        Gap(18)
        Label("Readiness", C.brand, size = 12)
        Gap(8)
        Head("How you are doing", 30, tracking = -0.04)
        Gap(16)

        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ring(frac, 120.dp, 12.dp, C.brandSoft, C.brand) { Head("${(frac * 100).toInt()}%", 26) }
                Column(Modifier.weight(1f)) {
                    Head(status, 20, weight = FontWeight.Bold, tracking = -0.01)
                    Text("$done of $total topics done (estimate)", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("${fmtH(remMin)} of study left (estimate)", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Mascot(84.dp, cheer = ahead, mood = if (overdue.size > 3) "sad" else "")
            }
        }
        Gap(14)

        Panel(Modifier.fillMaxWidth(), bg = if (fit != null && !fit.ok) C.warm else C.card) {
            Label("Exam", C.brand, size = 12)
            Gap(8)
            if (fit == null) {
                Text("Add your exam date to see whether your plan fits.", color = C.muted, fontSize = 14.sp, lineHeight = 20.sp)
            } else if (fit.days <= 0) {
                Text("Your exam date has passed or is today. Choose a new date.", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Head("${fit.days} days to go", 20, weight = FontWeight.Bold, tracking = -0.01)
                Text(
                    if (fit.ok) "You need about ${fmtH(fit.needMin)} and have about ${fmtH(fit.haveMin)}: it fits (estimates)."
                    else "You need about ${fmtH(fit.needMin)} but have about ${fmtH(fit.haveMin)}, ${fmtH(fit.shortMin)} short. " +
                        "Study about ${fmtH(fit.needDaily)} a day, or move the exam to ${fmtFullDate(fit.extendTo)} (estimates).",
                    color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Gap(12)
            Btn(if (s.examDate > 0L) "Change exam date" else "Set exam date", kind = BtnKind.Secondary, icon = "calendar") { pick = true }
        }
        Gap(14)

        if (overdue.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth(), bg = C.warm) {
                Label("Missed topics", C.accent, size = 12)
                Gap(8)
                Text("${overdue.size} topics are behind. Spread them over the coming days, keeping to ${fmtH(s.dailyMin)} a day where possible.", fontSize = 14.sp, lineHeight = 20.sp)
                Gap(12)
                Btn("Reschedule missed topics", Modifier.fillMaxWidth(), icon = "check") {
                    report = vm.busyDo("Rescheduling") { e.reschedule() }
                    vm.bump()
                }
                for (r in report) Text(r, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Gap(14)
        }

        Panel(Modifier.fillMaxWidth()) {
            Label("This week", C.brand, size = 12)
            Gap(8)
            Text("$weekDays of ${s.weeklyGoal} study days (streak: $streak)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Gap(8)
            Bar(weekDays.toFloat() / s.weeklyGoal)
            Text("About ${fmtH(weekNeed)} of study due by Sunday, including missed topics (estimate).", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Gap(14)

        val due = Revision.due(e)
        if (due.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth(), bg = C.warm) {
                Label("Revision due", C.accent, size = 12)
                for (x in due.take(5)) {
                    Gap(10)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(x.text.take(70), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
                            val ls = s.lastScore(x.id)
                            if (ls >= 0) Text("Last score $ls%", color = C.muted, fontSize = 12.sp)
                        }
                        Pill("Quiz", C.brand) { vm.startQuiz(x.id) }
                    }
                }
            }
            Gap(14)
        }
        val recent = items.filter { e.isDone(it) && s.review(it.id) == null }.takeLast(3)
        if (recent.isNotEmpty() && AiExtract.available()) {
            Panel(Modifier.fillMaxWidth()) {
                Label("Quiz yourself", C.brand, size = 12)
                Text("Quizzes left today: ${vm.quizLeft()} of ${vm.quizLimit()}", color = C.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                for (x in recent) {
                    Gap(10)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(x.text.take(70), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                        Pill("Quiz", C.brand) { vm.startQuiz(x.id) }
                    }
                }
            }
            Gap(14)
        }

        val hard = items.filter { s.feel(it.id) == "hard" }.take(3)
        val weak = if (Plan.tracks.size > 1) Plan.tracks.map { t ->
            val its = items.filter { it.track == t.id }
            Pair(t.name, if (its.isEmpty()) 1f else its.count { e.isDone(it) }.toFloat() / its.size)
        }.sortedBy { it.second }.take(2) else emptyList()
        if (hard.isNotEmpty() || weak.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Label("Revise first", C.brand, size = 12)
                if (hard.isNotEmpty()) {
                    Gap(8)
                    Text("You marked these as hard:", color = C.muted, fontSize = 13.sp)
                    for (h in hard) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("\u2022 " + h.text.take(70), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                            if (AiExtract.available()) Pill("Quiz", C.brand) { vm.startQuiz(h.id) }
                        }
                    }
                }
                if (weak.isNotEmpty()) {
                    Gap(12)
                    Text("Furthest behind by track:", color = C.muted, fontSize = 13.sp)
                    for (w in weak) {
                        Text("${w.first}: ${(w.second * 100).toInt()}% done", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        Bar(w.second, mod = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Gap(14)
        }

        Panel(Modifier.fillMaxWidth()) {
            Label("Badges", C.brand, size = 12)
            for (b in Progress.badges(e)) {
                Gap(12)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(b.sub, color = C.muted, fontSize = 12.5.sp)
                    }
                    Pill(if (b.got) "Earned" else "Locked", if (b.got) C.brand else C.muted)
                }
            }
        }
        Gap(14)
        Text(
            "These numbers are estimates from your plan and your study log. They are not a predicted exam score.",
            color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp
        )
        Gap(40)
    }

    if (pick) {
        DateChooser(
            if (s.examDate > today) s.examDate else today + 30,
            onPick = { s.examDate = it; vm.bump() }, onDismiss = { pick = false }
        )
    }
}

/** Study goals shown in Settings: exam date, daily time, weekly goal and the extraction engine. */
@Composable
fun GoalsCard(vm: AppVM) {
    val tick = vm.tick
    val s = vm.e.store
    var pick by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Head("Study goals", 17, weight = FontWeight.Bold, tracking = -0.01)
        Gap(12)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Exam date", color = C.muted, fontSize = 12.5.sp)
                Text(if (s.examDate > 0L) fmtFullDate(s.examDate) else "Not set", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Pill("Choose", C.brand) { pick = true }
            if (s.examDate > 0L) Pill("Clear") { s.examDate = 0L; vm.bump() }
        }
        Gap(14)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Study time a day", color = C.muted, fontSize = 12.5.sp)
                Text(fmtH(s.dailyMin), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Pill("-15 min") { s.dailyMin = s.dailyMin - 15; vm.bump() }
            Pill("+15 min") { s.dailyMin = s.dailyMin + 15; vm.bump() }
        }
        Gap(14)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Weekly goal", color = C.muted, fontSize = 12.5.sp)
                Text("${s.weeklyGoal} study days", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Pill("-1") { s.weeklyGoal = s.weeklyGoal - 1; vm.bump() }
            Pill("+1") { s.weeklyGoal = s.weeklyGoal + 1; vm.bump() }
        }
        Gap(14)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Smart extraction (AI)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Reads syllabi with AI when it is set up in this build. The text of the file is sent to Google. Off: only the rules on your phone. " +
                        "Used today: ${s.aiUsedToday()} of ${AiExtract.DAILY_LIMIT} requests.",
                    color = C.muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            SwitchKnob(s.engineMode == "smart") { s.engineMode = if (it) "smart" else "rules"; vm.bump() }
        }
    }
    if (pick) {
        DateChooser(
            if (s.examDate > 0L) s.examDate else java.time.LocalDate.now().toEpochDay() + 30,
            onPick = { s.examDate = it; vm.bump() }, onDismiss = { pick = false }
        )
    }
}

/** Ten-second check after a session: were the topics finished and how did it feel. */
@Composable
fun CheckinDialog(vm: AppVM) {
    val ids = vm.checkinIds
    var finished by remember(ids) { mutableStateOf(true) }
    var feel by remember(ids) { mutableStateOf("ok") }
    val names = ids.mapNotNull { Plan.item(it) }.take(3).map { it.text.take(60) }
    SpDialog(
        "How did that go?", { vm.skipCheckin() }, confirmText = "Save", dismissText = "Skip",
        onConfirm = { vm.saveCheckin(finished, feel) }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mascot(64.dp, cheer = finished)
            Text(names.joinToString("\n") { "\u2022 $it" }, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
        Gap(14)
        Label("Did you finish these topics?", C.brand, size = 11)
        Gap(6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Yes", Modifier.weight(1f), if (finished) BtnKind.Secondary else BtnKind.Ghost) { finished = true }
            Btn("Not yet", Modifier.weight(1f), if (!finished) BtnKind.Secondary else BtnKind.Ghost) { finished = false }
        }
        Gap(12)
        Label("How did it feel?", C.brand, size = 11)
        Gap(6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Easy", Modifier.weight(1f), if (feel == "easy") BtnKind.Secondary else BtnKind.Ghost) { feel = "easy" }
            Btn("Okay", Modifier.weight(1f), if (feel == "ok") BtnKind.Secondary else BtnKind.Ghost) { feel = "ok" }
            Btn("Hard", Modifier.weight(1f), if (feel == "hard") BtnKind.Secondary else BtnKind.Ghost) { feel = "hard" }
        }
        if (AiExtract.available() && ids.isNotEmpty()) {
            Gap(12)
            Btn("Save and take a quiz", Modifier.fillMaxWidth(), BtnKind.Secondary) {
                val first = ids.first()
                vm.saveCheckin(finished, feel)
                vm.startQuiz(first)
            }
        }
    }
}
