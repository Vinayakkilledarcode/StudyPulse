package com.studypulse.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.ZonedDateTime

object Reminders {
    private const val CH = "study_reminders"
    private const val ACT = "com.studypulse.app.REMIND"

    private fun channel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (m.getNotificationChannel(CH) == null) {
                val ch = NotificationChannel(CH, "Study reminders", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "Daily study reminders and timer alerts"
                m.createNotificationChannel(ch)
            }
        }
    }

    private fun nextMillis(minOfDay: Int): Long {
        val now = ZonedDateTime.now()
        var t = now.toLocalDate().atTime(minOfDay / 60, minOfDay % 60).atZone(now.zone)
        if (!t.isAfter(now.plusSeconds(1))) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    private fun pi(c: Context, kind: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code,
            Intent(c, ReminderReceiver::class.java).setAction(ACT).putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(c: Context, at: Long, p: PendingIntent) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private val DEFAULT_TIMES = listOf(7 * 60, 20 * 60 + 30, 12 * 60, 17 * 60, 21 * 60 + 30)

    /** Default reminder time for the track at [index] in the plan. */
    fun defaultTime(index: Int): Int = DEFAULT_TIMES[index % DEFAULT_TIMES.size]

    private fun cancelTracks(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 12) am.cancel(pi(c, "T", 100 + i))
    }

    fun cancelAll(c: Context) {
        cancelTracks(c)
        cancelEnd(c)
        cancelReturn(c)
    }

    fun scheduleAll(c: Context) {
        cancelTracks(c)
        if (!Account.signedIn(c)) return
        val e = Engine.get(c)
        if (!e.store.notifOn) return
        for ((i, t) in Plan.tracks.withIndex().take(12)) {
            setAlarm(c, nextMillis(e.store.remind(t.id, defaultTime(i))), pi(c, t.id, 100 + i))
        }
    }

    fun scheduleEnd(c: Context, at: Long) = setAlarm(c, at, pi(c, "END", 3))
    fun cancelEnd(c: Context) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, "END", 3))
        NotificationManagerCompat.from(c).cancel(30)
    }
    fun cancelReturn(c: Context) = NotificationManagerCompat.from(c).cancel(31)

    private fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun post(c: Context, id: Int, title: String, text: String) {
        channel(c)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(c))
            .build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (_: SecurityException) {}
    }

    fun test(c: Context) =
        post(c, 99, "Time to study", "Test: your topic appears here")

    fun postReturn(c: Context) =
        post(c, 31, "Study session is running", "Tap to go back to your timer")

    fun fire(c: Context, kind: String) {
        val e = Engine.get(c)
        when (kind) {
            "END" -> post(c, 30, "Study time is up", "Open StudyPulse and tap Stop to save your session")
            else -> {
                val idx = Plan.trackIndex(kind)
                if (idx < 99 && Account.signedIn(c)) {
                    val due = e.dueToday(kind)
                    if (due.isNotEmpty()) {
                        val left = due.sumOf { e.left(it) }
                        post(
                            c, 10 + idx,
                            "Time to study " + trackName(kind),
                            due.first().text + "  (" + fmtH(left) + " left today)"
                        )
                    }
                }
                scheduleAll(c)
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.fire(c, i.getStringExtra("kind") ?: return)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.scheduleAll(c)
    }
}
