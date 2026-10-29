package com.studypulse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Links shown right under every study topic: a video, a PDF search and (when the plan has one) a reading link.
 * Everything opens inside the app. Any PDF reached from the PDF button is saved to My books automatically.
 */
object TopicLinkData {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** A short search phrase made from the topic text. */
    fun query(item: Item): String {
        var s = item.text.removePrefix("Practice: ").trim()
        s = s.replace(Regex("^(Do|Learn):\\s*"), "")
        s = s.lineSequence().first().substringBefore(". ")
        s = s.replace(Regex("[\"\u201C\u201D()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()
        s = s.trimEnd('.', ',', ';', ':')
        if (s.length > 90) s = s.take(90).substringBeforeLast(' ')
        return s
    }

    /** The plan's own video search for this topic if it has one, otherwise a YouTube search of the topic. */
    fun video(item: Item): String {
        val l = item.links.firstOrNull { it.url.contains("youtube.com/results") }
            ?: item.links.firstOrNull { it.url.contains("youtube.com") || it.url.contains("youtu.be") }
        if (l != null) return l.url
        return "https://m.youtube.com/results?search_query=" + enc(query(item) + " tutorial")
    }

    /**
     * The title to search for: written by the smart engine when this build has it set up (kept for the topic so it is written once),
     * otherwise made from the topic text.
     */
    suspend fun videoTitle(c: Context, item: Item): String = withContext(Dispatchers.IO) {
        ResStore.get(c, item)?.queries?.video?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val q = QueryWriter.write(c, item)
        try { ResStore.put(c, item, FinderResult(q, null, emptyList(), null, 0L, emptyList())) } catch (e: Exception) { }
        q.video.ifBlank { query(item) + " tutorial" }
    }

    /** The YouTube results page for a title. It opens in the app, so no single video has to be chosen or loaded first. */
    fun searchUrl(title: String): String = "https://m.youtube.com/results?search_query=" + enc(title)

    /** A web search for PDF study material. It is only a search: nothing found this way is called a free PDF. */
    fun pdf(item: Item): String =
        "https://www.google.com/search?hl=en&q=" + enc(query(item) + " filetype:pdf")

    /** A reading link from the plan (a website, not a video or shop page), if the topic has one. */
    fun read(item: Item): String? = item.links.firstOrNull {
        (it.kind == "Read" || it.kind == "Learn") &&
            !it.url.contains("youtube.com") && !it.url.contains("youtu.be") && !it.url.contains("amazon.") && !it.url.contains("iso.org")
    }?.url
}

@Composable
fun Chip(icon: String, t: String, onClick: () -> Unit) {
    val s = RoundedCornerShape(9.dp)
    Row(
        Modifier.clip(s).background(C.bg).border(1.dp, C.line, s)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpIcon(icon, 14.dp, C.brand, stroke = 2f)
        Text(t, color = C.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun TopicLinks(item: Item, openWeb: (String) -> Unit, modifier: Modifier = Modifier) {
    val read = TopicLinkData.read(item)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val advanced = remember(item.id) { TopicClassifier.classify(item) == Difficulty.ADVANCED }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Chip("play", "Video") {
            val vm = TopicBusy.vm
            if (vm != null) vm.openVideoSearch(item, openWeb)
            else scope.launch { openWeb(TopicLinkData.searchUrl(TopicLinkData.videoTitle(ctx, item))) }
        }
        Chip("doc", "PDF search") { openWeb(TopicLinkData.pdf(item)) }
        if (read != null) Chip("book", "Read") { openWeb(read) }
        if (advanced) Chip("book", "Courses") { openWeb("sp://res/" + item.id) }
    }
}

/** Lets the topic chips reach the app's task log without passing the view model through every screen. */
object TopicBusy {
    var vm: AppVM? = null
}
