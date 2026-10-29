package com.studypulse.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class LinkRef(val kind: String, val title: String, val url: String)

data class TrackInfo(val id: String, val name: String)

/** [baseEpoch] is the calendar day this plan day falls on before the start date is moved. */
data class PlanDay(val n: Int, val baseEpoch: Long, val protectedDay: Boolean)

/** One study topic. */
data class Item(val id: String, val day: PlanDay, val track: String, val text: String, val minutes: Int, val links: List<LinkRef>)

fun trackName(t: String) = Plan.nameOf(t)
fun trackLong(t: String) = Plan.nameOf(t)

/** The study plan of the signed in account, kept as a JSON file in app storage. */
object Plan {
    var name: String = ""
        private set
    var tracks: List<TrackInfo> = emptyList()
        private set
    var days: List<PlanDay> = emptyList()
        private set
    var items: List<Item> = emptyList()
        private set
    var baseStart: Long = 0L
        private set
    private val map = HashMap<String, Item>()

    fun hasPlan(): Boolean = days.isNotEmpty()
    fun item(id: String): Item? = map[id]
    fun nameOf(track: String): String = tracks.firstOrNull { it.id == track }?.name ?: track
    fun trackIndex(track: String): Int = tracks.indexOfFirst { it.id == track }.let { if (it < 0) 99 else it }

    private fun file(c: Context): File = File(c.applicationContext.filesDir, "plan_" + Account.key(c) + ".json")

    @Synchronized
    fun load(c: Context) {
        val f = file(c)
        if (!f.exists()) { clear(); return }
        try { parse(JSONObject(f.readText())) } catch (e: Exception) { clear() }
    }

    @Synchronized
    fun saveJson(c: Context, json: String) {
        file(c).writeText(json)
        load(c)
    }

    fun installSample(c: Context) {
        val txt = c.applicationContext.assets.open("sample_plan.json").bufferedReader().use { it.readText() }
        saveJson(c, txt)
    }

    @Synchronized
    fun delete(c: Context) {
        file(c).delete()
        clear()
    }

    private fun clear() {
        name = ""; tracks = emptyList(); days = emptyList(); items = emptyList(); baseStart = 0L
        map.clear()
    }

    private fun parse(root: JSONObject) {
        val start = LocalDate.parse(root.getString("start")).toEpochDay()
        val ta = root.getJSONArray("tracks")
        val ts = ArrayList<TrackInfo>()
        for (i in 0 until ta.length()) {
            val o = ta.getJSONObject(i)
            ts.add(TrackInfo(o.getString("id"), o.getString("name")))
        }
        val da = root.getJSONArray("days")
        val ds = ArrayList<PlanDay>()
        val its = ArrayList<Item>()
        for (i in 0 until da.length()) {
            val o = da.getJSONObject(i)
            val d = PlanDay(o.getInt("n"), start + o.getInt("off"), o.optBoolean("prot", false))
            ds.add(d)
            val ia = o.getJSONArray("items")
            for (k in 0 until ia.length()) {
                val x = ia.getJSONObject(k)
                its.add(Item(x.getString("id"), d, x.getString("t"), x.getString("x"), x.optInt("m", 60), links(x.optJSONArray("l"))))
            }
        }
        name = root.optString("name", "Study plan")
        tracks = ts
        baseStart = start
        days = ds
        items = its
        map.clear()
        for (x in its) map[x.id] = x
    }

    private fun links(a: JSONArray?): List<LinkRef> {
        val out = ArrayList<LinkRef>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(LinkRef(o.optString("k"), o.optString("t"), o.optString("u")))
        }
        return out
    }
}
