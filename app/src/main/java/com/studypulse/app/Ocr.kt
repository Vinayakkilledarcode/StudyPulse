package com.studypulse.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Reads text out of scanned PDFs and photos on the phone with Google ML Kit. No internet needed, nothing leaves the phone. */
object Ocr {
    class Result(val text: String, val warnings: List<String>)

    private const val MAX_PAGES = 30

    fun image(bytes: ByteArray): Result {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("This image could not be opened.")
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val t = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0))).text
            return Result(t, listOf("Read from a picture with text recognition. Check the topics."))
        } finally { rec.close(); bmp.recycle() }
    }

    fun pdf(c: Context, bytes: ByteArray, tr: Trace = Trace.NONE): Result {
        val tmp = File.createTempFile("scan", ".pdf", c.cacheDir)
        val warnings = ArrayList<String>()
        val sb = StringBuilder()
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            tmp.writeBytes(bytes)
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            try {
                val pages = minOf(r.pageCount, MAX_PAGES)
                if (r.pageCount > MAX_PAGES) warnings.add("Only the first $MAX_PAGES pages were scanned.")
                for (i in 0 until pages) {
                    tr.step("Text recognition: page " + (i + 1) + " of " + pages)
                    val page = r.openPage(i)
                    var bmp: Bitmap? = null
                    try {
                        val w = 1400
                        val h = maxOf(1, (page.height * w.toFloat() / maxOf(1, page.width)).toInt())
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val t = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0))).text
                        if (t.isBlank()) warnings.add("Page ${i + 1} could not be read.") else sb.append(t).append('\n')
                    } catch (e: Exception) {
                        warnings.add("Page ${i + 1} could not be read.")
                    } finally {
                        page.close()
                        bmp?.recycle()
                    }
                }
            } finally { r.close(); pfd.close() }
        } finally { rec.close(); tmp.delete() }
        warnings.add(0, "This looks like a scan, so it was read with text recognition. Check the topics.")
        return Result(sb.toString(), warnings)
    }
}
