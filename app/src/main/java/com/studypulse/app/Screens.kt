package com.studypulse.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private val BrandLight: Color get() = nt(Color(0xFFBFE2CD), Color(0xFFCDD2D7))
private val BrandText: Color get() = nt(Color(0xFFCFE2D7), Color(0xFFB4BBC2))
private val CardShadow = Color(0x331A2E22)

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val g = if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
    return if (name.isBlank()) "$g." else "$g, $name."
}

private fun fmtDayShort(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + d.dayOfMonth
}

private fun startTimer(vm: AppVM, ctx: android.content.Context, track: String, minutes: Int, items: List<Item>) {
    vm.busyDo("Starting your session") { vm.e.startSession(ctx.findActivity(), track, minutes, items.map { it.id }) }
}

@Composable
private fun SectionHead(title: String, sub: String, right: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Head(title, 21)
            Text(sub, color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (right.isNotEmpty()) Text(right, color = C.muted, fontSize = 13.sp)
    }
}

// ======================================================================= TODAY

@Composable
fun TodayScreen(vm: AppVM, openWeb: (String) -> Unit, openLinks: (String) -> Unit) {
    val ctx = LocalContext.current
    val tick = vm.tick
    val e = vm.e
    val scope = rememberCoroutineScope()
    val today = e.today()
    val overdue = e.overdue()
    val all = Plan.tracks.map { it.id to e.dueToday(it.id) }
    val groups = all.filter { it.second.isNotEmpty() }

    var dialog by remember { mutableStateOf("") } // "", study, carry, rest, adjusted
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var preparing by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Item?>(null) }

    val pd = e.planDay()
    val total = e.planTotal()
    val planned = e.plannedMin(today)
    val studied = e.studiedMin(today)
    val pct = if (planned == 0) 0 else minOf(100, studied * 100 / planned)

    val heroCopy = when {
        pd < 1 -> "Your plan begins in " + (1 - pd) + (if (1 - pd == 1) " day." else " days.")
        pd > total -> "You have completed the full study plan. Well done."
        planned == 0 -> "No sessions are planned today."
        else -> "A focused ${fmtLong(planned)} today keeps you comfortably on track."
    }
    val focusTitle = if (planned > 0) fmtLong(planned) + " planned" else "Nothing planned"
    val focusLine = when {
        groups.isEmpty() && planned > 0 -> "All of today's topics are complete"
        groups.isEmpty() -> "Enjoy the breathing room"
        else -> (when (groups.size) { 1 -> "One session"; 2 -> "Two sessions"; else -> "${groups.size} sessions" }) + " \u00B7 First up: " + trackLong(groups[0].first)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Gap(26)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Label(fmtEyebrowDay(today), C.brand, size = 12)
                Gap(8)
                Head(greeting(Account.firstName(ctx)), 32, tracking = -0.04)
                Text(heroCopy, color = C.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Column(
                Modifier.widthIn(min = 82.dp).clip(RoundedCornerShape(16.dp)).background(C.brandSoft)
                    .padding(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Text("Day ${pd.coerceIn(1, total)}", color = C.brand, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("of $total", color = C.brand.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Gap(26)
        // today's focus
        val cardShape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth()
                .shadow(14.dp, cardShape, ambientColor = CardShadow, spotColor = CardShadow)
                .clip(cardShape).background(C.fill).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ring(pct / 100f, 66.dp, 6.dp, Color.White.copy(alpha = 0.16f), nt(Color(0xFF9BD4B4), Color(0xFFE6E9EC))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("TODAY", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Label("Today\u2019s focus", BrandLight, size = 12)
                    Gap(4)
                    Head(focusTitle, 21, Color.White, weight = FontWeight.Bold, tracking = -0.02)
                    Text(focusLine, color = BrandText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Gap(16)
            Btn("View plan", Modifier.fillMaxWidth(), BtnKind.OnBrand, "calendar") { dialog = "study" }
        }

        if (overdue.isNotEmpty()) {
            Gap(14)
            val s = RoundedCornerShape(17.dp)
            Row(
                Modifier.fillMaxWidth().clip(s).background(C.brandSoft).border(1.dp, C.line, s)
                    .clickable { dialog = "carry" }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(C.fill),
                    contentAlignment = Alignment.Center
                ) { Text("${overdue.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                Column(Modifier.weight(1f)) {
                    Text("Unfinished topics from earlier days", fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                    Text("Spread them across lighter upcoming days", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Gap(34)
        SectionHead("Your sessions", "Pick up where you left off", "${groups.size} " + if (groups.size == 1) "task" else "tasks")
        Gap(14)
        if (groups.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Nothing left for today", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "All of today's topics are done. You can still run a free timer from the plan.",
                    color = C.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        for ((track, items) in groups) {
            TaskCard(
                track, items, e, openWeb, onEdit = { edit = it },
                onStart = { startTimer(vm, ctx, track, maxOf(15, items.sumOf { e.left(it) }), items) }
            )
            Gap(14)
        }

        // lighter day
        val rest = RoundedCornerShape(18.dp)
        val scan = remember { Animatable(0f) }
        LaunchedEffect(preparing) {
            if (preparing) {
                scan.snapTo(0f)
                scan.animateTo(1f, tween(520, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(rest)
                .background(if (preparing) C.brandSoft else C.warm)
                .border(1.dp, if (preparing) nt(Color(0xFFB9D3C1), Color(0xFF4A5057)) else C.line, rest)
                .clickable(enabled = !preparing) {
                    preparing = true
                    scope.launch {
                        delay(520)
                        preparing = false
                        dialog = "rest"
                    }
                }
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    if (preparing) "Preparing a lighter schedule\u2026" else "Need a lighter day?",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
                Text(
                    if (preparing) "Checking your upcoming workload" else "We\u2019ll rebalance unfinished work without overloading tomorrow.",
                    color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (preparing) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(0.36f).height(3.dp)
                        .graphicsLayer { translationX = (-1.1f + 4.0f * scan.value) * size.width }
                        .clip(RoundedCornerShape(50)).background(C.fill)
                )
            }
        }
        Gap(120)
    }

    // ---- dialogs
    if (dialog == "study") {
        Modal("What do you want to study?", { dialog = "" }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((track, items) in all) {
                    StudyOption(
                        track, items, e, openWeb,
                        onEdit = { dialog = ""; edit = it },
                        onLinks = { dialog = ""; openLinks(track) },
                        onStart = { m -> dialog = ""; startTimer(vm, ctx, track, m, items) }
                    )
                }
            }
        }
    }
    if (dialog == "carry") {
        SpDialog(
            "Rebalance unfinished topics?", { dialog = "" },
            confirmText = "Yes, rebalance my plan", dismissText = "Not now",
            onConfirm = {
                report = e.spread(e.overdue(), today + 1)
                vm.bump()
                dialog = "adjusted"
            }
        ) {
            Text(
                "${overdue.size} unfinished topics will move to the lightest upcoming days, one at a time, so no day becomes overloaded.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
    if (dialog == "rest") {
        SpDialog(
            "Take today off?", { dialog = "" },
            confirmText = "Yes, rebalance my plan", dismissText = "Keep today\u2019s plan",
            onConfirm = {
                report = vm.busyDo("Rebalancing your plan") { e.noStudyToday() }
                dialog = "adjusted"
            }
        ) {
            Text(
                "Today\u2019s unfinished topics will move to the lightest upcoming days, one at a time, so no day becomes overloaded.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
    if (dialog == "adjusted") {
        val n = report.sumOf { it.substringAfter("\u2192 ").split(", ").size }
        Modal("Your plan is balanced", { dialog = "" }) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(C.fill),
                contentAlignment = Alignment.Center
            ) { SpIcon("check", 26.dp, Color.White) }
            Gap(16)
            Text(
                if (report.isEmpty()) "There was nothing to move. Your plan is already balanced."
                else "We moved $n unfinished " + (if (n == 1) "topic" else "topics") + " to your lightest days, between " +
                    report.first().substringBefore(" \u2192") + " and " + report.last().substringBefore(" \u2192") +
                    ". Your daily workload remains achievable.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
            if (report.isNotEmpty()) {
                Gap(18)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.bg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (line in report.take(6)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(line.substringBefore(" \u2192"), color = C.muted, fontSize = 13.sp, modifier = Modifier.width(100.dp))
                            Text(
                                line.substringAfter("\u2192 ").replace(", ", " \u00B7 "),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (report.size > 6) Text("+ ${report.size - 6} more days", color = C.muted, fontSize = 13.sp)
                }
            }
            Gap(22)
            Btn("Done", Modifier.fillMaxWidth()) { dialog = "" }
        }
    }
    val ed = edit
    if (ed != null) ItemDialog(vm, ed) { edit = null }
}

@Composable
private fun TaskCard(
    track: String, items: List<Item>, e: Engine, openWeb: (String) -> Unit,
    onEdit: (Item) -> Unit, onStart: () -> Unit
) {
    val (icon, tileBg, tileFg) = trackStyle(track)
    val left = items.sumOf { e.left(it) }
    val s = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(s).background(C.card).border(1.dp, C.line, s).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconTile(icon, tileBg, tileFg)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label(trackLong(track), C.muted, size = 11)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpIcon("clock", 15.dp, C.ink)
                    Text(fmtH(left), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            for ((i, x) in items.withIndex()) {
                Gap(if (i == 0) 10 else 14)
                if (i == 0) {
                    Head(x.text, 17, weight = FontWeight.Bold, tracking = -0.01, mod = Modifier.clickable { onEdit(x) })
                } else {
                    Text(x.text, color = C.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.clickable { onEdit(x) })
                }
                Gap(10)
                TopicLinks(x, openWeb)
            }
            Gap(18)
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Gap(14)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${items.size} " + (if (items.size == 1) "topic" else "topics"),
                    color = C.muted, fontSize = 12.sp
                )
                Btn("Start session", icon = "play") { onStart() }
            }
        }
    }
}

@Composable
private fun StudyOption(
    track: String, items: List<Item>, e: Engine, openWeb: (String) -> Unit,
    onEdit: (Item) -> Unit, onLinks: () -> Unit, onStart: (Int) -> Unit
) {
    val (icon, tileBg, tileFg) = trackStyle(track)
    val base = if (items.isEmpty()) 60 else items.sumOf { e.left(it) }
    var adj by remember { mutableStateOf(0) }
    val minutes = maxOf(15, base + adj)
    val s = RoundedCornerShape(17.dp)
    Column(Modifier.fillMaxWidth().clip(s).border(1.dp, C.line, s).padding(15.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, tileBg, tileFg, 40.dp, 20.dp)
            Column(Modifier.weight(1f)) {
                Label(trackLong(track), C.muted, size = 11)
                if (items.isEmpty()) {
                    Text(
                        "Everything planned today is done. You can still run a free timer.",
                        color = C.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                for (x in items) {
                    Gap(8)
                    Text(x.text, fontSize = 14.5.sp, lineHeight = 21.sp, modifier = Modifier.clickable { onEdit(x) })
                    Gap(8)
                    TopicLinks(x, openWeb)
                }
            }
        }
        Gap(14)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("15 min", Modifier.weight(1f), BtnKind.Ghost, "minus") { if (minutes > 15) adj -= 15 }
            Btn("15 min", Modifier.weight(1f), BtnKind.Ghost, "plus") { adj += 15 }
            Btn("Links", Modifier.weight(1f), BtnKind.Ghost, "link") { onLinks() }
        }
        Gap(8)
        Btn("Start ${fmtH(minutes)} timer", Modifier.fillMaxWidth(), icon = "play") { onStart(minutes) }
    }
}

// ======================================================================= ITEM EDITOR

@Composable
fun ItemDialog(vm: AppVM, item: Item, onClose: () -> Unit) {
    val tick = vm.tick
    val e = vm.e
    val done = e.isDone(item)
    var pick by remember { mutableStateOf(false) }
    Modal(trackLong(item.track) + " \u00B7 " + fmtH(e.mins(item)), onClose) {
        Text(item.text, fontSize = 15.sp, lineHeight = 22.sp)
        Gap(6)
        Text(
            "Studied ${fmtH(e.store.studied(item.id))} \u00B7 planned ${fmtDate(e.sched(item))}" + if (done) " \u00B7 done" else "",
            color = C.muted, fontSize = 13.sp
        )
        Gap(18)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(
                if (done) "Mark as not done" else "Mark as done", Modifier.fillMaxWidth(),
                if (done) BtnKind.Ghost else BtnKind.Primary, "check"
            ) {
                if (done) e.markUndone(item) else e.markDone(item)
                vm.bump()
            }
            Btn("Log 30 min studied", Modifier.fillMaxWidth(), BtnKind.Secondary, "clock") { e.logTime(item, 30); vm.bump() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("15 min", Modifier.weight(1f), BtnKind.Ghost, "minus") { e.addMinutes(item, -15); vm.bump() }
                Btn("15 min", Modifier.weight(1f), BtnKind.Ghost, "plus") { e.addMinutes(item, 15); vm.bump() }
            }
            Btn("Move to tomorrow", Modifier.fillMaxWidth(), BtnKind.Ghost, "chevR") {
                e.move(item, e.today() + 1); vm.bump(); onClose()
            }
            Btn("Pick another date", Modifier.fillMaxWidth(), BtnKind.Ghost, "calendar") { pick = true }
        }
    }
    if (pick) {
        DateChooser(e.sched(item), onPick = { d -> e.move(item, d); vm.bump(); onClose() }, onDismiss = { pick = false })
    }
}

// ======================================================================= WEEK

@Composable
fun WeekScreen(vm: AppVM, openWeb: (String) -> Unit) {
    val tick = vm.tick
    val e = vm.e
    val today = e.today()
    var wk by remember { mutableStateOf(0) }
    var opened by remember { mutableStateOf(-1L) }
    var edit by remember { mutableStateOf<Item?>(null) }
    val start = e.weekStart(today) + wk * 7L

    var plannedW = 0
    val perTrack = IntArray(Plan.tracks.size)
    var doneN = 0
    var totalN = 0
    for (d in 0 until 7) {
        val day = start + d
        plannedW += e.plannedMin(day)
        for ((ti, t) in Plan.tracks.withIndex()) perTrack[ti] += e.store.logOf(day, t.id)
        val its = e.itemsOn(day)
        totalN += its.size
        doneN += its.count { e.isDone(it) }
    }
    val studiedW = perTrack.sum()
    val pctW = if (plannedW == 0) 0 else minOf(100, studiedW * 100 / plannedW)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Gap(26)
        Label(fmtDate(start) + " \u2013 " + fmtDate(start + 6), C.brand, size = 12)
        Gap(8)
        Head("Your week", 32, tracking = -0.04)
        Text(
            "A clear view of your momentum and what\u2019s ahead.",
            color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
        )
        Gap(20)
        val sw = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().clip(sw).background(C.card).border(1.dp, C.line, sw).padding(5.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            SquareBtn("chevL") { wk-- }
            Text(
                if (wk == 0) "This week" else if (wk < 0) "Past week" else "Upcoming week",
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { wk = 0 }.padding(horizontal = 10.dp, vertical = 6.dp)
            )
            SquareBtn("chevR") { wk++ }
        }

        Gap(16)
        val cs = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().shadow(14.dp, cs, ambientColor = CardShadow, spotColor = CardShadow)
                .clip(cs).background(C.fill).padding(22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Weekly progress", color = BrandLight, fontSize = 13.sp)
                    Head(fmtH(studiedW), 30, Color.White, mod = Modifier.padding(vertical = 4.dp))
                    Text("of ${fmtH(plannedW)} planned", color = BrandText, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpIcon("chart", 22.dp, Color.White)
                    Column {
                        Text("$pctW%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("completed", color = BrandText, fontSize = 11.sp)
                    }
                }
            }
            Gap(18)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            Gap(14)
            Row(Modifier.fillMaxWidth()) {
                for ((ti, t) in Plan.tracks.withIndex().take(2)) MiniStat(t.name, fmtH(perTrack[ti]), Modifier.weight(1f))
                MiniStat("Topics done", "$doneN / $totalN", Modifier.weight(1f))
            }
        }

        Gap(16)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (d in 0 until 7) {
                val day = start + d
                val its = e.itemsOn(day)
                val pl = its.sumOf { e.mins(it) }
                val st = e.studiedMin(day)
                val isToday = day == today
                val tracks = its.map { it.track }.distinct()
                val sub = if (isToday) "Today"
                else if (tracks.isEmpty()) "No sessions"
                else tracks.joinToString(" \u00B7 ") { trackName(it) }
                val rs = RoundedCornerShape(16.dp)
                val open = opened == day
                Column(
                    Modifier.fillMaxWidth().clip(rs).background(C.card)
                        .border(1.dp, if (isToday) nt(Color(0xFF9CC4AA), Color(0xFFB8BEC5)) else C.line, rs)
                        .clickable { opened = if (open) -1L else day }
                        .drawBehind { if (isToday) drawRect(C.brand, size = Size(4.dp.toPx(), size.height)) }
                        .padding(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.width(95.dp)) {
                            Text(fmtDayShort(day), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(sub, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Bar(if (pl == 0) 0f else st.toFloat() / pl)
                            Text(
                                "${fmtH(st)} / ${fmtH(pl)}", color = C.muted, fontSize = 11.sp, textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                            )
                        }
                    }
                    if (open) {
                        if (its.isEmpty()) {
                            Text("Nothing planned", color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                        }
                        for (it in its) {
                            Gap(14)
                            Row(
                                Modifier.fillMaxWidth().clickable { edit = it },
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DoneDot(e.isDone(it))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        it.text, fontSize = 14.sp, lineHeight = 20.sp,
                                        color = if (e.isDone(it)) C.muted else C.ink
                                    )
                                    Text(
                                        trackName(it.track) + " \u00B7 " + fmtH(e.mins(it)),
                                        color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            TopicLinks(it, openWeb, Modifier.padding(start = 30.dp, top = 8.dp))
                        }
                    }
                }
            }
        }
        Text(
            "Tap a day to see its topics. Tap a topic to mark it done, change its length or move it.",
            color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 16.dp)
        )
        Gap(120)
    }
    val ed = edit
    if (ed != null) ItemDialog(vm, ed) { edit = null }
}

@Composable
private fun SquareBtn(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { SpIcon(icon, 18.dp, C.ink) }
}

@Composable
private fun MiniStat(label: String, value: String, mod: Modifier) {
    Column(mod) {
        Text(label, color = BrandLight, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DoneDot(done: Boolean) {
    if (done) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(C.fill), contentAlignment = Alignment.Center) {
            SpIcon("check", 13.dp, Color.White, stroke = 2.4f)
        }
    } else {
        Box(Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, C.line, CircleShape))
    }
}

// ======================================================================= SETTINGS

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(s).background(C.card).border(1.dp, C.line, s).padding(18.dp), content = content)
}

@Composable
private fun ToastBar(text: String, modifier: Modifier = Modifier) {
    val s = RoundedCornerShape(13.dp)
    Row(
        modifier.shadow(10.dp, s).clip(s).background(C.ink).padding(horizontal = 17.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        SpIcon("check", 17.dp, Color.White)
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingsScreen(vm: AppVM, openLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val tick = vm.tick
    val e = vm.e
    val s = e.store
    var confirmRebuild by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(2600)
            message = ""
        }
    }

    fun pickTime(cur: Int, set: (Int) -> Unit) {
        android.app.TimePickerDialog(
            ctx,
            android.app.TimePickerDialog.OnTimeSetListener { _, hh, mm ->
                set(hh * 60 + mm)
                Reminders.scheduleAll(ctx)
                vm.bump()
                message = "Reminder time saved"
            },
            cur / 60, cur % 60, true
        ).show()
    }

    val startEpoch = Plan.baseStart + s.offset

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Gap(26)
            Label("Preferences", C.brand, size = 12)
            Gap(8)
            Head("Settings", 32, tracking = -0.04)
            Text(
                "Make StudyPulse work around your routine.",
                color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
            )
            Gap(22)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconTile("moon", C.brandSoft, C.brand)
                        Column(Modifier.weight(1f)) {
                            Head("Night mode", 17, weight = FontWeight.Bold, tracking = -0.01)
                            Text("Black and silver.", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        SwitchKnob(Night.on) { Night.set(ctx, it); vm.bump() }
                    }
                }
                GoalsCard(vm)
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconTile("bell", C.brandSoft, C.brand)
                        Column(Modifier.weight(1f)) {
                            Head("Study reminders", 17, weight = FontWeight.Bold, tracking = -0.01)
                            Text("A gentle nudge when it\u2019s time to focus.", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        SwitchKnob(s.notifOn) {
                            s.notifOn = it
                            Reminders.scheduleAll(ctx)
                            vm.bump()
                        }
                    }
                }

                if (Plan.tracks.isNotEmpty()) SettingsCard {
                    for ((ti, t) in Plan.tracks.withIndex()) {
                        if (ti > 0) {
                            Gap(16)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                            Gap(16)
                        }
                        val cur = s.remind(t.id, Reminders.defaultTime(ti))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Head(t.name + " reminder", 16, weight = FontWeight.Bold, tracking = -0.01)
                                Text("Every study day", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Btn(fmtClock(cur), kind = BtnKind.Ghost, icon = "clock") { pickTime(cur) { v -> s.setRemind(t.id, v) } }
                        }
                    }
                }

                SettingsCard {
                    Label("Plan start date", C.brand, size = 12)
                    Gap(12)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Head(fmtFullDate(startEpoch), 17, weight = FontWeight.Bold, tracking = -0.01)
                            Text("Day 1 of your study plan", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Btn("Choose date", kind = BtnKind.Secondary, icon = "calendar") { pickDate = true }
                    }
                    Gap(12)
                    Text(
                        "Changing the start date moves every topic you have not rescheduled by hand.",
                        color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp
                    )
                }

                SettingsCard {
                    Label("Focus lock", C.brand, size = 12)
                    Gap(14)
                    Toggle("Lock when a timer starts", "Stays locked until you tap stop", s.lockOn) { s.lockOn = it; vm.bump() }
                    Gap(16)
                    Toggle("Silence notifications", "Do Not Disturb during the timer", s.lockDnd) { s.lockDnd = it; vm.bump() }
                    Gap(16)
                    Toggle("Pin the app", "Home, Recents and the shade stop working", s.lockPin) { s.lockPin = it; vm.bump() }
                    Gap(16)
                    val dnd = FocusGuard.dndGranted(ctx)
                    Text(
                        if (dnd) "Do Not Disturb access granted" else "Do Not Disturb access not granted yet",
                        color = if (dnd) C.brand else C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (!dnd) {
                        Gap(10)
                        Btn("Grant Do Not Disturb access", Modifier.fillMaxWidth(), BtnKind.Secondary, "bell") { FocusGuard.openDndSettings(ctx) }
                    }
                    Gap(12)
                    Text(
                        "One-time phone setting for pinning: Settings \u2192 Security (Samsung: Biometrics and security \u2192 Other security settings) \u2192 Pin windows or App pinning \u2192 On.",
                        color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp
                    )
                }

                SettingsCard {
                    Head("Plan maintenance", 17, weight = FontWeight.Bold, tracking = -0.01)
                    Text(
                        "Keep completed topics and reset the remaining schedule.",
                        color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                    Gap(16)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Rebuild plan", Modifier.fillMaxWidth(), BtnKind.Secondary) { confirmRebuild = true }
                        Btn("Test notification", Modifier.fillMaxWidth(), BtnKind.Secondary) {
                            Reminders.test(ctx)
                            message = "Test notification sent"
                        }
                        Btn("Allow exact alarms", Modifier.fillMaxWidth(), BtnKind.Secondary) {
                            if (Build.VERSION.SDK_INT >= 31) {
                                try {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                SettingsCard {
                    Head("My books", 17, weight = FontWeight.Bold, tracking = -0.01)
                    Text(
                        "PDF study material saved on this phone.",
                        color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                    Gap(14)
                    Btn("Open library", Modifier.fillMaxWidth(), BtnKind.Secondary, "book") { openLibrary() }
                }
            }
            Gap(120)
        }

        if (message.isNotEmpty()) {
            ToastBar(message, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp, start = 18.dp, end = 18.dp))
        }
    }

    if (pickDate) {
        DateChooser(
            startEpoch,
            onPick = { d ->
                vm.busyDo("Moving your start date") {
                    s.offset = (d - Plan.baseStart).toInt()
                    Reminders.scheduleAll(ctx)
                }
                message = "Plan start date updated"
            },
            onDismiss = { pickDate = false }
        )
    }
    if (confirmRebuild) {
        SpDialog(
            "Rebuild plan?", { confirmRebuild = false },
            confirmText = "Rebuild", dismissText = "Cancel",
            onConfirm = {
                confirmRebuild = false
                vm.busyDo("Rebuilding your plan") { e.rebuild() }
                message = "Study plan rebuilt successfully"
            }
        ) {
            Text(
                "Topics you finished stay done. Everything else goes back to its original plan date and length.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
}
