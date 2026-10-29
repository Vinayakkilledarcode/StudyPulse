package com.studypulse.app

import android.app.Activity
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.TreeMap

class Session(val start: Long, val planMin: Int, val track: String, val ids: List<String>)

fun fmtDay(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.dayOfMonth + " " +
        d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

fun fmtDate(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return d.dayOfMonth.toString() + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

/** All plan logic. Used by the screens and by the reminder receiver, so it holds no UI state. */
class Engine private constructor(val c: Context, val key: String) {
    val store: Store = Store.get(c)

    init { Plan.load(c) }

    companion object {
        @Volatile private var inst: Engine? = null
        /** The engine of the signed in account. */
        fun get(c: Context): Engine {
            val key = Account.key(c)
            val cur = inst
            if (cur != null && cur.key == key) return cur
            return synchronized(this) {
                val again = inst
                if (again != null && again.key == key) again
                else Engine(c.applicationContext, key).also { inst = it }
            }
        }
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    // ---------- topics ----------
    fun sched(i: Item): Long = store.ov(i.id) ?: (i.day.baseEpoch + store.offset)
    fun mins(i: Item): Int = store.mOv(i.id) ?: i.minutes
    fun isDone(i: Item): Boolean = store.done(i.id)
    fun left(i: Item): Int = if (isDone(i)) 0 else maxOf(0, mins(i) - store.studied(i.id))

    private val order = compareBy<Item>({ Plan.trackIndex(it.track) }, { it.day.n }, { it.id })

    fun itemsOn(epoch: Long): List<Item> = Plan.items.filter { sched(it) == epoch }.sortedWith(order)
    fun dueToday(track: String): List<Item> = itemsOn(today()).filter { it.track == track && !isDone(it) }
    fun overdue(): List<Item> = Plan.items.filter { !isDone(it) && sched(it) < today() }
        .sortedWith(compareBy<Item>({ sched(it) }, { it.day.n }, { it.id }))

    fun planDay(): Int = (today() - (Plan.baseStart + store.offset)).toInt() + 1
    fun planTotal(): Int = Plan.days.size

    // ---------- progress ----------
    fun plannedMin(epoch: Long): Int = itemsOn(epoch).sumOf { mins(it) }
    fun studiedMin(epoch: Long): Int = Plan.tracks.sumOf { store.logOf(epoch, it.id) }
    fun streak(): Int {
        var d = today()
        if (studiedMin(d) == 0) d--
        var n = 0
        while (studiedMin(d) > 0 && n < 4000) { n++; d-- }
        return n
    }
    fun weekStart(epoch: Long): Long = LocalDate.ofEpochDay(epoch).with(DayOfWeek.MONDAY).toEpochDay()

    // ---------- plan control ----------
    /** Puts each topic on the lightest upcoming day, one at a time. Returns the lines for the "Plan adjusted" message. */
    fun spread(list: List<Item>, from: Long): List<String> {
        if (list.isEmpty()) return emptyList()
        val last = (Plan.days.lastOrNull()?.baseEpoch ?: from) + store.offset
        val end = maxOf(last + 7, from + 21)
        val exam = HashSet<Long>()
        for (d in Plan.days) if (d.protectedDay) exam.add(d.baseEpoch + store.offset)
        val load = HashMap<Long, Int>()
        var d = from
        while (d <= end) {
            load[d] = if (exam.contains(d)) 100000 else plannedMin(d)
            d++
        }
        val moved = TreeMap<Long, MutableList<String>>()
        for (item in list) {
            var best = from
            var bl = Int.MAX_VALUE
            var x = from
            while (x <= end) {
                val l = load[x] ?: 0
                if (l < bl) { bl = l; best = x }
                x++
            }
            store.setOv(item.id, best)
            load[best] = bl + mins(item)
            moved.getOrPut(best) { mutableListOf() }.add(trackName(item.track))
        }
        val out = ArrayList<String>()
        for ((day, tracks) in moved) out.add(fmtDay(day) + " \u2192 " + tracks.joinToString(", "))
        return out
    }

    fun noStudyToday(): List<String> {
        val t = today()
        return spread(itemsOn(t).filter { !isDone(it) }, t + 1)
    }

    fun markDone(i: Item) {
        val l = left(i)
        if (l > 0) store.addLog(today(), i.track, l)
        store.setStudied(i.id, mins(i))
        store.setDone(i.id, true)
    }
    fun markUndone(i: Item) { store.setDone(i.id, false); store.setStudied(i.id, 0) }
    fun logTime(i: Item, m: Int) {
        store.addLog(today(), i.track, m)
        store.addStudied(i.id, m)
        if (store.studied(i.id) >= mins(i)) store.setDone(i.id, true)
    }
    fun move(i: Item, epoch: Long) = store.setOv(i.id, epoch)
    fun addMinutes(i: Item, delta: Int) = store.setMOv(i.id, maxOf(15, mins(i) + delta))

    fun rebuild() {
        Plan.load(c)
        store.resetUnfinished()
    }

    // ---------- timer session with focus lock ----------
    fun activeSession(): Session? {
        val s = store.sess
        if (s.isEmpty()) return null
        return try {
            val o = JSONObject(s)
            val a = o.getJSONArray("ids")
            Session(o.getLong("start"), o.getInt("plan"), o.getString("track"), List(a.length()) { a.getString(it) })
        } catch (e: Exception) { null }
    }

    fun startSession(a: Activity?, track: String, minutes: Int, ids: List<String>) {
        val now = System.currentTimeMillis()
        val o = JSONObject().put("start", now).put("plan", minutes).put("track", track)
        val arr = JSONArray()
        for (x in ids) arr.put(x)
        o.put("ids", arr)
        store.sess = o.toString()
        FocusGuard.engage(c, a, store)
        Reminders.scheduleEnd(c, now + minutes * 60000L)
    }

    /** save = true credits the elapsed minutes to the topics in order; false throws the session away. */
    fun finishSession(a: Activity?, save: Boolean) {
        val s = activeSession()
        Reminders.cancelEnd(c)
        Reminders.cancelReturn(c)
        if (s != null && save) {
            val elapsed = ((System.currentTimeMillis() - s.start) / 60000L).toInt()
            if (elapsed > 0) {
                var rem = elapsed
                for (id in s.ids) {
                    if (rem <= 0) break
                    val item = Plan.item(id) ?: continue
                    if (store.done(id)) continue
                    val need = mins(item) - store.studied(id)
                    if (need > 0) {
                        val give = minOf(need, rem)
                        store.addStudied(id, give)
                        rem -= give
                    }
                    if (store.studied(id) >= mins(item)) store.setDone(id, true)
                }
                val day = Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                store.addLog(day, s.track, elapsed)
            }
        }
        store.sess = ""
        FocusGuard.release(c, a, store)
    }
}
