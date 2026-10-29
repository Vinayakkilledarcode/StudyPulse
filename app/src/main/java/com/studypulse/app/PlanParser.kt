package com.studypulse.app

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/** A link found in the uploaded plan. */
data class RawLink(val kind: String, val title: String, val url: String)

/** One topic on one day, before the plan is saved. */
data class RawItem(val track: String, val text: String, val minutes: Int, val links: List<RawLink>)

class RawDay(val n: Int, var date: Long?, val items: MutableList<RawItem> = mutableListOf())

class ParsedPlan(
    val name: String,
    val days: List<RawDay>,
    val tracks: List<String>,
    val warnings: List<String>
) {
    val datedDays: Int get() = days.count { it.date != null }
    val firstDate: Long? get() = days.mapNotNull { it.date }.minOrNull()
    /** True when at least half of the days carry a real date. */
    val hasDates: Boolean get() = days.isNotEmpty() && datedDays * 2 >= days.size
    val topicCount: Int get() = days.sumOf { it.items.size }
}

/**
 * Turns an uploaded study plan into days and topics. Works on tables (Excel, CSV, Word tables) and on
 * plain text with "Day 1" or date headings (Word, PDF, text). Pure Kotlin: no Android classes.
 */
object PlanParser {
    const val DEFAULT_MIN = 60
    private val FULL_MONTHS = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"
    )

    // ================================================================== dates
    class DateHit(val epoch: Long, val start: Int, val end: Int)

    private val P_ISO = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    private val P_DMY = Regex("(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?!\\d)")
    private val P_DMON = Regex("(?<![a-z\\d])(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:of\\s+)?([a-z]{3,9})\\.?,?(?:\\s*(\\d{4}))?(?!\\d)")
    private val P_MOND = Regex("(?<![a-z\\d])([a-z]{3,9})\\.?\\s*(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)(?:,?\\s*(\\d{4}))?")

    private fun monthOf(word: String): Int {
        if (word.length < 3) return 0
        if (word == "sept") return 9
        val i = FULL_MONTHS.indexOfFirst { it.startsWith(word) }
        return if (i >= 0) i + 1 else 0
    }

    private fun mk(y: Int, m: Int, d: Int): Long? = try {
        if (y !in 2000..2100) null else LocalDate.of(y, m, d).toEpochDay()
    } catch (e: DateTimeException) { null }

    /** Finds the first date in the text. [year] is used when the text has no year. */
    fun findDate(text: String, year: Int): DateHit? {
        val s = text.lowercase(Locale.ENGLISH)
        val hits = ArrayList<DateHit>()
        for (m in P_ISO.findAll(s)) mk(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            ?.let { hits.add(DateHit(it, m.range.first, m.range.last + 1)) }
        for (m in P_DMY.findAll(s)) {
            var a = m.groupValues[1].toInt()
            var b = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            if (b > 12 && a <= 12) { val t = a; a = b; b = t }
            mk(y, b, a)?.let { hits.add(DateHit(it, m.range.first, m.range.last + 1)) }
        }
        for (m in P_DMON.findAll(s)) {
            val mo = monthOf(m.groupValues[2])
            if (mo == 0) continue
            val y = m.groupValues[3].ifEmpty { null }?.toInt() ?: year
            mk(y, mo, m.groupValues[1].toInt())?.let { hits.add(DateHit(it, m.range.first, m.range.last + 1)) }
        }
        for (m in P_MOND.findAll(s)) {
            val mo = monthOf(m.groupValues[1])
            if (mo == 0) continue
            val y = m.groupValues[3].ifEmpty { null }?.toInt() ?: year
            mk(y, mo, m.groupValues[2].toInt())?.let { hits.add(DateHit(it, m.range.first, m.range.last + 1)) }
        }
        return hits.minByOrNull { it.start }
    }

    /** A table cell that is a date: text date, or an Excel serial number. */
    fun cellDate(raw: String, year: Int): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (Regex("^\\d{5}(\\.0+)?$").matches(s)) {
            val n = s.substringBefore('.').toLong()
            if (n in 36526..73050) return LocalDate.of(1899, 12, 30).plusDays(n).toEpochDay()
        }
        return findDate(s, year)?.epoch
    }

    private val WEEKDAY = Regex("^(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)[a-z]*\\.?,?\\s*", RegexOption.IGNORE_CASE)

    /** A date at the very start of a line (after an optional weekday). Returns the date and the rest of the line. */
    fun leadingDate(line: String, year: Int): Pair<Long, String>? {
        val wd = WEEKDAY.find(line)?.value?.length ?: 0
        val hit = findDate(line.substring(wd), year) ?: return null
        if (hit.start != 0) return null
        return hit.epoch to line.substring(wd + hit.end).trim().trimStart('-', '\u2013', '\u2014', ':', '.', ')', ',', ' ')
    }

    // ================================================================== durations
    private val RE_HM = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:h|hr|hrs|hour|hours)\\b(?:\\s*(?:and\\s*)?(\\d+)\\s*(?:m|min|mins|minute|minutes)\\b)?", RegexOption.IGNORE_CASE)
    private val RE_M = Regex("(\\d+)\\s*(?:m|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE)

    private fun clampMin(v: Double): Int? {
        val m = Math.round(v / 5.0) * 5
        return if (m in 5..720) m.toInt() else null
    }

    /** Duration in a free text snippet, or null. */
    fun durationIn(text: String): Pair<Int, IntRange>? {
        RE_HM.find(text)?.let {
            val h = it.groupValues[1].replace(',', '.').toDouble()
            val m = it.groupValues[2].ifEmpty { "0" }.toDouble()
            clampMin(h * 60 + m)?.let { v -> return v to it.range }
        }
        RE_M.find(text)?.let { clampMin(it.groupValues[1].toDouble())?.let { v -> return v to it.range } }
        return null
    }

    /** A duration cell: "2h", "90 min", "1.5", "3 + 1", "1:30". [unit] 0 unknown, 1 minutes, 2 hours. */
    fun cellMinutes(raw: String, unit: Int): Int? {
        val s = raw.trim().lowercase(Locale.ENGLISH)
        if (s.isEmpty()) return null
        durationIn(s)?.let { return it.first }
        Regex("^(\\d{1,2}):(\\d{2})$").find(s)?.let { return clampMin(it.groupValues[1].toDouble() * 60 + it.groupValues[2].toDouble()) }
        val nums = Regex("\\d+(?:[.,]\\d+)?").findAll(s).map { it.value.replace(',', '.').toDouble() }.toList()
        if (nums.isEmpty() || !Regex("^[\\d.,+\\s]+$").matches(s)) return null
        val total = nums.sum()
        return when (unit) {
            1 -> clampMin(total)
            2 -> clampMin(total * 60)
            else -> if (total <= 12) clampMin(total * 60) else clampMin(total)
        }
    }

    /** Takes a duration written at the end of an item, or in brackets, out of the text. */
    fun splitDuration(text: String): Pair<String, Int?> {
        val dur = "(?:\\d+(?:[.,]\\d+)?\\s*(?:h|hr|hrs|hour|hours)\\b(?:\\s*(?:and\\s*)?\\d+\\s*(?:m|min|mins|minute|minutes)\\b)?|\\d+\\s*(?:m|min|mins|minute|minutes)\\b)"
        val paren = Regex("\\s*[(\\[]\\s*($dur)\\s*[)\\]]", RegexOption.IGNORE_CASE)
        val tail = Regex("\\s*(?:[-\u2013\u2014,:]\\s*|\\s)($dur)\\s*\\.?\\s*$", RegexOption.IGNORE_CASE)
        for (re in listOf(paren, tail)) {
            val m = re.find(text) ?: continue
            val v = durationIn(m.groupValues[1])?.first ?: continue
            val rest = (text.substring(0, m.range.first) + text.substring(m.range.last + 1)).trim().trimEnd('-', '\u2013', '\u2014', ',', ':')
            if (rest.length >= 3) return rest to v
        }
        return text to null
    }

    // ================================================================== links
    private val URL = Regex("https?://[^\\s)\\]>\"']+")

    private fun stripLinks(text: String): Pair<String, List<RawLink>> {
        val links = ArrayList<RawLink>()
        for (m in URL.findAll(text)) {
            val u = m.value.trimEnd('.', ',', ';')
            val yt = u.contains("youtube.com") || u.contains("youtu.be")
            links.add(RawLink(if (yt) "Watch" else "Read", host(u), u))
        }
        if (links.isEmpty()) return text.trim() to links
        val t = text.replace(URL, " ").replace(Regex("\\s+"), " ").trim().trimEnd('-', '\u2013', ':', ',', '(', ')').trim()
        return t to links
    }

    private fun host(u: String): String = u.substringAfter("://").substringBefore('/').removePrefix("www.")

    private fun clean(s: String): String = s.replace('\u00A0', ' ').replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("\\s+"), " ").trim()

    // ================================================================== table mode
    private enum class Col { DAY, DATE, WEEK, MIN, LINK, NOTE, SUBJECT, DETAIL, TOPIC }

    private fun words(h: String): List<String> =
        h.lowercase(Locale.ENGLISH).replace(Regex("[^a-z]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    private fun classify(header: String): Col {
        val raw = header.trim()
        if (raw == "#") return Col.DAY
        val w = words(raw)
        if (w.isEmpty()) return Col.NOTE
        if (w.any { it == "date" || it == "dates" } || w == listOf("when")) return Col.DATE
        if (w.contains("weekday")) return Col.NOTE
        if (w.contains("week") || w.contains("weeks")) return Col.WEEK
        if ((w.contains("day") || w.contains("days")) && w.size <= 2) return Col.DAY
        if (w.joinToString(" ") in setOf("no", "sl no", "sno", "s no", "sr no", "sr", "serial", "serial no", "session", "sessions")) return Col.DAY
        if (w.any { it in setOf("hours", "hour", "hrs", "hr", "duration", "minutes", "mins", "min", "time", "effort") }) return Col.MIN
        if (w.any { it in setOf("link", "links", "url", "urls", "resource", "resources", "reference", "references", "video", "videos", "watch", "material", "materials") }) return Col.LINK
        if (w.any { it in setOf("note", "notes", "remark", "remarks", "comment", "comments", "status", "done", "complete", "completed", "progress", "check", "checked", "tick") }) return Col.NOTE
        if (w.any { it in setOf("subject", "subjects", "course", "courses", "track", "tracks", "category", "stream", "paper", "section", "domain", "area", "department") }) return Col.SUBJECT
        if (w.any { it in setOf("description", "details", "detail", "objective", "objectives", "outcome", "outcomes", "deliverable", "deliverables", "goal", "goals") }) return Col.DETAIL
        return Col.TOPIC
    }

    private val FILLER = setOf("topic", "topics", "plan", "schedule", "study", "main", "daily", "task", "tasks", "syllabus")
    private val PRACTICE_WORDS = setOf("practice", "exercise", "exercises", "homework", "assignment", "lab", "revision", "drill", "drills")

    private fun trackLabel(header: String): String {
        var h = header.replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim()
        val ws = h.split(' ').filter { it.isNotEmpty() }
        val kept = ws.filter { it.lowercase(Locale.ENGLISH).trim(':', '-') !in FILLER }
        h = (if (kept.isEmpty()) ws else kept).joinToString(" ").trim(':', '-', ' ')
        return h.ifEmpty { "Study" }
    }

    private fun headerMinutes(header: String): Int? {
        val m = Regex("\\(([^)]*)\\)").find(header) ?: return null
        return durationIn(m.groupValues[1])?.first
    }

    private fun firstInt(s: String): Int? = Regex("\\d{1,3}").find(s)?.value?.toIntOrNull()

    fun fromRows(rows0: List<List<String>>, name: String, year: Int): ParsedPlan? {
        val rows = rows0.map { r -> r.map { clean(it) } }.filter { r -> r.any { it.isNotEmpty() } }
        if (rows.size < 2) return null
        // find the header row among the first few rows
        var hi = -1
        for (i in 0 until minOf(8, rows.size - 1)) {
            val nonEmpty = rows[i].count { it.isNotEmpty() }
            if (nonEmpty < 2) continue
            val kinds = rows[i].filter { it.isNotEmpty() }.map { classify(it) }
            if (kinds.any { it != Col.TOPIC && it != Col.NOTE } || kinds.all { it == Col.TOPIC }) {
                // a header has mostly short cells
                if (rows[i].all { it.length <= 40 }) { hi = i; break }
            }
        }
        if (hi < 0) return null
        val header = rows[hi]
        val data = rows.drop(hi + 1)
        val kinds = header.map { classify(it) }.toMutableList()

        // validate duration columns by content
        for (c in kinds.indices) if (kinds[c] == Col.MIN) {
            val vals = data.mapNotNull { r -> r.getOrNull(c)?.takeIf { it.isNotEmpty() } }
            val ok = vals.count { cellMinutes(it, unitOf(header[c])) != null }
            if (vals.isEmpty() || ok * 10 < vals.size * 6) kinds[c] = Col.NOTE
        }
        // validate DAY / DATE columns
        for (c in kinds.indices) if (kinds[c] == Col.DAY) {
            val vals = data.mapNotNull { r -> r.getOrNull(c)?.takeIf { it.isNotEmpty() } }
            if (vals.isEmpty() || vals.count { firstInt(it) != null || cellDate(it, year) != null } * 10 < vals.size * 6) kinds[c] = Col.NOTE
        }

        val dayCol = kinds.indexOf(Col.DAY)
        val dateCol = kinds.indexOf(Col.DATE)
        val minCols = kinds.indices.filter { kinds[it] == Col.MIN }
        val minCol = minCols.firstOrNull() ?: -1
        val linkCols = kinds.indices.filter { kinds[it] == Col.LINK }
        val subjectCol = kinds.indexOf(Col.SUBJECT)
        var topicCols = kinds.indices.filter { kinds[it] == Col.TOPIC }
        // topic columns need real text under them
        topicCols = topicCols.filter { c -> data.any { r -> (r.getOrNull(c) ?: "").any { ch -> ch.isLetter() } } }
        if (topicCols.isEmpty()) return null
        if (dayCol < 0 && dateCol < 0 && data.size < 2) return null

        // merge "KPMG practice" into "KPMG", and detail columns into the topic before them
        val trackOfCol = HashMap<Int, String>()
        val prefixOfCol = HashMap<Int, String>()
        val baseTopics = ArrayList<Int>()
        for (c in topicCols) {
            val ws = words(header[c])
            val last = ws.lastOrNull()
            var merged = false
            if (last != null && last in PRACTICE_WORDS && ws.size >= 2) {
                val base = ws.dropLast(1).joinToString(" ")
                val target = topicCols.firstOrNull { o -> o != c && words(header[o]).joinToString(" ").let { it == base || it.startsWith("$base ") && words(header[o]).last() !in PRACTICE_WORDS } }
                if (target != null) {
                    trackOfCol[c] = trackLabel(header[target]); prefixOfCol[c] = last.replaceFirstChar { it.uppercase() } + ": "; merged = true
                }
            }
            if (!merged) { trackOfCol[c] = if (topicCols.size == 1 && subjectCol < 0) "Study" else trackLabel(header[c]); baseTopics.add(c) }
        }
        val detailCols = kinds.indices.filter { kinds[it] == Col.DETAIL }
        val colDefaultMin = HashMap<Int, Int?>()
        for (c in topicCols) colDefaultMin[c] = headerMinutes(header[c])
        val unit = if (minCol >= 0) unitOf(header[minCol]) else 0

        val days = LinkedHashMap<Int, RawDay>()
        var cur: RawDay? = null
        var seq = 0
        var lastN = 0
        var curDate: Long? = null
        for (r in data) {
            fun cell(c: Int) = if (c in r.indices) r[c] else ""
            val dayCell = if (dayCol >= 0) cell(dayCol) else ""
            val dateCell = if (dateCol >= 0) cell(dateCol) else ""
            var date: Long? = if (dateCell.isNotEmpty()) cellDate(dateCell, year) else null
            var n: Int? = firstInt(dayCell)
            if (n != null && n > 400) n = null
            if (date == null && dayCell.isNotEmpty() && n == null) date = cellDate(dayCell, year)
            if (date == null && dayCell.isNotEmpty() && dateCol < 0) {
                // a date written next to the day number, e.g. "Day 3 (12 Oct)"
                date = findDate(dayCell, year)?.epoch
            }
            // topic cells in this row
            val parts = ArrayList<Triple<String, String, Int?>>() // track, text, minutes
            for (c in topicCols) {
                var t = cell(c)
                if (t.isEmpty()) continue
                for (dc in detailCols) if (dc > c && (topicCols.firstOrNull { it > c } ?: Int.MAX_VALUE) > dc && cell(dc).isNotEmpty()) t += ". " + cell(dc)
                t = (prefixOfCol[c] ?: "") + t
                val track = if (subjectCol >= 0) (cell(subjectCol).ifEmpty { "Study" }) else trackOfCol[c] ?: "Study"
                parts.add(Triple(track, t, colDefaultMin[c]))
            }
            if (parts.isEmpty() && subjectCol >= 0 && cell(subjectCol).isNotEmpty() && detailCols.isNotEmpty()) {
                val t = detailCols.map { cell(it) }.filter { it.isNotEmpty() }.joinToString(". ")
                if (t.isNotEmpty()) parts.add(Triple(cell(subjectCol), t, null))
            }
            val hasContent = parts.isNotEmpty()
            if (!hasContent && n == null && date == null) continue
            val day: RawDay
            if (n == null && date == null) {
                day = if (dayCol < 0 && dateCol < 0) RawDay(++seq, null).also { days[it.n] = it; cur = it; lastN = it.n }
                else cur ?: RawDay(++seq, null).also { days[it.n] = it; cur = it; lastN = it.n }
            } else {
                val key: Int = n ?: run {
                    val same = days.values.firstOrNull { date != null && it.date == date }
                    same?.n ?: (lastN + 1)
                }
                day = days.getOrPut(key) { RawDay(key, date) }
                if (day.date == null) day.date = date
                cur = day
                lastN = maxOf(lastN, key)
                seq = maxOf(seq, key)
                curDate = date ?: curDate
            }
            val rowMin: Int? = if (minCol >= 0) cellMinutes(cell(minCol), unit) else null
            val links = ArrayList<RawLink>()
            for (lc in linkCols) links.addAll(stripLinks(cell(lc)).second)
            for ((track, text0, defMin) in parts) {
                val (noLinks, inLinks) = stripLinks(text0)
                val (text, inText) = splitDuration(noLinks)
                if (text.isBlank()) continue
                val perItem = if (rowMin != null && parts.size > 1 && subjectCol < 0) clampMin(rowMin.toDouble() / parts.size) else rowMin
                day.items.add(RawItem(clean(track), text.take(700), perItem ?: inText ?: defMin ?: DEFAULT_MIN, inLinks + links))
            }
        }
        return finish(name, days.values.toList(), emptyList())
    }

    private fun unitOf(header: String): Int {
        val w = words(header)
        return when {
            w.any { it in setOf("min", "mins", "minutes", "minute") } -> 1
            w.any { it in setOf("hours", "hour", "hrs", "hr", "h") } -> 2
            else -> 0
        }
    }

    // ================================================================== text mode
    private val DAY_HEAD = Regex("^(?:day|d)\\s*[-#.:]?\\s*(\\d{1,3})\\b\\s*(?:[:.\\-\u2013\u2014)]\\s*)?(.*)$", RegexOption.IGNORE_CASE)
    private val WEEK_HEAD = Regex("^week\\s*[-#.:]?\\s*\\d+\\b.*$", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("^\\s*(?:[\u2022\u25CF\u25AA\u25E6\u2023\u2043*\\-\u2013\u2014\u00B7]|\\d{1,2}[.)])\\s+")
    private val LABEL = Regex("^([A-Za-z][A-Za-z0-9&/+ .'\\-]{1,30}?)\\s*(?::|\\s[-\u2013\u2014]\\s)\\s*(.+)$")
    private val GENERIC_LABELS = setOf(
        "practice", "do", "learn", "read", "watch", "note", "notes", "task", "tasks", "topic", "topics", "goal", "goals",
        "homework", "revision", "review", "resources", "resource", "link", "links", "reading", "exercise", "exercises",
        "study", "lesson", "chapter", "unit", "module", "part", "step", "target", "objective", "objectives", "focus",
        "session", "assignment", "activity", "today", "plan", "quiz", "test", "exam", "mock", "project", "lab", "day", "week"
    )

    private val NOTE_LABEL = Regex("^(?:notes?|remarks?|tips?|reminders?)\\s*[:\u2013\u2014-]", RegexOption.IGNORE_CASE)

    private class Pending(var n: Int, var date: Long?, val lines: MutableList<String> = mutableListOf())

    fun fromLines(lines0: List<String>, name: String, year: Int): ParsedPlan? {
        val lines = lines0.map { clean(it) }.filter { it.isNotEmpty() && !Regex("^(?:page\\s*)?\\d{1,3}(?:\\s*/\\s*\\d{1,3})?$", RegexOption.IGNORE_CASE).matches(it) }
        if (lines.isEmpty()) return null

        data class Head(val idx: Int, val n: Int?, val date: Long?, val rest: String)
        val heads = ArrayList<Head>()
        for ((i, l) in lines.withIndex()) {
            if (WEEK_HEAD.matches(l)) continue
            val dm = DAY_HEAD.find(l)
            if (dm != null) {
                var rest = dm.groupValues[2].trim()
                var date: Long? = null
                val lead = leadingDate(rest, year)   // "Day 1 - Mon, 2 Nov 2026" or "Day 1: 12/10/2026"
                if (lead != null) {
                    date = lead.first
                    rest = lead.second
                } else {
                    val hit = findDate(rest, year)
                    if (hit != null) {
                        // a date in brackets at the end, e.g. "Day 3 (12 Oct)"
                        val br = Regex("[(\\[]\\s*" + Regex.escape(rest.substring(hit.start, hit.end)) + "\\s*[)\\]]").find(rest)
                        if (br != null) { date = hit.epoch; rest = rest.removeRange(br.range).trim() }
                    }
                }
                heads.add(Head(i, dm.groupValues[1].toInt(), date, rest))
                continue
            }
        }
        val dayHeads = heads
        var useDates = false
        val dateHeads = ArrayList<Head>()
        if (dayHeads.size < 2) {
            for ((i, l) in lines.withIndex()) {
                if (l.length > 90) continue
                val ld = leadingDate(l, year) ?: continue
                dateHeads.add(Head(i, null, ld.first, ld.second))
            }
            useDates = dateHeads.size >= 2
        }
        val chosen: List<Head> = if (!useDates && dayHeads.size >= 1 && dayHeads.size * 2 >= minOf(lines.size, 4)) dayHeads else if (useDates) dateHeads else emptyList()

        val pend = ArrayList<Pending>()
        if (chosen.isEmpty()) {
            // flat list: one topic per day
            val items = groupItems(lines)
            if (items.size < 3) return null
            for ((i, t) in items.withIndex()) pend.add(Pending(i + 1, null, mutableListOf(t)))
        } else {
            var base = 0
            var lastAbs = 0
            for ((k, h) in chosen.withIndex()) {
                val end = if (k + 1 < chosen.size) chosen[k + 1].idx else lines.size
                var abs: Int
                if (useDates) abs = k + 1 else {
                    abs = base + (h.n ?: (lastAbs + 1))
                    if (abs <= lastAbs) { base = lastAbs; abs = base + (h.n ?: 1) }
                }
                lastAbs = abs
                val body = ArrayList<String>()
                if (h.rest.isNotEmpty()) body.add(h.rest)
                for (j in h.idx + 1 until end) if (!WEEK_HEAD.matches(lines[j])) body.add(lines[j])
                pend.add(Pending(abs, h.date, groupItems(body).toMutableList()))
            }
        }

        // item labels become tracks when the same label appears on several days
        val labelDays = HashMap<String, MutableSet<Int>>()
        for (p in pend) for (t in p.lines) LABEL.find(t)?.let {
            val lab = it.groupValues[1].trim()
            if (lab.split(' ').size <= 4 && lab.lowercase(Locale.ENGLISH) !in GENERIC_LABELS) labelDays.getOrPut(lab.lowercase(Locale.ENGLISH)) { HashSet() }.add(p.n)
        }
        val accepted = labelDays.filter { it.value.size >= 2 }.keys
        val useLabels = accepted.isNotEmpty() && accepted.size <= 8

        val out = ArrayList<RawDay>()
        for (p in pend) {
            val day = RawDay(p.n, p.date)
            for (t0 in p.lines) {
                var track = "Study"
                var text = t0
                if (NOTE_LABEL.containsMatchIn(t0)) continue
                if (useLabels) LABEL.find(t0)?.let {
                    val lab = it.groupValues[1].trim()
                    if (lab.lowercase(Locale.ENGLISH) in accepted) { track = lab; text = it.groupValues[2].trim() }
                }
                val (noLinks, links) = stripLinks(text)
                val (t, m) = splitDuration(noLinks)
                if (t.isBlank()) continue
                day.items.add(RawItem(track, t.take(700), m ?: DEFAULT_MIN, links))
            }
            if (day.items.isNotEmpty()) out.add(day)
        }
        return finish(name, out, emptyList())
    }

    /** Groups wrapped text lines into items. Bullets start new items; otherwise a lowercase start continues the line before. */
    private fun groupItems(body: List<String>): List<String> {
        val anyBullet = body.any { BULLET.containsMatchIn(it) }
        val items = ArrayList<String>()
        for (l in body) {
            val isBullet = BULLET.containsMatchIn(l)
            val text = l.replace(BULLET, "").trim()
            if (text.isEmpty()) continue
            val continues = if (anyBullet) !isBullet && items.isNotEmpty() else items.isNotEmpty() && text[0].isLowerCase()
            if (continues) items[items.size - 1] = items.last() + " " + text else items.add(text)
        }
        return items
    }

    // ================================================================== result
    private fun finish(name: String, days0: List<RawDay>, warn: List<String>): ParsedPlan? {
        val days = days0.filter { it.items.isNotEmpty() }
        if (days.isEmpty()) return null
        // make day numbers unique and ordered
        val ordered = days.sortedWith(compareBy<RawDay>({ it.n }, { it.date ?: Long.MAX_VALUE }))
        val seen = HashSet<Int>()
        val fixed = ArrayList<RawDay>()
        var dup = false
        for (d in ordered) { if (!seen.add(d.n)) dup = true }
        if (dup) ordered.forEachIndexed { i, d -> fixed.add(RawDay(i + 1, d.date, d.items)) } else fixed.addAll(ordered)
        val tracks = LinkedHashSet<String>()
        for (d in fixed) for (i in d.items) tracks.add(i.track)
        return ParsedPlan(name, fixed, tracks.toList(), warn)
    }

    /** Tries every table first, then the text lines. */
    fun parse(tables: List<List<List<String>>>, lines: List<String>, name: String, year: Int): ParsedPlan? {
        for (t in tables.sortedByDescending { it.size }) fromRows(t, name, year)?.let { return it }
        return fromLines(lines, name, year)
    }

    // ================================================================== saving
    private fun esc(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) when (ch) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
        }
        return sb.append('"').toString()
    }

    private val EXAM = Regex("\\b(exam|examination|mock test|final test|test day)\\b", RegexOption.IGNORE_CASE)

    /**
     * The saved form of a plan. [start] is the calendar date of the first day. Dated plans keep their gaps,
     * undated plans put one day after the other.
     */
    fun toJson(p: ParsedPlan, name: String, start: Long): String {
        val dated = p.hasDates
        val first = p.firstDate ?: start
        val firstN = p.days.first().n
        val offs = IntArray(p.days.size)
        var prev = -1
        for ((i, d) in p.days.withIndex()) {
            var off = if (dated) (if (d.date != null) (d.date!! - first).toInt() else prev + 1) else d.n - firstN
            if (off <= prev && !dated) off = prev + 1
            if (off < prev) off = prev
            if (off < 0) off = 0
            offs[i] = off; prev = off
        }
        val ids = p.tracks.withIndex().associate { it.value to "T${it.index + 1}" }
        val sb = StringBuilder()
        sb.append("{\"v\":2,\"name\":").append(esc(name)).append(",\"start\":\"").append(LocalDate.ofEpochDay(start)).append("\",\"tracks\":[")
        sb.append(p.tracks.joinToString(",") { "{\"id\":\"${ids[it]}\",\"name\":${esc(it)}}" })
        sb.append("],\"days\":[")
        for ((i, d) in p.days.withIndex()) {
            if (i > 0) sb.append(',')
            val prot = d.items.any { EXAM.containsMatchIn(it.text) }
            sb.append("{\"n\":").append(d.n).append(",\"off\":").append(offs[i]).append(",\"prot\":").append(prot).append(",\"items\":[")
            for ((k, it) in d.items.withIndex()) {
                if (k > 0) sb.append(',')
                sb.append("{\"id\":\"").append(d.n).append('-').append(k + 1).append("\",\"t\":\"").append(ids[it.track]).append("\",\"x\":").append(esc(it.text))
                    .append(",\"m\":").append(it.minutes).append(",\"l\":[")
                sb.append(it.links.distinctBy { l -> l.url }.take(6).joinToString(",") { l -> "{\"k\":${esc(l.kind)},\"t\":${esc(l.title)},\"u\":${esc(l.url)}}" })
                sb.append("]}")
            }
            sb.append("]}")
        }
        return sb.append("]}").toString()
    }
}
