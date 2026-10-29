package com.studypulse.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Step between reading a syllabus and building the plan: the person fixes the topics first. */
@Composable
fun TopicsStage(vm: AppVM) {
    var edit by remember { mutableStateOf(-1) }
    var adding by remember { mutableStateOf(false) }
    val total = vm.draft.sumOf { it.minutes }

    Panel(Modifier.fillMaxWidth(), bg = C.fill, border = C.fill, pad = 20.dp) {
        Label("We found", Color(0xFFCFE2D7), size = 12)
        Gap(6)
        Head("${vm.draft.size} topics", 24, Color.White, weight = FontWeight.Bold, tracking = -0.02)
        Gap(8)
        Text(
            "Read with: ${vm.draftEngine}. About ${fmtH(total)} in total (estimate).",
            color = Color(0xFFCFE2D7), fontSize = 13.5.sp, lineHeight = 19.sp
        )
        for (n in vm.importNotes) Text(n, color = Color(0xFFCFE2D7), fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Gap(14)
    Text("Check the topics. Rename, reorder, merge or delete them, then continue.", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp)
    Gap(12)
    OutlinedTextField(
        vm.draftSubject, { vm.draftSubject = it.take(60) }, Modifier.fillMaxWidth(),
        colors = tfColors(), singleLine = true, label = { Text("Plan name") }
    )

    for ((i, t) in vm.draft.withIndex()) {
        if (i == 0 || vm.draft[i - 1].unit != t.unit) {
            Gap(14)
            Label(t.unit.ifEmpty { "Topics" }, C.brand, size = 11)
            Gap(6)
        } else Gap(8)
        Panel(Modifier.fillMaxWidth(), pad = 12.dp) {
            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
            Text(
                "about ${t.minutes} min (estimate)" + (if (t.advanced) " \u00B7 harder topic" else ""),
                color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
            )
            Gap(8)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Edit", C.brand) { edit = i }
                Pill("-15 min") { vm.draftMinutes(i, -15) }
                Pill("+15 min") { vm.draftMinutes(i, 15) }
                if (i > 0) Pill("Up") { vm.draftMove(i, -1) }
                if (i < vm.draft.size - 1) Pill("Down") { vm.draftMove(i, 1) }
                if (i > 0) Pill("Merge up") { vm.draftMerge(i) }
                Pill("Delete", C.accent) { vm.draftDelete(i) }
            }
        }
    }

    Gap(16)
    Btn("Add a topic", Modifier.fillMaxWidth(), BtnKind.Secondary) { adding = true }
    Gap(10)
    Btn("Next: exam date and daily time", Modifier.fillMaxWidth(), icon = "check") { vm.toSetup() }
    Gap(10)
    Btn("Choose another file", Modifier.fillMaxWidth(), BtnKind.Ghost, "upload") { vm.cancelImport() }

    if (edit in vm.draft.indices) {
        val cur = vm.draft[edit]
        var title by remember(edit) { mutableStateOf(cur.title) }
        var mins by remember(edit) { mutableStateOf(cur.minutes.toString()) }
        SpDialog(
            "Edit topic", { edit = -1 }, confirmText = "Save", dismissText = "Cancel",
            onConfirm = { vm.draftEdit(edit, title, mins.toIntOrNull()); edit = -1 }
        ) {
            OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), colors = tfColors(), label = { Text("Topic") })
            Gap(10)
            OutlinedTextField(
                mins, { mins = it.filter { ch -> ch.isDigit() }.take(3) }, Modifier.fillMaxWidth(),
                colors = tfColors(), singleLine = true, label = { Text("Minutes (estimate)") }
            )
        }
    }
    if (adding) {
        var unit by remember { mutableStateOf(vm.draft.lastOrNull()?.unit ?: "Topics") }
        var title by remember { mutableStateOf("") }
        SpDialog(
            "Add a topic", { adding = false }, confirmText = "Add", dismissText = "Cancel",
            onConfirm = { vm.draftAdd(unit, title); adding = false }
        ) {
            OutlinedTextField(unit, { unit = it.take(80) }, Modifier.fillMaxWidth(), colors = tfColors(), singleLine = true, label = { Text("Unit") })
            Gap(10)
            OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), colors = tfColors(), label = { Text("Topic") })
        }
    }
}

private fun roundUp15(m: Int) = ((m + 14) / 15) * 15

/** Exam date and daily time, with a check whether the topics fit. */
@Composable
fun SetupStage(vm: AppVM) {
    var pick by remember { mutableStateOf(false) }
    val start = LocalDate.now().toEpochDay() + 1
    val total = vm.draft.sumOf { it.minutes }
    val fit = Scheduler.fit(total, start, vm.setupExam, vm.setupDaily, vm.draft.map { it.minutes })

    Panel(Modifier.fillMaxWidth()) {
        Label("Exam date", C.brand, size = 12)
        Gap(10)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (vm.setupExam > 0L) fmtFullDate(vm.setupExam) else "Not set",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f)
            )
            Btn("Choose date", kind = BtnKind.Secondary, icon = "calendar") { pick = true }
        }
        if (vm.setupExam > 0L) {
            Gap(8)
            Pill("No exam date") { vm.setupExam = 0L }
        }
    }
    Gap(14)
    Panel(Modifier.fillMaxWidth()) {
        Label("Study time each day", C.brand, size = 12)
        Gap(10)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(fmtH(vm.setupDaily), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Pill("-15 min") { vm.setupDaily = (vm.setupDaily - 15).coerceAtLeast(30) }
            Pill("+15 min") { vm.setupDaily = (vm.setupDaily + 15).coerceAtMost(720) }
        }
    }
    Gap(14)
    Panel(Modifier.fillMaxWidth(), bg = if (fit != null && !fit.ok) C.warm else C.card) {
        Label("Does it fit?", C.brand, size = 12)
        Gap(8)
        if (fit == null) {
            Text(
                "No exam date, so the plan simply runs at ${fmtH(vm.setupDaily)} a day: about ${fmtH(total)} of study in total (estimate).",
                color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp
            )
        } else if (fit.days <= 0) {
            Text("The exam date must be after tomorrow.", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else if (fit.ok) {
            Text(
                "Yes. You need about ${fmtH(fit.needMin)} and have about ${fmtH(fit.haveMin)} until the exam (estimates).",
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                "Not yet. You need about ${fmtH(fit.needMin)} but have about ${fmtH(fit.haveMin)} until the exam, " +
                    "${fmtH(fit.shortMin)} short (estimates). Pick one way to close the gap:",
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold
            )
            Gap(10)
            Btn("Study ${fmtH(roundUp15(fit.needDaily).coerceAtMost(720))} a day", Modifier.fillMaxWidth(), BtnKind.Secondary) {
                vm.setupDaily = roundUp15(fit.needDaily).coerceAtMost(720)
            }
            if (fit.dropTopics in 1 until (vm.draft.size - 2)) {
                Gap(8)
                Btn("Drop the last ${fit.dropTopics} topics", Modifier.fillMaxWidth(), BtnKind.Secondary) { vm.draftDropLast(fit.dropTopics) }
            }
            Gap(8)
            Btn("Move the exam to ${fmtFullDate(fit.extendTo)}", Modifier.fillMaxWidth(), BtnKind.Secondary) { vm.setupExam = fit.extendTo }
        }
    }
    Gap(18)
    Btn("Build my plan", Modifier.fillMaxWidth(), icon = "check") { vm.buildFromDraft() }
    Gap(10)
    Btn("Back to topics", Modifier.fillMaxWidth(), BtnKind.Ghost) { vm.importStage = "topics" }

    if (pick) {
        DateChooser(
            if (vm.setupExam > 0L) vm.setupExam else start + 30,
            onPick = { vm.setupExam = it }, onDismiss = { pick = false }
        )
    }
}
