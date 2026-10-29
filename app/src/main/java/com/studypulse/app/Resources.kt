package com.studypulse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Video, books, a confirmed free PDF (only when one was really found) and courses for one topic.
 * Every result was fetched for real, ranked and link-checked, and says in one line why it was picked.
 */
@Composable
fun ResourcesScreen(vm: AppVM, itemId: String, onClose: () -> Unit, openWeb: (String) -> Unit) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val tick = vm.tick
    val item = Plan.item(itemId)
    val scope = rememberCoroutineScope()
    var res by remember(itemId) { mutableStateOf<FinderResult?>(null) }
    var loading by remember(itemId) { mutableStateOf(true) }
    var msg by remember(itemId) { mutableStateOf("") }

    LaunchedEffect(itemId) {
        if (item != null) res = Finder.find(ctx, item)
        loading = false
    }

    fun report(url: String) {
        if (item == null) return
        scope.launch {
            ResStore.report(ctx, url)
            loading = true
            res = Finder.find(ctx, item, force = true)
            loading = false
            msg = "Thanks. That link is hidden and the topic was searched again. The report stays on this phone."
        }
    }

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Gap(18)
        Row(Modifier.fillMaxWidth()) { Pill("Close", C.ink) { onClose() } }
        Gap(18)
        if (item == null) {
            Text("This topic is no longer in your plan.", color = C.muted, fontSize = 14.sp)
            return@Column
        }
        val r = res
        val level = when (r?.queries?.level) {
            "advanced" -> Difficulty.ADVANCED
            "basic" -> Difficulty.BASIC
            else -> TopicClassifier.classify(item)
        }
        val paid = Entitlement.tier(ctx) != PlanTier.FREE

        Label(if (level == Difficulty.ADVANCED) "Advanced topic" else "Standard topic", C.brand, size = 12)
        Gap(8)
        Head(TopicLinkData.query(item).ifBlank { item.text.take(60) }, 24, tracking = -0.03)
        Gap(18)

        if (loading || r == null) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = C.brand, trackColor = C.brandSoft, strokeWidth = 4.dp, modifier = Modifier.size(42.dp))
                Text("Finding and checking resources", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            return@Column
        }

        if (msg.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth(), bg = C.warm) { Text(msg, fontSize = 13.5.sp, lineHeight = 19.sp) }
            Gap(14)
        }

        Panel(Modifier.fillMaxWidth()) {
            Label("Video", C.brand, size = 12)
            Gap(8)
            val v = r.video
            if (v != null) {
                Text(v.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.sp)
                Text("Why: ${v.why}", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                Gap(12)
                Btn("Watch video", Modifier.fillMaxWidth(), icon = "play") { openWeb(v.url) }
                Gap(8)
                Pill("Report broken link") { report(v.url) }
            } else {
                Text(
                    r.notes.firstOrNull { it.contains("video") } ?: "No video was picked for this topic.",
                    color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp
                )
                Gap(12)
                Btn("Search YouTube", Modifier.fillMaxWidth(), BtnKind.Secondary, "play") { openWeb(TopicLinkData.video(item)) }
            }
        }

        Gap(14)
        Panel(Modifier.fillMaxWidth()) {
            Label("Books", C.brand, size = 12)
            if (r.books.isNotEmpty()) {
                for (bk in r.books) {
                    Gap(14)
                    Text(bk.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.sp)
                    Text(bk.source, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    val parts = bk.why.split("\n", limit = 2)
                    Text(parts.getOrElse(1) { "" }, color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                    Text("Why: ${parts[0]}", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    Gap(10)
                    Btn("Open on Google Books", Modifier.fillMaxWidth(), BtnKind.Ghost, "book") { openWeb(bk.url) }
                    Gap(6)
                    Pill("Report broken link") { report(bk.url) }
                }
            } else {
                val shelf = ResourceBuilder.books(item)
                Gap(8)
                Text(
                    if (shelf.isEmpty()) "No book was confirmed for this topic."
                    else "No book could be checked online, so these are suggestions with a shop search link, not checked results.",
                    color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp
                )
                for (bk in shelf) {
                    Gap(14)
                    Text(bk.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(bk.author, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Text("Why: ${bk.summary}", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                    Gap(10)
                    Btn("Search for this book", Modifier.fillMaxWidth(), BtnKind.Ghost, "book") { openWeb(bk.url) }
                }
            }
        }

        Gap(14)
        Panel(Modifier.fillMaxWidth()) {
            val pdf = r.pdf
            if (pdf != null) {
                Label("Free PDF (checked)", C.brand, size = 12)
                Gap(8)
                Text(pdf.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.sp)
                Text("Why: ${pdf.why}", color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                Gap(12)
                Btn("Open PDF", Modifier.fillMaxWidth(), icon = "doc") { openWeb(pdf.url) }
                Gap(8)
                Pill("Report broken link") { report(pdf.url) }
            } else {
                Label("PDF", C.brand, size = 12)
                Gap(8)
                Text(
                    "No free PDF was confirmed for this topic. A web search for PDFs can find some, but nothing it shows has been checked.",
                    color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp
                )
                Gap(12)
                Btn("Search the web for PDFs", Modifier.fillMaxWidth(), BtnKind.Secondary, "doc") { openWeb(TopicLinkData.pdf(item)) }
            }
        }

        if (level == Difficulty.ADVANCED) {
            Gap(14)
            Panel(Modifier.fillMaxWidth()) {
                Label("Structured courses", C.brand, size = 12)
                Gap(8)
                Text(
                    "This topic has a steep learning curve. A full course teaches it in order. These buttons open a search for " +
                        "\"${r.queries.course}\" on each site. They are searches, not specific courses.",
                    color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp
                )
                Gap(12)
                if (paid) {
                    for (c in ResourceBuilder.courseLinks(r.queries.course)) {
                        Btn("Search ${c.provider}", Modifier.fillMaxWidth(), BtnKind.Secondary, "book") { openWeb(c.url) }
                        Gap(8)
                    }
                } else {
                    Btn("Unlock with Premium", Modifier.fillMaxWidth(), icon = "check") { vm.paywall = true }
                }
            }
        }

        Gap(14)
        val days = ((System.currentTimeMillis() - r.checkedAt) / 86_400_000L).toInt()
        Text(
            (if (days <= 0) "Checked today. " else "Checked $days ${if (days == 1) "day" else "days"} ago. ") +
                "Results are ranked by how well the title matches your topic, how well known the source is, and whether the link answered. " +
                "Links are checked again each week. They are suggestions, not endorsements.",
            color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp
        )
        Gap(40)
    }
}
