package com.studypulse.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads the text and tables out of an uploaded file: PDF, Word, Excel, CSV or plain text. */
object PlanSource {
    class Loaded(val name: String, val tables: List<List<List<String>>>, val lines: List<String>, val warnings: List<String> = emptyList())

    private const val MAX_FILE = 25_000_000
    private const val MAX_ENTRY = 30_000_000

    fun read(c: Context, uri: Uri, t: Trace = Trace.NONE): Loaded {
        var fileName = "Study plan"
        c.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && cur.moveToFirst()) fileName = cur.getString(i) ?: fileName
        }
        t.step("Opening " + fileName)
        val bytes = c.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_FILE) }
            ?: throw IllegalArgumentException("The file could not be opened.")
        if (bytes.isEmpty()) throw IllegalArgumentException("The file is empty.")
        t.note(String.format(java.util.Locale.US, "%.1f MB", bytes.size / 1_000_000.0))
        t.note("File type: " + when {
            startsWith(bytes, "%PDF") -> "PDF document"
            startsWith(bytes, "PK") -> "Word or Excel document"
            startsWith(bytes, "\u0089PNG") || startsWith(bytes, "\u00FF\u00D8") -> "picture"
            else -> "text file"
        })
        val base = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifEmpty { "Study plan" }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            startsWith(bytes, "%PDF") -> pdf(c, bytes, base, t)
            startsWith(bytes, "PK") -> archive(bytes, base, t)
            startsWith(bytes, "\u0089PNG") || startsWith(bytes, "\u00FF\u00D8") -> picture(bytes, base, t)
            ext == "doc" || ext == "xls" || ext == "ppt" || startsWith(bytes, "\u00D0\u00CF\u0011\u00E0") ->
                throw IllegalArgumentException("Old .doc and .xls files are not supported. Save the file as .docx or .xlsx and try again.")
            else -> text(bytes, base, ext, t)
        }
    }

    private fun startsWith(b: ByteArray, s: String): Boolean {
        if (b.size < s.length) return false
        for (i in s.indices) if (b[i] != s[i].code.toByte()) return false
        return true
    }

    private fun readLimited(ins: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IllegalArgumentException("The file is too large (limit 25 MB).")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun pdf(c: Context, bytes: ByteArray, name: String, t: Trace): Loaded {
        t.step("Reading the PDF text")
        PDFBoxResourceLoader.init(c.applicationContext)
        val doc = try { PDDocument.load(ByteArrayInputStream(bytes)) } catch (e: Exception) {
            throw IllegalArgumentException("This PDF is locked or damaged and cannot be read.")
        }
        var pages = 0
        val txt = try {
            val st = PDFTextStripper()
            st.sortByPosition = true
            pages = doc.numberOfPages
            st.getText(doc)
        } finally { doc.close() }
        t.note(pages.toString() + " pages, " + txt.trim().length + " characters")
        if (txt.isNotBlank()) {
            val ls = txt.lines().map { it.trim() }.filter { it.isNotEmpty() }
            t.note(ls.size.toString() + " lines, about " + txt.split(Regex("\\s+")).count { it.isNotEmpty() } + " words")
            t.note("Starts with: " + ls.first().take(70))
        }
        if (txt.isBlank()) {
            t.step("No text layer found: scanning the pages (text recognition)")
            val o = Ocr.pdf(c, bytes, t)
            if (o.text.isBlank()) throw IllegalArgumentException("No text could be read from this PDF, even with text recognition. Try a clearer scan.")
            t.note("Recognised " + o.text.trim().length + " characters")
            return Loaded(name, emptyList(), o.text.lines(), o.warnings)
        }
        return Loaded(name, emptyList(), txt.lines())
    }

    private fun picture(bytes: ByteArray, name: String, t: Trace): Loaded {
        t.step("Reading the text in the picture (text recognition)")
        val o = Ocr.image(bytes)
        if (o.text.isBlank()) throw IllegalArgumentException("No text could be read from this picture.")
        return Loaded(name, emptyList(), o.text.lines(), o.warnings)
    }

    private fun archive(bytes: ByteArray, name: String, t: Trace): Loaded {
        t.step("Unpacking the document")
        var docXml: String? = null
        var shared: String? = null
        val sheets = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                val wanted = n == "word/document.xml" || n == "xl/sharedStrings.xml" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                if (!wanted) continue
                val s = String(readLimited(z, MAX_ENTRY), Charsets.UTF_8)
                when {
                    n == "word/document.xml" -> docXml = s
                    n == "xl/sharedStrings.xml" -> shared = s
                    else -> sheets.add(s)
                }
            }
        }
        val d = docXml
        if (d != null) {
            val c = DocText.docx(d)
            t.note("Word document: " + c.lines.size + " lines, " + c.tables.size + " tables")
            c.lines.firstOrNull { it.isNotBlank() }?.let { t.note("Starts with: " + it.trim().take(70)) }
            return Loaded(name, c.tables, c.lines)
        }
        if (sheets.isNotEmpty()) {
            val sst = DocText.xlsxShared(shared ?: "")
            val tables = sheets.map { DocText.xlsxRows(it, sst) }.filter { it.isNotEmpty() }
            t.note("Spreadsheet: " + tables.size + " sheets, " + tables.sumOf { it.size } + " rows")
            return Loaded(name, tables, tables.firstOrNull()?.map { r -> r.filter { it.isNotEmpty() }.joinToString(" ") } ?: emptyList())
        }
        throw IllegalArgumentException("This file is not a Word (.docx) or Excel (.xlsx) document.")
    }

    private fun text(bytes: ByteArray, name: String, ext: String, t: Trace): Loaded {
        t.step("Reading the text file")
        val cs = when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }
        val s = String(bytes, cs).removePrefix("\uFEFF")
        t.note(s.lines().count { it.isNotBlank() }.toString() + " lines of text")
        if (s.count { it == '\u0000' } > 20) throw IllegalArgumentException("This file type is not supported. Use PDF, Word, Excel, CSV or text.")
        val asTable = ext == "csv" || ext == "tsv" || ext == "tab" || (s.lineSequence().take(5).filter { it.isNotBlank() }.all { it.contains('\t') })
        val tables = if (asTable) listOf(DocText.delimitedRows(s, DocText.detectDelimiter(s))) else emptyList()
        return Loaded(name, tables, s.lines())
    }
}
