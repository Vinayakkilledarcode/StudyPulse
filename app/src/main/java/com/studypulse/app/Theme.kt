package com.studypulse.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.time.LocalDate
import java.util.Locale

/** Design tokens. */
object C {
    private fun p(d: Long, n: Long): Color = Color(if (Night.on) n else d)
    val ink: Color get() = p(0xFF17211B, 0xFFE6E9EC)
    val muted: Color get() = p(0xFF69746D, 0xFF8E959C)
    val bg: Color get() = p(0xFFF4F6F2, 0xFF08090A)
    val card: Color get() = p(0xFFFFFFFF, 0xFF121416)
    val card2: Color get() = p(0xFFF4F6F2, 0xFF08090A)
    val line: Color get() = p(0xFFE1E7DF, 0xFF2B2F33)
    /** Brand colour used for text, icons and outlines. */
    val brand: Color get() = p(0xFF286246, 0xFFCDD2D7)
    /** Brand colour used as a filled background (white text sits on it). */
    val fill: Color get() = p(0xFF286246, 0xFF2A2E33)
    /** Main button background and its text colour. */
    val btn: Color get() = p(0xFF286246, 0xFFD5D9DD)
    val onBtn: Color get() = p(0xFFFFFFFF, 0xFF0B0C0D)
    val brandSoft: Color get() = p(0xFFE4F1E9, 0xFF1A1D20)
    val accent: Color get() = p(0xFFE86F3A, 0xFFB4BBC2)
    val warm: Color get() = p(0xFFFFFAF5, 0xFF14161A)
    val blue: Color get() = p(0xFF37608C, 0xFFB8C0C8)
    val blueSoft: Color get() = p(0xFFE5EDF8, 0xFF181B1F)
    val violet: Color get() = p(0xFF79578D, 0xFFA9AFB6)
    val violetSoft: Color get() = p(0xFFEEE7F3, 0xFF1C1F23)
    val toggleOff: Color get() = p(0xFFCBD2CE, 0xFF3A3E43)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int): Font =
    Font(res, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight(w), FontStyle.Normal))

val DM = FontFamily(
    variable(R.font.dmsans, 400), variable(R.font.dmsans, 500),
    variable(R.font.dmsans, 600), variable(R.font.dmsans, 700), variable(R.font.dmsans, 800)
)
val MX = FontFamily(
    variable(R.font.manrope, 600), variable(R.font.manrope, 700), variable(R.font.manrope, 800)
)

fun spTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = DM), displayMedium = b.displayMedium.copy(fontFamily = DM),
        displaySmall = b.displaySmall.copy(fontFamily = DM), headlineLarge = b.headlineLarge.copy(fontFamily = DM),
        headlineMedium = b.headlineMedium.copy(fontFamily = DM), headlineSmall = b.headlineSmall.copy(fontFamily = DM),
        titleLarge = b.titleLarge.copy(fontFamily = DM), titleMedium = b.titleMedium.copy(fontFamily = DM),
        titleSmall = b.titleSmall.copy(fontFamily = DM), bodyLarge = b.bodyLarge.copy(fontFamily = DM),
        bodyMedium = b.bodyMedium.copy(fontFamily = DM), bodySmall = b.bodySmall.copy(fontFamily = DM),
        labelLarge = b.labelLarge.copy(fontFamily = DM), labelMedium = b.labelMedium.copy(fontFamily = DM),
        labelSmall = b.labelSmall.copy(fontFamily = DM)
    )
}

fun spColors() = if (Night.on) darkColorScheme(
    primary = C.btn, onPrimary = C.onBtn,
    secondary = C.btn, onSecondary = C.onBtn,
    primaryContainer = C.brandSoft, onPrimaryContainer = C.brand,
    background = C.bg, onBackground = C.ink,
    surface = C.card, onSurface = C.ink, surfaceVariant = C.bg, onSurfaceVariant = C.muted,
    surfaceContainerHigh = C.card, surfaceContainerHighest = C.card, surfaceContainer = C.card,
    surfaceContainerLow = C.card, surfaceContainerLowest = C.card,
    outline = C.line, outlineVariant = C.line
) else lightColorScheme(
    primary = C.brand, onPrimary = Color.White,
    secondary = C.brand, onSecondary = Color.White,
    primaryContainer = C.brandSoft, onPrimaryContainer = C.brand,
    background = C.bg, onBackground = C.ink,
    surface = C.card, onSurface = C.ink, surfaceVariant = C.bg, onSurfaceVariant = C.muted,
    surfaceContainerHigh = C.card, surfaceContainerHighest = C.card, surfaceContainer = C.card,
    surfaceContainerLow = C.card, surfaceContainerLowest = C.card,
    outline = C.line, outlineVariant = C.line
)

// ----------------------------------------------------------------------- formatting

fun fmtH(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
}

/** "4 hours", "1 hour 30 min", "45 min". */
fun fmtLong(min: Int): String {
    val h = min / 60
    val m = min % 60
    val hs = if (h == 1) "1 hour" else "$h hours"
    return if (h == 0) "$m min" else if (m == 0) hs else "$hs $m min"
}

fun fmtClock(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

fun hms(sec: Long): String {
    val s = if (sec < 0) -sec else sec
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

/** Icon, tile colour and icon colour for a track, by its position in the plan. */
fun trackStyle(track: String): Triple<String, Color, Color> = when (Plan.trackIndex(track) % 4) {
    0 -> Triple("book", C.blueSoft, C.blue)
    1 -> Triple("chart", C.violetSoft, C.violet)
    2 -> Triple("doc", C.brandSoft, C.brand)
    else -> Triple("clock", C.warm, C.accent)
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

fun monthName(d: LocalDate): String = d.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

fun fmtEyebrowDay(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ", " + d.dayOfMonth + " " + monthName(d)
}

fun fmtFullDate(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return d.dayOfMonth.toString() + " " + monthName(d) + " " + d.year
}

// ----------------------------------------------------------------------- text

/** Manrope heading. */
@Composable
fun Head(
    t: String, size: Int, color: Color = C.ink, mod: Modifier = Modifier,
    weight: FontWeight = FontWeight.ExtraBold, tracking: Double = -0.03, align: TextAlign? = null
) {
    Text(
        t, color = color, fontFamily = MX, fontWeight = weight, fontSize = size.sp,
        lineHeight = (size * 1.18).sp, letterSpacing = tracking.em, modifier = mod, textAlign = align
    )
}

/** Small uppercase label (eyebrows, section labels). */
@Composable
fun Label(t: String, color: Color = C.muted, mod: Modifier = Modifier, size: Int = 11) =
    Text(
        t.uppercase(), color = color, fontSize = size.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.08.em, modifier = mod
    )

// ----------------------------------------------------------------------- surfaces

@Composable
fun Panel(
    mod: Modifier = Modifier, border: Color = C.line, bg: Color = C.card,
    onClick: (() -> Unit)? = null, pad: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit
) {
    val s = RoundedCornerShape(20.dp)
    var m = mod.clip(s).background(bg).border(1.dp, border, s)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(pad), content = content)
}

@Composable
fun IconTile(icon: String, bg: Color, fg: Color, size: Dp = 44.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) { SpIcon(icon, iconSize, fg) }
}

@Composable
fun BrandMark(size: Dp = 36.dp, radius: Dp = 11.dp, iconSize: Dp = 19.dp, gradient: Boolean = false) {
    val m = if (gradient) Modifier.background(Brush.linearGradient(listOf(nt(Color(0xFF387A58), Color(0xFF3A3F45)), nt(Color(0xFF204F39), Color(0xFF16181B)))))
    else Modifier.background(C.fill)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).then(m),
        contentAlignment = Alignment.Center
    ) { SpIcon("book", iconSize, nt(Color.White, Color(0xFFE6E9EC))) }
}

@Composable
fun Bar(frac: Float, color: Color = C.brand, mod: Modifier = Modifier) {
    val f = frac.coerceIn(0f, 1f)
    Box(mod.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(C.bg)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun Ring(
    frac: Float, dia: Dp, stroke: Dp, track: Color, color: Color,
    content: @Composable BoxScope.() -> Unit
) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val tl = Offset(sw / 2f, sw / 2f)
            val d = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, tl, d, style = Stroke(sw))
            val f = frac.coerceIn(0f, 1f)
            if (f > 0f) drawArc(color, -90f, 360f * f, false, tl, d, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

// ----------------------------------------------------------------------- buttons

enum class BtnKind { Primary, Secondary, Ghost, OnBrand }

@Composable
fun Btn(
    text: String, mod: Modifier = Modifier, kind: BtnKind = BtnKind.Primary,
    icon: String? = null, onClick: () -> Unit
) {
    val bg = when (kind) {
        BtnKind.Primary -> C.btn
        BtnKind.Secondary -> C.brandSoft
        BtnKind.Ghost -> C.bg
        BtnKind.OnBrand -> Color.White.copy(alpha = 0.14f)
    }
    val fg = when (kind) {
        BtnKind.Primary -> C.onBtn
        BtnKind.Secondary -> C.brand
        BtnKind.Ghost -> C.ink
        BtnKind.OnBrand -> Color.White
    }
    Row(
        mod.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SpIcon(icon, 17.dp, fg)
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Full-width button used by the reading screens. */
@Composable
fun BigBtn(
    t: String, mod: Modifier = Modifier, bg: Color = C.btn, fg: Color = C.onBtn,
    onClick: () -> Unit
) {
    Box(
        mod.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(t, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Pill(
    t: String, fg: Color = C.ink, bg: Color = C.card, border: Color = C.line,
    mod: Modifier = Modifier, onClick: (() -> Unit)? = null
) {
    val s = RoundedCornerShape(50)
    var m = mod.clip(s).background(bg).border(1.dp, border, s)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Text(
        t, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = m.padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun SwitchKnob(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) C.btn else C.toggleOff, tween(200), label = "track")
    val x by animateDpAsState(if (on) 22.dp else 0.dp, tween(200), label = "knob")
    Box(
        Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(50)).background(track)
            .clickable { onChange(!on) }.padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = x).size(24.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
        )
    }
}

/** A settings row: title and caption on the left, switch on the right. */
@Composable
fun Toggle(label: String, sub: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Head(label, 16, weight = FontWeight.Bold, tracking = -0.01)
            if (sub.isNotEmpty()) Text(sub, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SwitchKnob(v, onChange)
    }
}

@Composable
fun tfColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.ink, unfocusedTextColor = C.ink,
    focusedContainerColor = C.card, unfocusedContainerColor = C.card,
    focusedBorderColor = C.brand, unfocusedBorderColor = C.line, cursorColor = C.brand
)

// ----------------------------------------------------------------------- dialogs

/** Bottom sheet style dialog: soft scrim, rounded sheet, title and close button. */
@Composable
fun Modal(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(
        if (shown) 1f else 0f, tween(340, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)), label = "modal"
    )
    val view = LocalView.current
    SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
    val none = remember { MutableInteractionSource() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(nt(Color(0xFF0E1812), Color(0xFF000000)).copy(alpha = 0.52f * t))
                .clickable(interactionSource = none, indication = null) { onClose() }
        ) {
            val maxH = maxHeight * 0.88f
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(14.dp)
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = t
                        translationY = (1f - t) * 18.dp.toPx()
                        scaleX = 0.97f + 0.03f * t
                        scaleY = 0.97f + 0.03f * t
                    }
                    .shadow(30.dp, shape, ambientColor = Color(0x400D1B12), spotColor = Color(0x400D1B12))
                    .clip(shape)
                    .background(C.card)
                    .clickable(interactionSource = none, indication = null) { }
                    .heightIn(max = maxH)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 22.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Label("StudyPulse", C.brand, size = 12)
                        Spacer(Modifier.height(8.dp))
                        Head(title, 24)
                    }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(C.bg).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) { SpIcon("close", 18.dp, C.muted) }
                }
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

/** Confirm / cancel dialog built on [Modal]. */
@Composable
fun SpDialog(
    title: String, onDismiss: () -> Unit,
    border: Color = C.line,
    confirmText: String = "Close", onConfirm: () -> Unit = onDismiss,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Modal(title, onDismiss) {
        content()
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(confirmText, Modifier.fillMaxWidth()) { onConfirm() }
            if (dismissText != null) Btn(dismissText, Modifier.fillMaxWidth(), BtnKind.Secondary) { onDismiss() }
        }
    }
}

/** Calendar date picker. [initialEpoch] and the result are epoch days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChooser(initialEpoch: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpoch * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onPick(Math.floorDiv(ms, 86_400_000L))
                onDismiss()
            }) { Text("Select date", color = C.brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.muted, fontWeight = FontWeight.Bold) } }
    ) { DatePicker(state = state) }
}
