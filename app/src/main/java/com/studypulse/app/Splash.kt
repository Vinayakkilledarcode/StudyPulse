package com.studypulse.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening animation shown every time the app is opened: the logo pops in, two rings expand
 * and fade, the name rises in, then the whole screen fades into the app.
 */
@Composable
fun OpeningSplash(onDone: () -> Unit) {
    val core = remember { Animatable(0f) }
    val orbitA = remember { Animatable(0f) }
    val orbitB = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val none = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { core.animateTo(1f, tween(750, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1.2f))) }
            launch { orbitA.animateTo(1f, tween(1350, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f))) }
            launch {
                delay(80)
                orbitB.animateTo(1f, tween(1350, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)))
            }
            launch {
                delay(450)
                word.animateTo(1f, tween(450, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)))
            }
            launch {
                delay(1700)
                exit.animateTo(1f, tween(550, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
            }
        }
        onDone()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                scaleX = 1f + 0.04f * exit.value
                scaleY = 1f + 0.04f * exit.value
            }
            .background(C.bg)
            .clickable(interactionSource = none, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        AmbientGlow(core.value)
        Orbit(110, orbitA.value)
        Orbit(160, orbitB.value)

        val c = core.value
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .graphicsLayer {
                    val sc = 0.2f + 0.8f * c
                    scaleX = sc
                    scaleY = sc
                    rotationZ = -18f * (1f - c)
                    alpha = c.coerceIn(0f, 1f)
                }
        ) {
            Mascot(size = 124.dp)
        }

        Box(
            Modifier
                .offset(y = (108 + 10 * (1f - word.value)).dp)
                .graphicsLayer { alpha = word.value }
        ) {
            Head("StudyPulse", 22)
        }
    }
}

@Composable
private fun Orbit(dia: Int, t: Float) {
    // opacity: 0 -> 1 over the first 45%, then back to 0; scale grows from .25 to 1.25
    val a = if (t < 0.45f) t / 0.45f else 1f - (t - 0.45f) / 0.55f
    Box(
        Modifier
            .size(dia.dp)
            .graphicsLayer {
                val sc = 0.25f + 1.0f * t
                scaleX = sc
                scaleY = sc
                alpha = a.coerceIn(0f, 1f)
            }
            .border(2.dp, nt(Color(0x2E286246), Color(0x40CDD2D7)), CircleShape)
    )
}

/** Soft blue, violet, green and orange light that drifts and breathes behind the logo. */
@Composable
fun AmbientGlow(fade: Float) {
    val t = rememberInfiniteTransition(label = "glow")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.coerceIn(0f, 1f) }) {
        val w = size.width
        val h = size.height
        val r = size.minDimension * (0.55f + 0.12f * pulse)
        fun blob(color: Color, cx: Float, cy: Float, rad: Float) {
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = rad), radius = rad, center = Offset(cx, cy))
        }
        blob(nt(Color(0x554285F4), Color(0x33FFFFFF)), w * (0.25f + 0.10f * drift), h * 0.35f, r)
        blob(nt(Color(0x559B72CB), Color(0x26CDD2D7)), w * (0.80f - 0.10f * drift), h * 0.45f, r * 0.9f)
        blob(nt(Color(0x5534A853), Color(0x2EE6E9EC)), w * 0.50f, h * (0.70f + 0.05f * drift), r * 0.8f)
        blob(nt(Color(0x44E86F3A), Color(0x1FB4BBC2)), w * (0.65f - 0.10f * drift), h * 0.20f, r * 0.6f)
    }
}
