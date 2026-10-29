package com.studypulse.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Every action shows the busy screen for at least this long, even when the work finishes sooner. Change it here. */
const val BUSY_MIN_MS = 1800L

/** One step of the task log. state: 0 running, 1 done, 2 failed, 3 detail. */
data class BusyLine(val text: String, val state: Int, val at: Long, val ms: Long)

/** How the slow parts of the app report what they are doing. Callbacks may run on any thread. */
class Trace(val step: (String) -> Unit, val note: (String) -> Unit) {
    companion object { val NONE = Trace({ }, { }) }
}

/** Only these actions use the silver hovering-logo animation; every other action shows the step-by-step task card with the spinning logo. */
private val CLASSIC = setOf("Signing in", "Signing out", "Sending your code")

/** True for the actions that keep the full-screen silver animation (the app behind is blurred only for these). */
fun isClassicBusy(label: String) = label in CLASSIC

/** Tasks of the upload screen: their steps are shown on that page itself (under the title), not floating over it. */
private val INLINE = setOf("Reading your plan", "Building your schedule", "Building your plan", "Finding your video")
fun isInlineBusy(label: String) = label in INLINE

/**
 * One line, in the strip under the Back button where the page title sits: the newest step or detail of the task.
 * Each new line fades in after the one before it, with the logo turning until the task is finished.
 */
@Composable
fun InlineTaskLine(lines: List<BusyLine>) {
    val cur = lines.lastOrNull()
    val failed = lines.any { it.state == 2 }
    val done = !failed && lines.any { it.text == "Done" }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cur != null) androidx.compose.runtime.key(lines.size) {
            Reveal {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            failed -> SpIcon("close", 20.dp, Color(0xFFE86F3A), 2.2f)
                            done -> SpIcon("check", 20.dp, C.brand, 2.2f)
                            else -> SpinLogo(24.dp, 7.dp, 14.dp, true)
                        }
                    }
                    Text(
                        cur.text, color = if (cur.state == 3) C.muted else C.ink, fontSize = 15.sp,
                        fontWeight = if (cur.state == 3) FontWeight.Normal else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Full-screen busy animation: the app behind goes dark (and blurred on Android 12+), the StudyPulse logo hovers in the middle,
 * one corner of it lights up and the light spreads until the whole logo is bright, then it starts again from the same corner.
 * Under it the action's name is typed out letter by letter and deleted again, over and over, until the action is done.
 */
@Composable
fun BusyOverlay(label: String, lines: List<BusyLine>) {
    val shown = label.isNotEmpty()
    var last by remember { mutableStateOf("") }
    LaunchedEffect(label) { if (label.isNotEmpty()) last = label }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "busyFade")
    if (a <= 0.01f && !shown) return
    val none = remember { MutableInteractionSource() }
    val cur = if (label.isNotEmpty()) label else last
    val classic = cur in CLASSIC
    if (classic) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = a }
                .background(Color(0xE6050806))
                .clickable(interactionSource = none, indication = null) { },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-10).dp)) {
                ShimmerLogo(104.dp)
                TypingLabel(cur)
            }
        }
    } else if (!isInlineBusy(cur)) {
        // No dimming and no blur: the page stays as it is and a small card on it lists every step of the task.
        // The invisible layer only stops stray taps while the task runs.
        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = none, indication = null) { },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.graphicsLayer { alpha = a }.statusBarsPadding().padding(top = 58.dp, start = 14.dp, end = 14.dp)) {
                TaskCard(cur, lines)
            }
        }
    }
    // Upload-page tasks draw nothing here: their single-line steps are shown in the page itself (InlineTaskLine).
}

@Composable
private fun ShimmerLogo(size: Dp) {
    val t = rememberInfiniteTransition(label = "busyLogo")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "hover")
    // the light starts in the top-left corner, spreads over the whole logo, holds, then fades back to black and starts again
    val edge = if (p < 0.8f) (p / 0.8f) * 1.25f - 0.12f else 1.13f
    val lit = if (p < 0.9f) 1f else 1f - (p - 0.9f) / 0.1f
    val shape = RoundedCornerShape(32.dp)
    val ink = Color(0xFF0B0D0F)
    Box(Modifier.size(size * 2f).offset(y = (-6 * bob).dp), contentAlignment = Alignment.Center) {
        // silver glow around the logo, as strong as the part that is lit
        Canvas(Modifier.matchParentSize()) {
            val a = edge.coerceIn(0f, 1f) * lit
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFE3E7EC).copy(alpha = 0.50f * a), Color(0xFF9AA3AD).copy(alpha = 0.20f * a), Color.Transparent),
                    center = center, radius = this.size.minDimension / 2f
                ),
                radius = this.size.minDimension / 2f, center = center
            )
        }
        // the logo in black
        Box(Modifier.size(size).clip(shape).background(Color(0xFF121417)).border(1.5.dp, Color(0xFF3A4046), shape), contentAlignment = Alignment.Center) {
            SpIcon("book", 50.dp, Color(0xFF4A5056))
        }
        // the same logo in white and silver, revealed from the corner
        Box(
            Modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = lit }
                .drawWithContent {
                    drawContent()
                    val w = this.size.width
                    val h = this.size.height
                    val from = Offset(0f, 0f)
                    val to = Offset(w, h)
                    val s0 = (edge - 0.22f).coerceIn(0f, 1f)
                    val s1 = (edge - 0.08f).coerceIn(0f, 1f)
                    val s2 = edge.coerceIn(0f, 1f)
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, s0 to Color.Transparent, s1 to Color(0xCCFFFFFF), s2 to Color.Transparent, 1f to Color.Transparent,
                            start = from, end = to
                        ),
                        blendMode = BlendMode.SrcAtop
                    )
                    val m0 = (edge - 0.10f).coerceIn(0f, 1f)
                    val m1 = edge.coerceIn(0f, 1f)
                    drawRect(
                        Brush.linearGradient(0f to Color.Black, m0 to Color.Black, m1 to Color.Transparent, 1f to Color.Transparent, start = from, end = to),
                        blendMode = BlendMode.DstIn
                    )
                }
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFC9CFD6)))),
            contentAlignment = Alignment.Center
        ) {
            SpIcon("book", 50.dp, ink)
        }
    }
}

/** Types the text one letter at a time, holds it, deletes it letter by letter, and repeats. */
@Composable
private fun TypingLabel(text: String) {
    var n by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (true) {
            while (n < text.length) { delay(85); n++ }
            delay(700)
            while (n > 0) { delay(45); n-- }
            delay(350)
        }
    }
    val t = rememberInfiniteTransition(label = "caret")
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "caret")
    Row(Modifier.height(30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text.take(n), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(Modifier.width(2.dp).height(22.dp).graphicsLayer { alpha = if (c > 0.5f) 1f else 0f }.background(Color.White))
    }
}

/** The app logo, small, turning round while [spin] is true. */
@Composable
private fun SpinLogo(size: Dp, radius: Dp, icon: Dp, spin: Boolean) {
    val t = rememberInfiniteTransition(label = "logoSpin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    Box(Modifier.graphicsLayer { rotationZ = if (spin) r else 0f }) { BrandMark(size = size, radius = radius, iconSize = icon) }
}

/** Each new line fades and slides in when it appears, so the steps show up one after another. */
@Composable
private fun Reveal(content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(320), label = "rowIn")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 14f }) { content() }
}

private fun secs(ms: Long) = String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)

/**
 * The task card: the logo turns while the task runs, the name of the task sits next to it with an arrow, and under it every
 * step is listed as it happens, with the details that were read (pages, characters, topics found, and so on).
 */
@Composable
private fun TaskCard(title: String, lines: List<BusyLine>, inline: Boolean = false) {
    var open by remember { mutableStateOf(true) }
    val running = lines.any { it.state == 0 }
    val arrow by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "arrow")
    val shape = RoundedCornerShape(18.dp)
    val sc = rememberScrollState()
    LaunchedEffect(lines.size, open) {
        kotlinx.coroutines.yield()
        sc.animateScrollTo(sc.maxValue)
    }
    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth()
            .clip(shape).background(Color(0xF2121416)).border(1.dp, Color(0xFF34393F), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpinLogo(24.dp, 7.dp, 14.dp, running)
            Text(
                title, color = Color(0xFFE6E9EC), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Box(Modifier.graphicsLayer { rotationZ = arrow }) { SpIcon("chevR", 16.dp, Color(0xFF8E959C)) }
        }
        if (open && lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                if (inline) Modifier else Modifier.heightIn(max = 300.dp).verticalScroll(sc),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                for (l in lines) Reveal {
                    when (l.state) {
                        3 -> Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("\u2022", color = Color(0xFF5E666D), fontSize = 12.sp)
                            Text(l.text, color = Color(0xFF9AA3AD), fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        else -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.width(20.dp).height(20.dp), contentAlignment = Alignment.Center) {
                                when (l.state) {
                                    0 -> SpinLogo(16.dp, 5.dp, 10.dp, true)
                                    2 -> SpIcon("close", 16.dp, Color(0xFFE86F3A), 2.2f)
                                    else -> SpIcon("check", 16.dp, Color(0xFF7FC8A0), 2.2f)
                                }
                            }
                            Text(
                                l.text, color = if (l.state == 0) Color(0xFFE6E9EC) else Color(0xFFB4BBC2),
                                fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (l.state != 0 && l.ms >= 100L) Text(secs(l.ms), color = Color(0xFF6B737A), fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}
