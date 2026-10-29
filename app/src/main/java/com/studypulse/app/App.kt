package com.studypulse.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.delay

/** One screen on top of the main tabs: links, in-app browser, library or PDF reader. */
data class Ov(val kind: String, val arg: String = "", val idx: Int = 0)

@Composable
fun App(vm: AppVM) {
    // Keep the layout compact: a very large system font size is capped.
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, fontScale = base.fontScale.coerceAtMost(1.0f)),
        LocalTextStyle provides TextStyle(fontFamily = DM, color = C.ink)
    ) {
        SideEffect { TopicBusy.vm = vm }
        val barView = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            val w = barView.context.findActivity()?.window
            if (w != null) {
                val ctl = androidx.core.view.WindowCompat.getInsetsController(w, barView)
                ctl.isAppearanceLightStatusBars = !Night.on
                ctl.isAppearanceLightNavigationBars = !Night.on
            }
        }
        MaterialTheme(colorScheme = spColors(), typography = spTypography()) {
            AppBody(vm)
        }
    }
}

@Composable
private fun AppBody(vm: AppVM) {
    val tick = vm.tick
    val stack = remember { mutableStateListOf<Ov>() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var splash by rememberSaveable { mutableStateOf(true) }
    val sess = vm.e.activeSession()
    val top = stack.lastOrNull()

    val pend = vm.pendingOpen
    LaunchedEffect(pend) {
        if (pend >= 0) {
            stack.add(Ov("pdf", idx = pend))
            vm.pendingOpen = -1
        }
    }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }
    val openWeb: (String) -> Unit = { u ->
        if (u.startsWith("sp://res/")) stack.add(Ov("res", u.removePrefix("sp://res/"))) else stack.add(Ov("web", u))
    }
    val openLinks: (String) -> Unit = { track -> stack.add(Ov("links", track)) }

    val ready = vm.authed && Plan.hasPlan()
    Box(Modifier.fillMaxSize().background(C.bg)) {
      Box(Modifier.fillMaxSize().then(if (isClassicBusy(vm.busyLabel)) Modifier.blur(18.dp) else Modifier)) {
        if (!vm.authed) {
            AuthScreen(vm)
        } else if (!Plan.hasPlan()) {
            UploadScreen(vm, onBack = null, onDone = { })
        } else if (sess != null) {
            TimerScreen(vm, sess, openLinks, openWeb)
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(vm) { stack.add(Ov("ready")) }
                if (vm.tick >= 0 && Entitlement.showAds(androidx.compose.ui.platform.LocalContext.current)) AdBanner()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> TodayScreen(vm, openWeb, openLinks)
                        1 -> WeekScreen(vm, openWeb)
                        2 -> ProfileScreen(vm) { stack.add(Ov("upload")) }
                        else -> SettingsScreen(vm) { stack.add(Ov("lib")) }
                    }
                }
            }
            NavPill(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        }

        if (top != null && ready) {
            when (top.kind) {
                "links" -> LinksScreen(
                    vm, top.arg, onClose = { pop() },
                    openWeb = openWeb,
                    openLibrary = { stack.add(Ov("lib")) }
                )
                "web" -> WebScreen(vm, top.arg) { pop() }
                "res" -> ResourcesScreen(vm, top.arg, onClose = { pop() }, openWeb = openWeb)
                "ready" -> ReadinessScreen(vm, onClose = { pop() })
                "lib" -> LibraryScreen(vm, onClose = { pop() }, openPdf = { i -> stack.add(Ov("pdf", idx = i)) })
                "pdf" -> PdfScreen(vm, top.idx) { pop() }
                "upload" -> UploadScreen(vm, onBack = { vm.cancelImport(); pop() }, onDone = { pop() })
            }
        }

        if (vm.checkinIds.isNotEmpty() && vm.authed) CheckinDialog(vm)
        if (vm.quizFor.isNotEmpty() && vm.authed) QuizScreen(vm)
        if (vm.paywall && vm.authed) PaywallScreen(vm)

        if (splash) OpeningSplash { splash = false }
      }
        BusyOverlay(vm.busyLabel, vm.busyLines)
    }
}

@Composable
private fun TopBar(vm: AppVM, onStreak: () -> Unit = {}) {
    val tick = vm.tick
    val streak = vm.e.streak()
    Column(Modifier.fillMaxWidth().background(C.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark()
                Head("StudyPulse", 20, tracking = 0.0)
            }
            val s = RoundedCornerShape(50)
            Row(
                Modifier.clip(s).background(C.card).border(1.dp, C.line, s).clickable { onStreak() }.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$streak", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("day streak \u203A", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

@Composable
private fun NavPill(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("Today" to "home", "Week" to "calendar", "Profile" to "user", "Settings" to "settings")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color(0x2615261B), spotColor = Color(0x2615261B))
            .clip(shape).background(C.card.copy(alpha = 0.95f))
            .border(1.dp, C.line, shape).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((i, p) in items.withIndex()) {
            val sel = tab == i
            val bg by animateColorAsState(if (sel) C.fill else Color.Transparent, tween(200), label = "navbg")
            val fg by animateColorAsState(if (sel) nt(Color.White, Color(0xFFE6E9EC)) else C.muted, tween(200), label = "navfg")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable { onTab(i) }.padding(horizontal = 6.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SpIcon(p.second, 19.dp, fg)
                Text(p.first, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Full-screen countdown. The focus lock stays on until stop is tapped, even after the time is up. */
@Composable
fun TimerScreen(vm: AppVM, s: Session, openLinks: (String) -> Unit, openWeb: (String) -> Unit) {
    val ctx = LocalContext.current
    val e = vm.e
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(0) } // 0 none, 1 stop and save, 2 discard

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    DisposableEffect(Unit) {
        (ctx.findActivity() as? MainActivity)?.keepScreenOn(true)
        onDispose { (ctx.findActivity() as? MainActivity)?.keepScreenOn(false) }
    }
    // Back is disabled during a session. Only stop ends it.
    BackHandler(enabled = true) { }

    val totalSec = s.planMin * 60L
    val elapsedSec = ((now - s.start) / 1000L).coerceAtLeast(0L)
    val remain = totalSec - elapsedSec
    val over = remain < 0
    val studiedMin = (elapsedSec / 60).toInt()
    val items = s.ids.mapNotNull { Plan.item(it) }.filter { !e.isDone(it) }
    val dnd = FocusGuard.dndGranted(ctx)
    val pinned = FocusGuard.isPinned(ctx)
    val frac = if (totalSec == 0L) 1f else (elapsedSec.toFloat() / totalSec).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandMark(28.dp, 9.dp, 15.dp)
            Label(trackLong(s.track) + " \u00B7 focus lock on", C.brand, size = 12)
        }
        Spacer(Modifier.height(26.dp))
        Ring(frac, 236.dp, 10.dp, C.brandSoft, C.brand) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label(if (over) "Time is up" else "Focus session", C.muted, size = 12)
                Spacer(Modifier.height(6.dp))
                Text(
                    (if (over) "+" else "") + hms(remain),
                    style = TextStyle(
                        fontFamily = MX, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp,
                        letterSpacing = (-0.02).em, fontFeatureSettings = "tnum", color = C.ink
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (over) "Tap stop to save your session" else "of ${fmtH(s.planMin)} \u00B7 ${fmtH(studiedMin)} studied",
            color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))

        Panel(Modifier.fillMaxWidth()) {
            Label("Studying now", C.brand, size = 12)
            Spacer(Modifier.height(10.dp))
            if (items.isEmpty()) Text("Free study time", color = C.muted, fontSize = 14.sp)
            for ((i, x) in items.withIndex()) {
                if (i > 0) Spacer(Modifier.height(14.dp))
                Text(x.text, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
                TopicLinks(x, openWeb)
            }
        }
        Spacer(Modifier.height(12.dp))
        Btn("Notes and links", Modifier.fillMaxWidth(), BtnKind.Secondary, "link") { openLinks(s.track) }
        Spacer(Modifier.height(12.dp))

        Panel(Modifier.fillMaxWidth(), pad = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpIcon(if (dnd) "check" else "bell", 16.dp, if (dnd) C.brand else C.accent)
                Text(
                    if (dnd) "Notifications silenced" else "Notifications not silenced (grant Do Not Disturb access in Settings)",
                    color = if (dnd) C.brand else C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpIcon(if (pinned) "lock" else "close", 16.dp, if (pinned) C.brand else C.accent)
                Text(
                    if (pinned) "App pinned: other apps are blocked" else "App not pinned (turn on Pin windows in phone settings)",
                    color = if (pinned) C.brand else C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Btn("Stop and save", Modifier.fillMaxWidth(), BtnKind.Primary, "check") { confirm = 1 }
        Spacer(Modifier.height(10.dp))
        Btn("Discard without saving", Modifier.fillMaxWidth(), BtnKind.Ghost) { confirm = 2 }
        Spacer(Modifier.height(24.dp))
    }

    if (confirm != 0) {
        val save = confirm == 1
        SpDialog(
            if (save) "Stop and save?" else "Discard session?", { confirm = 0 },
            confirmText = if (save) "Stop and save" else "Discard", dismissText = "Keep studying",
            onConfirm = {
                confirm = 0
                vm.busyDo(if (save) "Saving your session" else "Closing your session") {
                    val ids = s.ids
                    e.finishSession(ctx.findActivity(), save)
                    if (save) vm.askCheckin(ids)
                }
            }
        ) {
            Text(
                if (save) "${fmtH(studiedMin)} will be saved to your topics and progress. The lock turns off."
                else "Nothing is saved. The lock turns off.",
                color = C.muted, fontSize = 15.sp, lineHeight = 24.sp
            )
        }
    }
}
