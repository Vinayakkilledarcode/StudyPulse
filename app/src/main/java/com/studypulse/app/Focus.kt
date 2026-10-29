package com.studypulse.app

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Focus lock for a normal app (no root, no device owner). Android only allows two real tools:
 *  1) Do Not Disturb in Priority mode: other apps' notifications are silenced.
 *  2) Screen pinning (startLockTask): Home, Recents and the notification shade stop working
 *     until the app is unpinned, so no other app can be opened.
 * The app re-pins itself if you escape, and keeps the lock until you tap STOP.
 */
object FocusGuard {
    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun dndGranted(c: Context): Boolean = nm(c).isNotificationPolicyAccessGranted

    fun openDndSettings(c: Context) {
        try {
            c.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
    }

    fun isPinned(c: Context): Boolean =
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun engage(c: Context, a: Activity?, s: Store) {
        if (!s.lockOn) return
        if (s.lockDnd && dndGranted(c)) {
            val n = nm(c)
            if (s.dndPrev == -1) s.dndPrev = n.currentInterruptionFilter
            n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        if (s.lockPin && a != null) ensurePinned(a, s)
    }

    fun ensurePinned(a: Activity, s: Store) {
        if (!s.lockOn || !s.lockPin || isPinned(a)) return
        try { a.startLockTask(); s.pinFlag = true } catch (_: Throwable) {}
    }

    fun release(c: Context, a: Activity?, s: Store) {
        if (a != null && isPinned(a)) {
            try { a.stopLockTask() } catch (_: Throwable) {}
        }
        s.pinFlag = false
        restoreDnd(c, s)
    }

    /** Puts Do Not Disturb back exactly as it was before the session. Also used after a crash. */
    fun restoreDnd(c: Context, s: Store) {
        val prev = s.dndPrev
        if (prev == -1) return
        val n = nm(c)
        if (n.isNotificationPolicyAccessGranted) {
            n.setInterruptionFilter(if (prev in 1..4) prev else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        s.dndPrev = -1
    }
}
