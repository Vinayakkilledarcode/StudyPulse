package com.studypulse.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Book(val name: String, val file: String, val last: Int, val src: String = "")
data class UserLink(val track: String, val title: String, val url: String)

/** All app data lives in one SharedPreferences file. No database, so nothing to migrate or compile. */
class Store private constructor(c: Context, val key: String, prefsName: String) {
    private val p = c.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private var st = parse(p.getString("st", "{}"))   // per-topic state: s studied, d done, o moved-to day, m minutes
    private var lg = parse(p.getString("lg", "{}"))   // per-day study log: epochDay -> [kpmgMin, hedgeMin]

    companion object {
        @Volatile private var inst: Store? = null
        /** The store of the signed in account. A different account gets its own store. */
        fun get(c: Context): Store {
            val key = Account.key(c)
            val cur = inst
            if (cur != null && cur.key == key) return cur
            return synchronized(this) {
                val again = inst
                if (again != null && again.key == key) again
                else Store(c.applicationContext, key, Account.prefsName(c)).also { inst = it }
            }
        }
        private fun parse(s: String?): JSONObject = try { JSONObject(s ?: "{}") } catch (e: Exception) { JSONObject() }
    }

    // ---------- settings ----------
    var offset: Int
        get() = p.getInt("off", 0)
        set(v) { p.edit().putInt("off", v).apply() }
    var notifOn: Boolean
        get() = p.getBoolean("notif", true)
        set(v) { p.edit().putBoolean("notif", v).apply() }
    /** Exam day (epoch day), 0 when not set. */
    var examDate: Long
        get() = p.getLong("exam", 0L)
        set(v) { p.edit().putLong("exam", v).apply() }
    /** Minutes the person can study each day. */
    var dailyMin: Int
        get() = p.getInt("dailyMin", 120)
        set(v) { p.edit().putInt("dailyMin", v.coerceIn(30, 720)).apply() }
    /** Study days per week the person aims for. */
    var weeklyGoal: Int
        get() = p.getInt("weeklyGoal", 5)
        set(v) { p.edit().putInt("weeklyGoal", v.coerceIn(1, 7)).apply() }
    /** "smart" runs the AI extraction first, "rules" only uses the rules on the phone. */
    var engineMode: String
        get() = p.getString("engine", "smart") ?: "smart"
        set(v) { p.edit().putString("engine", v).apply() }
    var bestStreak: Int
        get() = p.getInt("bestStreak", 0)
        set(v) { p.edit().putInt("bestStreak", v).apply() }
    var goalMet: Boolean
        get() = p.getBoolean("goalMet", false)
        set(v) { p.edit().putBoolean("goalMet", v).apply() }
    /** How a session felt: "easy", "ok" or "hard"; empty when never asked. */
    /** Spaced-repetition state of a topic (SM-2 style); null when never quizzed. */
    fun review(id: String): Review? = Review.decode(p.getString("sm_$id", "") ?: "")
    fun setReview(id: String, r: Review) { p.edit().putString("sm_$id", r.encode()).apply() }
    fun lastScore(id: String): Int = p.getInt("qs_$id", -1)
    fun setLastScore(id: String, v: Int) { p.edit().putInt("qs_$id", v).apply() }
    /** The quiz written for a topic is kept, so reviewing it again costs no AI request. */
    fun quizCache(id: String): String = p.getString("quiz_$id", "") ?: ""
    fun setQuizCache(id: String, json: String) { p.edit().putString("quiz_$id", json).apply() }
    fun quizUsedToday(): Int {
        val d = java.time.LocalDate.now().toEpochDay()
        return if (p.getLong("qDay", 0L) == d) p.getInt("qCount", 0) else 0
    }
    fun quizCountOne() {
        val d = java.time.LocalDate.now().toEpochDay()
        p.edit().putLong("qDay", d).putInt("qCount", quizUsedToday() + 1).apply()
    }
    fun quizUndoOne() {
        val d = java.time.LocalDate.now().toEpochDay()
        p.edit().putLong("qDay", d).putInt("qCount", maxOf(0, quizUsedToday() - 1)).apply()
    }

    /** AI requests made today. Resets every day, so the free quota is never used up. */
    fun aiUsedToday(): Int {
        val d = java.time.LocalDate.now().toEpochDay()
        return if (p.getLong("aiDay", 0L) == d) p.getInt("aiCount", 0) else 0
    }
    fun aiCountOne() {
        val d = java.time.LocalDate.now().toEpochDay()
        p.edit().putLong("aiDay", d).putInt("aiCount", aiUsedToday() + 1).apply()
    }
    fun feel(id: String): String = p.getString("feel_$id", "") ?: ""
    fun setFeel(id: String, v: String) { p.edit().putString("feel_$id", v).apply() }
    /** Reminder time (minutes after midnight) for one track. */
    fun remind(track: String, def: Int): Int {
        if (p.contains("rt_$track")) return p.getInt("rt_$track", def)
        if (track == "K" && p.contains("kt")) return p.getInt("kt", def)   // first version
        if (track == "H" && p.contains("ht")) return p.getInt("ht", def)
        return def
    }
    fun setRemind(track: String, v: Int) { p.edit().putInt("rt_$track", v).apply() }
    var lockOn: Boolean
        get() = p.getBoolean("lock", true)
        set(v) { p.edit().putBoolean("lock", v).apply() }
    var lockDnd: Boolean
        get() = p.getBoolean("lockDnd", true)
        set(v) { p.edit().putBoolean("lockDnd", v).apply() }
    var lockPin: Boolean
        get() = p.getBoolean("lockPin", true)
        set(v) { p.edit().putBoolean("lockPin", v).apply() }
    var dndPrev: Int
        get() = p.getInt("dndPrev", -1)
        set(v) { p.edit().putInt("dndPrev", v).apply() }
    var pinFlag: Boolean
        get() = p.getBoolean("pinFlag", false)
        set(v) { p.edit().putBoolean("pinFlag", v).apply() }
    var sess: String
        get() = p.getString("sess", "") ?: ""
        set(v) { p.edit().putString("sess", v).apply() }

    fun note(track: String): String = p.getString("note_$track", "") ?: ""
    fun setNote(track: String, v: String) { p.edit().putString("note_$track", v).apply() }

    // ---------- topic state ----------
    private fun save() { p.edit().putString("st", st.toString()).apply() }

    fun studied(id: String): Int = st.optJSONObject(id)?.optInt("s", 0) ?: 0
    fun done(id: String): Boolean = st.optJSONObject(id)?.optBoolean("d", false) ?: false
    fun ov(id: String): Long? {
        val o = st.optJSONObject(id) ?: return null
        return if (o.has("o")) o.getLong("o") else null
    }
    fun mOv(id: String): Int? {
        val o = st.optJSONObject(id) ?: return null
        return if (o.has("m")) o.getInt("m") else null
    }

    @Synchronized
    private fun edit(id: String, f: (JSONObject) -> Unit) {
        val o = st.optJSONObject(id) ?: JSONObject().also { st.put(id, it) }
        f(o)
        save()
    }

    fun setDone(id: String, v: Boolean) = edit(id) { it.put("d", v) }
    fun setStudied(id: String, v: Int) = edit(id) { it.put("s", v) }
    fun addStudied(id: String, v: Int) = edit(id) { it.put("s", it.optInt("s", 0) + v) }
    fun setOv(id: String, day: Long) = edit(id) { it.put("o", day) }
    fun setMOv(id: String, m: Int) = edit(id) { it.put("m", m) }

    /** Plan rebuild: keep what is studied, put everything else back on its plan date and length. */
    @Synchronized
    fun resetUnfinished() {
        val keys = ArrayList<String>()
        val keyIt = st.keys()
        while (keyIt.hasNext()) keys.add(keyIt.next())
        for (k in keys) {
            val o = st.optJSONObject(k) ?: continue
            if (o.optBoolean("d", false)) continue
            o.remove("o"); o.remove("m")
        }
        save()
    }

    // ---------- study log ----------
    // day -> {track: minutes}. The first version wrote [kpmg, hedge] arrays; those are still read.
    @Synchronized
    fun addLog(day: Long, track: String, min: Int) {
        val k = day.toString()
        val o: JSONObject = when (val cur = lg.opt(k)) {
            is JSONObject -> cur
            is JSONArray -> JSONObject().put("K", cur.optInt(0, 0)).put("H", cur.optInt(1, 0))
            else -> JSONObject()
        }
        o.put(track, o.optInt(track, 0) + min)
        lg.put(k, o)
        p.edit().putString("lg", lg.toString()).apply()
    }

    fun logOf(day: Long, track: String): Int = when (val cur = lg.opt(day.toString())) {
        is JSONObject -> cur.optInt(track, 0)
        is JSONArray -> if (track == "K") cur.optInt(0, 0) else if (track == "H") cur.optInt(1, 0) else 0
        else -> 0
    }

    /** All minutes ever logged. */
    fun totalMinutes(): Int {
        var sum = 0
        val it = lg.keys()
        while (it.hasNext()) {
            when (val cur = lg.opt(it.next())) {
                is JSONObject -> { val ks = cur.keys(); while (ks.hasNext()) sum += cur.optInt(ks.next(), 0) }
                is JSONArray -> for (i in 0 until cur.length()) sum += cur.optInt(i, 0)
            }
        }
        return sum
    }

    /** A new plan starts clean: progress, log, notes and saved links belong to the old plan's tracks. */
    @Synchronized
    fun resetForNewPlan() {
        st = JSONObject()
        lg = JSONObject()
        val e = p.edit().putString("st", "{}").putString("lg", "{}").putString("sess", "").putInt("off", 0).remove("ulinks")
        for (k in p.all.keys) if (k.startsWith("note_") || k.startsWith("rt_") || k.startsWith("feel_") || k.startsWith("sm_") || k.startsWith("qs_") || k.startsWith("quiz_")) e.remove(k)
        e.remove("kt").remove("ht")
        e.apply()
    }

    // ---------- your links ----------
    fun userLinks(): List<UserLink> {
        val a = try { JSONArray(p.getString("ulinks", "[]")) } catch (e: Exception) { JSONArray() }
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            UserLink(o.optString("t"), o.optString("n"), o.optString("u"))
        }
    }
    @Synchronized
    fun addUserLink(l: UserLink) {
        val cur = userLinks().toMutableList(); cur.add(l)
        val a = JSONArray()
        for (x in cur) a.put(JSONObject().put("t", x.track).put("n", x.title).put("u", x.url))
        p.edit().putString("ulinks", a.toString()).apply()
    }
    @Synchronized
    fun removeUserLink(idx: Int) {
        val cur = userLinks().toMutableList()
        if (idx !in cur.indices) return
        cur.removeAt(idx)
        val a = JSONArray()
        for (x in cur) a.put(JSONObject().put("t", x.track).put("n", x.title).put("u", x.url))
        p.edit().putString("ulinks", a.toString()).apply()
    }

    // ---------- books ----------
    fun books(): List<Book> {
        val a = try { JSONArray(p.getString("books", "[]")) } catch (e: Exception) { JSONArray() }
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Book(o.optString("n"), o.optString("f"), o.optInt("l", 0), o.optString("s"))
        }
    }
    @Synchronized
    private fun putBooks(list: List<Book>) {
        val a = JSONArray()
        for (b in list) a.put(JSONObject().put("n", b.name).put("f", b.file).put("l", b.last).put("s", b.src))
        p.edit().putString("books", a.toString()).apply()
    }
    @Synchronized fun addBook(b: Book) { putBooks(books() + b) }
    @Synchronized fun removeBook(idx: Int) { val l = books().toMutableList(); if (idx in l.indices) { l.removeAt(idx); putBooks(l) } }
    @Synchronized fun setLastPage(idx: Int, page: Int) {
        val l = books().toMutableList()
        if (idx in l.indices) { l[idx] = l[idx].copy(last = page); putBooks(l) }
    }
}
