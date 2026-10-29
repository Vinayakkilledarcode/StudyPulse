package com.studypulse.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pulsy, the StudyPulse owl: a round, softly lit 3D-style mascot drawn entirely in code (no image files).
 * It bobs, blinks and flaps its wings. [cheer] lifts the wings up for happy moments such as Premium being active.
 */
@Composable
fun Mascot(size: Dp = 120.dp, cheer: Boolean = false, modifier: Modifier = Modifier, mood: String = "") {
    val t = rememberInfiniteTransition(label = "pulsy")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val flap by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (cheer) 420 else 1100, easing = LinearEasing), RepeatMode.Reverse), label = "flap")
    val blink by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 3400; 1f at 0; 1f at 3100; 0.08f at 3220; 1f at 3360 }),
        label = "blink"
    )
    val night = Night.on
    Canvas(modifier.size(size)) { drawPulsy(bob, flap, blink, cheer, night, mood) }
}

private fun DrawScope.sphere(c: Offset, r: Float, light: Color, mid: Color, dark: Color) {
    drawCircle(
        Brush.radialGradient(listOf(light, mid, dark), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 1.5f),
        radius = r, center = c
    )
}

private fun DrawScope.drawPulsy(bob: Float, flap: Float, blink: Float, cheer: Boolean, night: Boolean, mood: String) {
    val w = size.width
    fun p(x: Float, y: Float) = Offset(x * w, y * w)

    val green1 = if (night) Color(0xFFEFF2F4) else Color(0xFF7BD0A0)
    val green2 = if (night) Color(0xFF9CA4AC) else Color(0xFF3A8A62)
    val green3 = if (night) Color(0xFF4A5057) else Color(0xFF1B5239)
    val orange1 = if (night) Color(0xFFF4F6F8) else Color(0xFFFFA36B)
    val orange2 = if (night) Color(0xFFB4BBC2) else Color(0xFFE86F3A)

    // soft ground shadow that shrinks as the owl rises
    val sh = 1f - 0.12f * bob
    drawOval(
        Color(0x33000000),
        topLeft = p(0.5f - 0.27f * sh, 0.90f), size = Size(0.54f * sh * w, 0.07f * w)
    )

    translate(top = -0.035f * w * bob) {
        // wings sit behind the body
        val wingUp = if (cheer) -38f else -6f
        val ang = wingUp + (if (cheer) 26f else 14f) * flap
        rotate(ang, pivot = p(0.18f, 0.58f)) {
            drawOval(
                Brush.linearGradient(listOf(green2, green3), p(0.04f, 0.45f), p(0.2f, 0.75f)),
                topLeft = p(0.04f, 0.46f), size = Size(0.17f * w, 0.30f * w)
            )
        }
        rotate(-ang, pivot = p(0.82f, 0.58f)) {
            drawOval(
                Brush.linearGradient(listOf(green2, green3), p(0.96f, 0.45f), p(0.8f, 0.75f)),
                topLeft = p(0.79f, 0.46f), size = Size(0.17f * w, 0.30f * w)
            )
        }

        // feet
        drawOval(Brush.verticalGradient(listOf(orange1, orange2), 0.85f * w, 0.92f * w), p(0.34f, 0.86f), Size(0.13f * w, 0.06f * w))
        drawOval(Brush.verticalGradient(listOf(orange1, orange2), 0.85f * w, 0.92f * w), p(0.53f, 0.86f), Size(0.13f * w, 0.06f * w))

        // ear tufts
        sphere(p(0.27f, 0.27f), 0.085f * w, green1, green2, green3)
        sphere(p(0.73f, 0.27f), 0.085f * w, green1, green2, green3)

        // body
        sphere(p(0.5f, 0.57f), 0.36f * w, green1, green2, green3)

        // cream belly
        drawOval(
            Brush.radialGradient(listOf(if (night) Color(0xFFF4F6F8) else Color(0xFFFFFBEF), if (night) Color(0xFFC9CFD6) else Color(0xFFF2E4C4)), center = p(0.46f, 0.66f), radius = 0.26f * w),
            topLeft = p(0.29f, 0.58f), size = Size(0.42f * w, 0.32f * w)
        )
        // belly feather marks
        for (i in 0..2) {
            val x = 0.43f + 0.07f * i
            drawArc(
                Color(0x22A8864A), 200f, 140f, false,
                topLeft = p(x - 0.035f, 0.70f), size = Size(0.07f * w, 0.05f * w),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.012f * w)
            )
        }

        // eyes
        for (sx in listOf(0.36f, 0.64f)) {
            val c = p(sx, 0.47f)
            val r = 0.125f * w
            sphere(c, r, Color.White, Color(0xFFF1F4F2), Color(0xFFC9D3CD))
            scale(1f, blink, pivot = c) {
                val pc = Offset(c.x + (if (sx < 0.5f) 0.01f else -0.01f) * w, c.y + 0.012f * w)
                sphere(pc, 0.068f * w, Color(0xFF55645B), Color(0xFF1E2A23), Color(0xFF0B120E))
                drawCircle(Color.White, 0.022f * w, Offset(pc.x - 0.02f * w, pc.y - 0.024f * w))
                drawCircle(Color(0xCCFFFFFF), 0.01f * w, Offset(pc.x + 0.02f * w, pc.y + 0.018f * w))
            }
        }

        // brows: "sad" raises the inner ends, "focus" lowers them
        if (mood == "sad" || mood == "focus") {
            val low = if (mood == "sad") 0.40f else 0.36f
            val high = if (mood == "sad") 0.35f else 0.40f
            val brow = Color(0xFF0B120E)
            val bw = 0.018f * w
            drawLine(brow, p(0.26f, low), p(0.44f, high), strokeWidth = bw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(brow, p(0.56f, high), p(0.74f, low), strokeWidth = bw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }

        // cheeks
        val cheek = if (night) Color(0x44FFFFFF) else Color(0x55E86F3A)
        drawCircle(cheek, 0.045f * w, p(0.24f, 0.60f))
        drawCircle(cheek, 0.045f * w, p(0.76f, 0.60f))

        // beak
        val beak = Path().apply {
            moveTo(0.455f * w, 0.555f * w)
            quadraticBezierTo(0.5f * w, 0.535f * w, 0.545f * w, 0.555f * w)
            quadraticBezierTo(0.51f * w, 0.625f * w, 0.5f * w, 0.63f * w)
            quadraticBezierTo(0.49f * w, 0.625f * w, 0.455f * w, 0.555f * w)
            close()
        }
        drawPath(beak, Brush.verticalGradient(listOf(orange1, orange2), 0.54f * w, 0.63f * w))

        // graduation cap
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFF33443B), Color(0xFF17211B)), 0.20f * w, 0.30f * w),
            p(0.34f, 0.205f), Size(0.32f * w, 0.10f * w)
        )
        val board = Path().apply {
            moveTo(0.5f * w, 0.095f * w)
            lineTo(0.78f * w, 0.185f * w)
            lineTo(0.5f * w, 0.265f * w)
            lineTo(0.22f * w, 0.185f * w)
            close()
        }
        drawPath(board, Brush.linearGradient(listOf(Color(0xFF4A6155), Color(0xFF17211B)), p(0.3f, 0.1f), p(0.7f, 0.27f)))
        drawCircle(orange2, 0.014f * w, p(0.5f, 0.18f))
        drawLine(orange2, p(0.5f, 0.18f), p(0.77f, 0.20f), strokeWidth = 0.011f * w)
        drawLine(orange2, p(0.77f, 0.20f), p(0.78f, 0.29f), strokeWidth = 0.011f * w)
        drawCircle(orange1, 0.02f * w, p(0.78f, 0.30f))

        // glossy highlight on the head
        drawOval(
            Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), center = p(0.34f, 0.38f), radius = 0.1f * w),
            topLeft = p(0.24f, 0.31f), size = Size(0.2f * w, 0.12f * w)
        )
    }
}
