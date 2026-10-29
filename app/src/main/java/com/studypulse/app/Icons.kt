package com.studypulse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons on a 24 x 24 grid, drawn as strokes so they stay crisp at any size. */
private val ICONS: Map<String, List<String>> = mapOf(
    "home" to listOf("m3 10 9-7 9 7", "M5 9v11h14V9M9 20v-7h6v7"),
    "calendar" to listOf(
        "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Z",
        "M16 3v4M8 3v4M3 10h18"
    ),
    "settings" to listOf(
        "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z",
        "M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63 1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.56 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15Z"
    ),
    "clock" to listOf("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18Z", "M12 7v5l3 2"),
    "book" to listOf("M4 5a3 3 0 0 1 3-3h4v17H7a3 3 0 0 0-3 3V5ZM20 5a3 3 0 0 0-3-3h-4v17h4a3 3 0 0 1 3 3V5Z"),
    "chart" to listOf("M4 19V9M10 19V5M16 19v-7M22 19H2"),
    "play" to listOf("m9 7 8 5-8 5V7Z"),
    "check" to listOf("m5 12 4 4L19 6"),
    "bell" to listOf("M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"),
    "chevL" to listOf("m15 18-6-6 6-6"),
    "chevR" to listOf("m9 18 6-6-6-6"),
    "close" to listOf("M18 6 6 18M6 6l12 12"),
    "plus" to listOf("M12 5v14M5 12h14"),
    "minus" to listOf("M5 12h14"),
    "doc" to listOf("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z", "M14 3v5h5M9 13h6M9 17h6"),
    "link" to listOf(
        "M10 14a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1.5 1.5",
        "M14 10a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1.5-1.5"
    ),
    "reload" to listOf("M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7"),
    "trash" to listOf("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"),
    "lock" to listOf("M6 11h12v9H6Z", "M8 11V8a4 4 0 0 1 8 0v3"),
    "moon" to listOf("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z"),
    "user" to listOf("M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z", "M20 21v-1a5 5 0 0 0-5-5H9a5 5 0 0 0-5 5v1"),
    "upload" to listOf("M12 16V4M7 9l5-5 5 5M4 20h16"),
    "logout" to listOf("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"),
    "phone" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z", "M11 18h2"),
    "mail" to listOf("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z", "m3 7 9 6 9-6"),
    "edit" to listOf("M4 20h4L19 9l-4-4L4 16v4Z", "m13 7 4 4")
)

@Composable
fun SpIcon(
    name: String,
    size: Dp = 20.dp,
    tint: Color = C.ink,
    stroke: Float = 1.8f,
    modifier: Modifier = Modifier
) {
    val paths = remember(name) {
        (ICONS[name] ?: emptyList()).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            for (p in paths) {
                drawPath(p, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
