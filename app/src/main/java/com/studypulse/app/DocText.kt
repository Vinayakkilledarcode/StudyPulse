package com.studypulse.app

/** Text extraction helpers for Word, Excel and delimited text. No Android classes, so they can be tested on a plain JVM. */
object DocText {
    fun unescape(s: String): String {
        if (!s.contains('&')) return s
        var r = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m -> String(Character.toChars(m.groupValues[1].toInt(16))) }
        r = Regex("&#(\\d+);").replace(r) { m -> String(Character.toChars(m.groupValues[1].toInt())) }
        return r.replace("&amp;", "&")
    }

    class DocxContent(val lines: List<String>, val tables: List<List<List<String>>>)

    private val TBL = Regex("<w:tbl[ >].*?</w:tbl>", RegexOption.DOT_MATCHES_ALL)
    private val PARA = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
    private val ROW = Regex("<w:tr[ >].*?</w:tr>", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("<w:tc[ >].*?</w:tc>", RegexOption.DOT_MATCHES_ALL)
    private val RUN = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br\\s*/>", RegexOption.DOT_MATCHES_ALL)

    private fun paraText(p: String): String {
        val sb = StringBuilder()
        for (m in RUN.findAll(p)) {
            if (m.groups[1] != null) sb.append(unescape(m.groupValues[1])) else sb.append(' ')
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun docx(xml: String): DocxContent {
        val lines = ArrayList<String>()
        val tables = ArrayList<List<List<String>>>()
        var last = 0
        fun paragraphs(chunk: String) {
            for (p in PARA.findAll(chunk)) {
                val t = paraText(p.value)
                if (t.isEmpty()) continue
                lines.add(if (p.value.contains("<w:numPr>")) "\u2022 $t" else t)
            }
        }
        for (t in TBL.findAll(xml)) {
            paragraphs(xml.substring(last, t.range.first))
            last = t.range.last + 1
            val rows = ArrayList<List<String>>()
            for (r in ROW.findAll(t.value)) {
                val cells = ArrayList<String>()
                for (c in CELL.findAll(r.value)) {
                    cells.add(PARA.findAll(c.value).map { paraText(it.value) }.filter { it.isNotEmpty() }.joinToString(" "))
                }
                rows.add(cells)
            }
            if (rows.isNotEmpty()) tables.add(rows)
        }
        paragraphs(xml.substring(last))
        return DocxContent(lines, tables)
    }

    // ------------------------------------------------------------------ xlsx
    private val SI = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val T = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val XROW = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val XCELL = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val ATTR_R = Regex("\\br=\"([A-Z]+)\\d+\"")
    private val ATTR_T = Regex("\\bt=\"([a-zA-Z]+)\"")
    private val V = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

    fun xlsxShared(xml: String): List<String> =
        SI.findAll(xml).map { si -> T.findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) } }.toList()

    private fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    fun xlsxRows(sheetXml: String, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        for (r in XROW.findAll(sheetXml)) {
            val body = r.groupValues[1]
            val cells = ArrayList<String>()
            var seq = 0
            for (c in XCELL.findAll(body)) {
                val attrs = c.groupValues[1]
                val inner = c.groupValues[2]
                val idx = ATTR_R.find(attrs)?.let { colIndex(it.groupValues[1]) } ?: seq
                seq = idx + 1
                val type = ATTR_T.find(attrs)?.groupValues?.get(1) ?: "n"
                val v = V.find(inner)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                val text = when (type) {
                    "s" -> shared.getOrElse(v.trim().toIntOrNull() ?: -1) { "" }
                    "inlineStr" -> T.findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v
                }
                while (cells.size < idx) cells.add("")
                if (cells.size == idx) cells.add(text.trim()) else cells[idx] = text.trim()
            }
            rows.add(cells)
        }
        return rows
    }

    // ------------------------------------------------------------------ csv / tsv
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(8).toList()
        if (sample.isEmpty()) return ','
        var best = ','
        var bestScore = 0
        for (d in charArrayOf(',', ';', '\t', '|')) {
            val counts = sample.map { l -> l.count { it == d } }
            val min = counts.minOrNull() ?: 0
            if (min >= 1 && min * sample.size > bestScore) { bestScore = min * sample.size; best = d }
        }
        return best
    }

    fun delimitedRows(text: String, delim: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        while (i < s.length) {
            val ch = s[i]
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i++ } else inQ = false
                } else sb.append(ch)
            } else when (ch) {
                '"' -> inQ = true
                delim -> { cur.add(sb.toString().trim()); sb.setLength(0) }
                '\n' -> { cur.add(sb.toString().trim()); sb.setLength(0); rows.add(cur); cur = ArrayList() }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || cur.isNotEmpty()) { cur.add(sb.toString().trim()); rows.add(cur) }
        return rows.filter { r -> r.any { it.isNotEmpty() } }
    }
}
