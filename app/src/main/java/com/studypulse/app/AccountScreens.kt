package com.studypulse.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate

private val SoftGreen: Color get() = nt(Color(0xFFBFE2CD), Color(0xFFCDD2D7))
private val SoftText: Color get() = nt(Color(0xFFCFE2D7), Color(0xFFB4BBC2))
private val Shadow = Color(0x331A2E22)

// ======================================================================= SIGN IN

@Composable
private fun GoogleG(size: Dp) {
    Canvas(Modifier.size(size)) {
        val sw = this.size.minDimension * 0.2f
        val d = Size(this.size.width - sw, this.size.height - sw)
        val tl = Offset(sw / 2f, sw / 2f)
        drawArc(nt(Color(0xFFEA4335), Color(0xFFE6E9EC)), 215f, 100f, false, tl, d, style = Stroke(sw))
        drawArc(nt(Color(0xFFFBBC05), Color(0xFFC3C8CE)), 135f, 80f, false, tl, d, style = Stroke(sw))
        drawArc(nt(Color(0xFF34A853), Color(0xFFA9AFB6)), 50f, 85f, false, tl, d, style = Stroke(sw))
        drawArc(nt(Color(0xFF4285F4), Color(0xFFD5D9DD)), 0f, 50f, false, tl, d, style = Stroke(sw))
        val cy = this.size.height / 2f
        drawLine(nt(Color(0xFF4285F4), Color(0xFFD5D9DD)), Offset(this.size.width / 2f, cy), Offset(this.size.width - sw / 2f, cy), strokeWidth = sw)
    }
}

@Composable
fun AuthScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val configured = Auth.configured(ctx)
    val busy = vm.authBusy

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Gap(44)
        BrandMark(64.dp, 20.dp, 32.dp, gradient = true)
        Gap(20)
        Head("Welcome to StudyPulse", 28, align = TextAlign.Center)
        Text(
            "Sign in with Google to keep your study plan, progress and streak with you.",
            color = C.muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
        )
        Gap(28)

        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth().widthIn(max = 460.dp)
                .shadow(10.dp, shape, ambientColor = Shadow, spotColor = Shadow)
                .clip(shape).background(C.card).border(1.dp, C.line, shape).padding(20.dp)
        ) {
            if (!configured) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.warm).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(14.dp)
                ) {
                    Text("Sign-in is not set up in this build", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Add the Firebase file google-services.json to the app folder and build again (see the README). Sign-in is required, so the app stays on this screen until then.",
                        color = C.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                val gs = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(gs).background(C.card).border(1.dp, C.line, gs)
                        .clickable(enabled = !busy) { act?.let { vm.signInGoogle(it) } }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoogleG(20.dp)
                    Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (busy) {
                Gap(16)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = C.brand, trackColor = C.brandSoft, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                }
            }
            if (vm.authError.isNotEmpty()) {
                Gap(14)
                Text(vm.authError, color = C.accent, fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Gap(20)
        Text(
            "Your plan and progress are stored on this phone.",
            color = C.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center
        )
        Gap(30)
    }
}

// ======================================================================= UPLOAD A PLAN

/** Types the title letter by letter, waits, backspaces it, and starts over. */
@Composable
fun TypingTitle(text: String) {
    var n by remember { mutableStateOf(0) }
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        while (true) {
            for (i in 1..text.length) { n = i; delay(90) }
            delay(1400)
            for (i in text.length - 1 downTo 0) { n = i; delay(45) }
            delay(450)
        }
    }
    LaunchedEffect(Unit) {
        while (true) { caretOn = !caretOn; delay(420) }
    }
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Head(text.take(n), 30, tracking = -0.04)
        Box(Modifier.padding(start = 4.dp).width(3.dp).height(30.dp).background(if (caretOn) C.brand else Color.Transparent))
    }
}

@Composable
fun UploadScreen(vm: AppVM, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readPlanFile(uri)
    }
    var pickDate by remember { mutableStateOf(false) }
    if (onBack != null) BackHandler { if (vm.inImportFlow()) vm.cancelImport() else onBack() }

    val pageScroll = rememberScrollState()
    LaunchedEffect(vm.importStage) { pageScroll.scrollTo(0) }
    // The top part (Back button and the title strip) stays fixed; only the content under it scrolls.
    // So the task line is always in view, whichever stage the page is on and however far down the button was.
    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().padding(horizontal = 14.dp)
    ) {
        Gap(18)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (onBack != null) Pill("Back", C.ink) { if (vm.inImportFlow()) vm.cancelImport() else onBack() }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark()
                Head("StudyPulse", 20, tracking = 0.0)
            }
        }
        Gap(22)
        if (isInlineBusy(vm.busyLabel)) InlineTaskLine(vm.busyLines) else TypingTitle("Upload your plan")
        Gap(12)

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(pageScroll)) {
        Gap(12)
        when (vm.importStage) {
            "reading" -> Gap(4)
            "topics" -> TopicsStage(vm)
            "setup" -> SetupStage(vm)
            "preview" -> {
                val p = vm.importPlan
                if (p != null) {
                    val today = LocalDate.now().toEpochDay()
                    val first = p.firstDate
                    val last = p.days.mapNotNull { it.date }.maxOrNull()
                    val start = vm.importStart()
                    val cs = RoundedCornerShape(22.dp)
                    Column(
                        Modifier.fillMaxWidth().shadow(14.dp, cs, ambientColor = Shadow, spotColor = Shadow)
                            .clip(cs).background(C.fill).padding(20.dp)
                    ) {
                        Label("We found", SoftGreen, size = 12)
                        Gap(6)
                        Head("${p.days.size} days \u00B7 ${p.topicCount} topics", 24, Color.White, weight = FontWeight.Bold, tracking = -0.02)
                        Gap(12)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in p.tracks.take(4)) {
                                Text(
                                    t, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Gap(12)
                        Text(
                            if (p.hasDates && first != null && last != null)
                                (if (vm.importBuilt) "Planned from your topics: " else "Dates found in your file: ") + "${fmtFullDate(first)} to ${fmtFullDate(last)}."
                            else "No dates in your file, so the plan runs one day after the other.",
                            color = SoftText, fontSize = 13.5.sp, lineHeight = 19.sp
                        )
                    }
                    Gap(14)
                    Panel(Modifier.fillMaxWidth()) {
                        Label("Start date", C.brand, size = 12)
                        Gap(10)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Head(fmtFullDate(start), 18, weight = FontWeight.Bold, tracking = -0.01)
                                Text("Day 1 of your plan", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Btn("Choose date", kind = BtnKind.Secondary, icon = "calendar") { pickDate = true }
                        }
                        Gap(14)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (p.hasDates && first != null) {
                                Btn("Use file dates", Modifier.weight(1f), if (vm.importMode == "file") BtnKind.Secondary else BtnKind.Ghost) { vm.importMode = "file" }
                            }
                            Btn("Start tomorrow", Modifier.weight(1f), if (vm.importMode == "tomorrow") BtnKind.Secondary else BtnKind.Ghost) { vm.importMode = "tomorrow" }
                        }
                        if (p.hasDates && first != null && first < today) {
                            Text(
                                if (vm.importMode == "file") "Some dates in your file have passed. Those topics will show as unfinished."
                                else "The dates in your file have passed, so the plan starts tomorrow instead.",
                                color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    Gap(14)
                    Panel(Modifier.fillMaxWidth()) {
                        Label("Preview", C.brand, size = 12)
                        for (d in p.days.take(3)) {
                            Gap(14)
                            Text(
                                "Day ${d.n}" + (d.date?.let { " \u00B7 " + fmtDay(it) } ?: ""),
                                fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                            )
                            for (it in d.items.take(4)) {
                                Text(
                                    "\u2022 " + it.text, color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (d.items.size > 4) Text("+ ${d.items.size - 4} more", color = C.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (p.days.size > 3) Text("and ${p.days.size - 3} more days", color = C.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                    Gap(18)
                    Btn("Create my plan", Modifier.fillMaxWidth(), icon = "check") {
                        vm.confirmImport { onDone() }
                    }
                    Gap(10)
                    Btn("Choose another file", Modifier.fillMaxWidth(), BtnKind.Ghost, "upload") { vm.cancelImport() }
                }
            }
            else -> {
                val ds = RoundedCornerShape(20.dp)
                val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "rgb")
                val spin by inf.animateFloat(
                    0f, 360f,
                    androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.LinearEasing)
                    ),
                    label = "spin"
                )
                val night = Night.on
                Column(
                    Modifier.fillMaxWidth().clip(ds).background(C.card)
                        .drawBehind {
                            val ring = List(7) { i ->
                                if (night) {
                                    // silver shimmer: no colour in night mode
                                    val k = (kotlin.math.sin(Math.toRadians((spin + i * 60f).toDouble())).toFloat() + 1f) / 2f
                                    Color(0.35f + 0.60f * k, 0.37f + 0.60f * k, 0.40f + 0.58f * k, 1f)
                                } else Color.hsv((spin + i * 60f) % 360f, 1f, 1f)
                            }
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val r = CornerRadius(20.dp.toPx())
                            // soft glow, then a crisp line
                            drawRoundRect(
                                androidx.compose.ui.graphics.Brush.sweepGradient(ring.map { it.copy(alpha = 0.30f) }, center),
                                cornerRadius = r, style = Stroke(10.dp.toPx())
                            )
                            drawRoundRect(
                                androidx.compose.ui.graphics.Brush.sweepGradient(ring, center),
                                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                                cornerRadius = CornerRadius(18.5.dp.toPx()), style = Stroke(3.dp.toPx())
                            )
                        }
                        .clickable { picker.launch(arrayOf("*/*")) }.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconTile("upload", C.brandSoft, C.brand, 56.dp, 26.dp)
                    Gap(14)
                    Head("Choose a file", 20, weight = FontWeight.Bold)
                    Text("PDF (scans too), Word, Excel, CSV, text or a photo", color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Gap(16)
                    Btn("Browse files", icon = "upload") { picker.launch(arrayOf("*/*")) }
                }
                if (vm.importStage == "error") {
                    Gap(14)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.warm)
                            .border(1.dp, C.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(16.dp)
                    ) {
                        Text("We could not read this file", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(vm.importError, color = C.muted, fontSize = 13.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Gap(14)
                Btn("Use the sample plan instead", Modifier.fillMaxWidth(), BtnKind.Ghost) {
                    vm.useSamplePlan { onDone() }
                }
            }
        }
        Gap(40)
        }
    }

    if (pickDate) {
        DateChooser(
            vm.importStart(),
            onPick = { d -> vm.importCustom = d; vm.importMode = "custom" },
            onDismiss = { pickDate = false }
        )
    }
}

// ======================================================================= PROFILE

@Composable
private fun Avatar(url: String, name: String, size: Dp) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = if (url.isEmpty()) null else withContext(Dispatchers.IO) {
            try { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() } catch (e: Exception) { null }
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(C.brandSoft), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (name.isNotBlank()) {
            val initials = name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
            Text(initials, color = C.brand, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp)
        } else SpIcon("user", size * 0.5f, C.brand)
    }
}

@Composable
fun ProfileScreen(vm: AppVM, openUpload: () -> Unit) {
    val ctx = LocalContext.current
    val tick = vm.tick
    val e = vm.e
    var dialog by remember { mutableStateOf("") }   // "", name, replace, signout
    var nameDraft by remember { mutableStateOf("") }

    val name = Account.name(ctx)
    val email = Account.email(ctx)
    val phone = Account.phone(ctx)
    val method = Account.method(ctx)
    val total = e.planTotal()
    val pd = e.planDay()
    val doneN = Plan.items.count { e.isDone(it) }
    val start = Plan.baseStart + e.store.offset

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Gap(26)
        Label("Account", C.brand, size = 12)
        Gap(8)
        Head("Profile", 32, tracking = -0.04)
        Gap(20)

        val cs = RoundedCornerShape(20.dp)
        Row(
            Modifier.fillMaxWidth().clip(cs).background(C.card).border(1.dp, C.line, cs).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(Account.photo(ctx), name, 68.dp)
            Column(Modifier.weight(1f)) {
                Head(name.ifBlank { "Add your name" }, 20, weight = FontWeight.Bold, tracking = -0.02)
                val line = email.ifBlank { phone }
                if (line.isNotBlank()) Text(line, color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (method) { "google" -> "Signed in with Google"; "phone" -> "Signed in with phone"; else -> "On this phone only" },
                    color = C.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(C.bg).clickable { nameDraft = name; dialog = "name" },
                contentAlignment = Alignment.Center
            ) { SpIcon("edit", 18.dp, C.ink) }
        }

        Gap(14)
        val bs = RoundedCornerShape(22.dp)
        Row(
            Modifier.fillMaxWidth().shadow(14.dp, bs, ambientColor = Shadow, spotColor = Shadow)
                .clip(bs).background(C.fill).padding(20.dp)
        ) {
            ProfileStat("Day streak", "${e.streak()}", Modifier.weight(1f))
            ProfileStat("Topics done", "$doneN / ${Plan.items.size}", Modifier.weight(1f))
            ProfileStat("Hours studied", fmtH(e.store.totalMinutes()), Modifier.weight(1f))
        }

        Gap(14)
        Panel(Modifier.fillMaxWidth()) {
            Label("Study plan", C.brand, size = 12)
            Gap(10)
            Head(Plan.name.ifBlank { "My study plan" }, 18, weight = FontWeight.Bold, tracking = -0.01)
            Text(
                (if (pd < 1) "Starts ${fmtFullDate(start)}" else if (pd > total) "Plan complete" else "Day $pd of $total") +
                    " \u00B7 ${Plan.items.size} topics",
                color = C.muted, fontSize = 13.5.sp, modifier = Modifier.padding(top = 3.dp)
            )
            Text("Day 1: ${fmtFullDate(start)}", color = C.muted, fontSize = 13.5.sp, modifier = Modifier.padding(top = 2.dp))
            Gap(12)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in Plan.tracks.take(3)) {
                    Text(
                        t.name, color = C.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(C.brandSoft).padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Gap(16)
            Btn("Upload a new plan", Modifier.fillMaxWidth(), BtnKind.Secondary, "upload") { dialog = "replace" }
        }

        Gap(14)
        Panel(Modifier.fillMaxWidth()) {
            Head("Account", 17, weight = FontWeight.Bold, tracking = -0.01)
            Text(
                "Signing out keeps your plan and progress for the next time you sign in on this phone.",
                color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp)
            )
            Gap(14)
            Btn(
                if (Entitlement.tier(ctx) == PlanTier.FREE) "Upgrade to Premium" else "Plan: ${Entitlement.tier(ctx).label}",
                Modifier.fillMaxWidth(), BtnKind.Secondary, "check"
            ) { vm.paywall = true }
            Gap(10)
            Btn("Sign out", Modifier.fillMaxWidth(), BtnKind.Ghost, "logout") { dialog = "signout" }
        }
        Gap(120)
    }

    if (dialog == "name") {
        Modal("Your name", { dialog = "" }) {
            OutlinedTextField(
                value = nameDraft, onValueChange = { nameDraft = it.take(40) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = tfColors(), shape = RoundedCornerShape(12.dp),
                placeholder = { Text("How should we greet you?", color = C.muted) }
            )
            Gap(18)
            Btn("Save", Modifier.fillMaxWidth(), icon = "check") {
                vm.setDisplayName(nameDraft)
                dialog = ""
            }
        }
    }
    if (dialog == "replace") {
        SpDialog(
            "Upload a new plan?", { dialog = "" },
            confirmText = "Choose a file", dismissText = "Keep my plan",
            onConfirm = { dialog = ""; openUpload() }
        ) {
            Text(
                "Your current plan, progress and streak will be replaced when you create the new plan.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
    if (dialog == "signout") {
        SpDialog(
            "Sign out?", { dialog = "" },
            confirmText = "Sign out", dismissText = "Stay signed in",
            onConfirm = { dialog = ""; vm.signOut(ctx.findActivity()) }
        ) {
            Text(
                "Your plan and progress stay saved on this phone. A running timer will be discarded.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, mod: Modifier) {
    Column(mod) {
        Text(label, color = SoftGreen, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 3.dp))
    }
}
