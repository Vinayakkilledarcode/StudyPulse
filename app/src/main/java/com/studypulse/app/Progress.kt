package com.studypulse.app

import java.time.LocalDate

/** Puts every missed topic on the first coming day that has room (daily time), before the exam date when there is one. */
fun Engine.reschedule(): List<String> {
    val miss = overdue()
    if (miss.isEmpty()) return emptyList()
    val from = today()
    val cap = store.dailyMin
    val lastPlanned = (Plan.days.lastOrNull()?.baseEpoch ?: from) + store.offset
    val exam = store.examDate
    val end = if (exam > from) exam - 1 else maxOf(lastPlanned + 7, from + 21)
    val blocked = HashSet<Long>()
    for (d in Plan.days) if (d.protectedDay) blocked.add(d.baseEpoch + store.offset)
    val load = HashMap<Long, Int>()
    var d = from
    while (d <= end) {
        load[d] = if (blocked.contains(d)) 100000 else itemsOn(d).filter { !isDone(it) }.sumOf { mins(it) }
        d++
    }
    val moved = java.util.TreeMap<Long, Int>()
    for (item in miss) {
        var pick = -1L
        var x = from
        while (x <= end) {
            val l = load[x] ?: 0
            if (l < 100000 && l + mins(item) <= cap) { pick = x; break }
            x++
        }
        if (pick < 0) {
            var bl = Int.MAX_VALUE
            x = from
            while (x <= end) {
                val l = load[x] ?: 0
                if (l < bl) { bl = l; pick = x }
                x++
            }
        }
        store.setOv(item.id, pick)
        load[pick] = (load[pick] ?: 0) + mins(item)
        moved[pick] = (moved[pick] ?: 0) + 1
    }
    val out = ArrayList<String>()
    for ((day, n) in moved) out.add(fmtDay(day) + " \u2192 " + n + (if (n == 1) " topic" else " topics"))
    return out
}

/** Days studied so far in the current week (Monday to Sunday). */
fun Engine.weekDaysStudied(): Int {
    val t = today()
    val ws = weekStart(t)
    var n = 0
    var d = ws
    while (d <= t) { if (studiedMin(d) > 0) n++; d++ }
    return n
}

class Badge(val name: String, val sub: String, val got: Boolean)

object Progress {
    fun doneCount(e: Engine): Int = Plan.items.count { e.isDone(it) }

    fun badges(e: Engine): List<Badge> {
        val s = e.store
        val best = maxOf(s.bestStreak, e.streak())
        val done = doneCount(e)
        val trackDone = Plan.tracks.any { t ->
            val its = Plan.items.filter { it.track == t.id }
            its.isNotEmpty() && its.all { e.isDone(it) }
        }
        return listOf(
            Badge("First step", "Study for the first time", s.totalMinutes() > 0),
            Badge("Warm-up", "3 study days in a row", best >= 3),
            Badge("One week", "7 study days in a row", best >= 7),
            Badge("One month", "30 study days in a row", best >= 30),
            Badge("Goal week", "Reach your weekly goal", s.goalMet || e.weekDaysStudied() >= s.weeklyGoal),
            Badge("10 topics", "Finish 10 topics", done >= 10),
            Badge("50 topics", "Finish 50 topics", done >= 50),
            Badge("Track complete", "Finish every topic of a track", trackDone)
        )
    }

    fun daysLeft(e: Engine): Int {
        val x = e.store.examDate
        return if (x > 0L) (x - LocalDate.now().toEpochDay()).toInt() else -1
    }
}
