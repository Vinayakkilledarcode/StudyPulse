package com.studypulse.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Night mode: gothic black and silver only. Saved on the phone and applied everywhere through the C colours. */
object Night {
    var on by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("studypulse_ui", Context.MODE_PRIVATE)

    fun load(c: Context) { on = prefs(c).getBoolean("night", false) }

    fun set(c: Context, v: Boolean) {
        on = v
        prefs(c).edit().putBoolean("night", v).apply()
    }
}

/** [day] colour in the normal theme, [night] colour in night mode. */
fun nt(day: Color, night: Color): Color = if (Night.on) night else day
