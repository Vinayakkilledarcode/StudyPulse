package com.studypulse.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/** Everything opens inside the app: YouTube, web pages, PDFs. Nothing sends you to another app. */

private fun inAppUrl(u: String): String =
    u.replace("://www.youtube.com", "://m.youtube.com").replace("://youtube.com", "://m.youtube.com")

private fun isYoutube(u: String) = u.contains("youtube.com") || u.contains("youtu.be")

// ======================================================================= NOTES & LINKS

@Composable
fun LinksScreen(vm: AppVM, track: String, onClose: () -> Unit, openWeb: (String) -> Unit, openLibrary: () -> Unit) {
    BackHandler { onClose() }
    val tick = vm.tick
    val e = vm.e
    val s = e.store
    var ytq by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var note by remember(track) { mutableStateOf(s.note(track)) }

    // days shown: today's topics for this track; if none, the next day that has some
    var shown = e.itemsOn(e.today()).filter { it.track == track }
    if (shown.isEmpty()) {
        var d = e.today() + 1
        while (d < e.today() + 60 && shown.isEmpty()) {
            shown = e.itemsOn(d).filter { it.track == track }
            d++
        }
    }
    val dayList = shown.map { it.day }.distinctBy { it.n }

    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Back", C.ink) { onClose() }
            Spacer(Modifier.width(12.dp))
            Text(trackLong(track) + " \u00B7 notes and links", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (d in dayList) {
                val epoch = shown.first { it.day.n == d.n }.let { e.sched(it) }
                Panel(Modifier.fillMaxWidth(), border = C.brand) {
                    Label("Plan day ${d.n} \u00B7 ${fmtDay(epoch)}", C.brand, size = 11)
                    Spacer(Modifier.height(8.dp))
                    val dayItems = shown.filter { it.day.n == d.n }
                    for ((k, x) in dayItems.withIndex()) {
                        if (k > 0) Spacer(Modifier.height(8.dp))
                        Text(x.text, color = if (k == 0) C.ink else C.muted, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    val links = dayItems.flatMap { it.links }.distinctBy { it.url }
                    if (links.isEmpty()) Text("No saved links for this day. Use the Video, PDF and Read buttons on the topic.", color = C.muted, fontSize = 12.5.sp)
                    for (l in links) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                                .background(C.card2).clickable { vm.openLink(l.url, openWeb) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(30.dp)) { SpIcon(if (isYoutube(l.url)) "play" else "link", 18.dp, C.brand) }
                            Column {
                                Label(l.kind, C.muted, size = 10)
                                Text(l.title, color = C.ink, fontSize = 13.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
            if (dayList.isEmpty()) {
                Panel(Modifier.fillMaxWidth()) { Text("No topics planned for this track right now.", color = C.muted, fontSize = 13.5.sp) }
            }

            Panel(Modifier.fillMaxWidth()) {
                Label("Search YouTube", C.brand, size = 11)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        ytq, { ytq = it }, Modifier.weight(1f), singleLine = true, colors = tfColors(),
                        placeholder = { Text("topic to search", color = C.muted) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("Search", C.brand, bg = C.brandSoft, border = C.brandSoft) {
                        if (ytq.isNotBlank()) openWeb("https://m.youtube.com/results?search_query=" + URLEncoder.encode(ytq.trim(), "UTF-8"))
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Label("Your links", C.brand, size = 11)
                Spacer(Modifier.height(8.dp))
                val mine = s.userLinks().withIndex().filter { it.value.track == track }
                for (x in mine) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            x.value.title, color = C.ink, fontSize = 13.sp,
                            modifier = Modifier.weight(1f).clickable { vm.openLink(x.value.url, openWeb) }
                        )
                        Pill("Remove", C.muted) { s.removeUserLink(x.index); vm.bump() }
                    }
                }
                if (mine.isEmpty()) Text("Save a YouTube video, playlist or website here.", color = C.muted, fontSize = 12.5.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newTitle, { newTitle = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tfColors(), placeholder = { Text("name", color = C.muted) })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(newUrl, { newUrl = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tfColors(), placeholder = { Text("https://...", color = C.muted) })
                Spacer(Modifier.height(8.dp))
                Pill("Save link", C.brand, bg = C.brandSoft, border = C.brandSoft) {
                    var u = newUrl.trim()
                    if (u.isNotEmpty()) {
                        if (!u.startsWith("http")) u = "https://$u"
                        s.addUserLink(UserLink(track, if (newTitle.isBlank()) u else newTitle.trim(), u))
                        newTitle = ""; newUrl = ""
                        vm.bump()
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Label("Notes", C.brand, size = 11)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it; s.setNote(track, it) }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors = tfColors(), placeholder = { Text("mistakes, formulas, reminders...", color = C.muted) }
                )
            }

            BigBtn("My books (PDF library)", bg = C.brandSoft, fg = C.brand) { openLibrary() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ======================================================================= IN-APP BROWSER

/** Lets the YouTube player page tell the app that the video is ready or failed. */
class VideoBridge(private val post: (String, Int) -> Unit) {
    @android.webkit.JavascriptInterface fun ready(code: Int) { post("ready", 0) }
    @android.webkit.JavascriptInterface fun error(code: Int) { post("error", code) }
}

private fun videoErrorText(code: Int): String = when (code) {
    100 -> "This video was removed or is private."
    101, 150 -> "The owner of this video does not allow it to play in other apps."
    153 -> "YouTube could not verify this app. Try again, or open the video in YouTube."
    2, 5 -> "The player hit an error while loading this video."
    -2 -> "There is no internet connection."
    -3 -> "The YouTube player could not be reached. Check your connection."
    else -> "The video did not load in time."
}

private const val H264_ONLY_JS = """(function(){
function bad(t){return /vp0?9|av01|av1|vp8|hev1|hvc1|dvh1|dvhe/i.test(t||'');}
try{if(window.MediaSource&&MediaSource.isTypeSupported){var o=MediaSource.isTypeSupported.bind(MediaSource);MediaSource.isTypeSupported=function(t){return bad(t)?false:o(t)};}}catch(e){}
try{var p=HTMLMediaElement.prototype,c=p.canPlayType;p.canPlayType=function(t){return bad(t)?'':c.call(this,t)};}catch(e){}
})();"""

/** Opens a web page in the app. The special address "sp://video" first finds the video title, with its steps shown in a strip, then opens YouTube. */
@Composable
fun WebScreen(vm: AppVM, startUrl0: String, onClose: () -> Unit) {
    if (startUrl0 == "sp://video") VideoFinder(vm, onClose) else WebScreenBody(vm, startUrl0, onClose)
}

@Composable
private fun VideoFinder(vm: AppVM, onClose: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { url = vm.findVideoUrl() }
    val u = url
    if (u != null) {
        WebScreenBody(vm, u, onClose)
    } else {
        BackHandler { onClose() }
        Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().padding(horizontal = 14.dp)) {
            Gap(18)
            Row(Modifier.fillMaxWidth()) { Pill("Close", C.ink) { onClose() } }
            Gap(22)
            InlineTaskLine(vm.busyLines)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebScreenBody(vm: AppVM, startUrl0: String, onClose: () -> Unit) {
    // a plain youtube.com / youtu.be video link is played with the same embed player as every other video
    val startUrl = remember(startUrl0) {
        val id0 = extractYoutubeId(startUrl0)
        if (id0 != null && !isYoutubeEmbed(startUrl0)) formatYoutubeEmbedUrl(id0) else startUrl0
    }
    var custom by remember { mutableStateOf<View?>(null) }
    var customCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    val ctx = LocalContext.current
    val act = remember { ctx.findActivity() }
    val videoId = remember(startUrl) { extractYoutubeId(startUrl) }
    val isVideo = videoId != null
    val isEmbed = remember(startUrl) { isYoutubeEmbed(startUrl) }
    val isYt = remember(startUrl) { startUrl.contains("youtube.com") || startUrl.contains("youtu.be") }
    val fullscreen = isVideo || custom != null

    // The in-app player reports loading / ready / error, so a failed video shows a message and not a black screen.
    var vState by remember(startUrl) { mutableStateOf(if (isEmbed) "loading" else "ready") }
    var vCode by remember(startUrl) { mutableStateOf(0) }
    var attempt by remember(startUrl) { mutableStateOf(0) }
    val mainH = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Three players, tried in turn until one shows a picture. The YouTube page itself (2) is the one that works on the most phones,
    // so it goes first; then 0 scripted player, then 1 plain frame.
    val modeBox = remember(startUrl) { mutableStateOf(2) }
    var loadedMode by remember(startUrl) { mutableStateOf(2) }
    fun nextMode(m: Int) = when (m) { 2 -> 0; 0 -> 1; else -> 2 }
    val wvMajor = remember {
        try { androidx.webkit.WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
    }
    LaunchedEffect(vState, attempt, modeBox.value) {
        if (vState == "loading") {
            when (modeBox.value) {
                1 -> { kotlinx.coroutines.delay(3500); if (vState == "loading") vState = "ready" }
                2 -> { kotlinx.coroutines.delay(15000); if (vState == "loading") modeBox.value = 0 }
                else -> { kotlinx.coroutines.delay(12000); if (vState == "loading") modeBox.value = 1 }
            }
        }
    }
    fun loadPlayer(wv: WebView) {
        vState = "loading"
        loadedMode = modeBox.value
        when (modeBox.value) {
            1 -> wv.loadDataWithBaseURL("https://" + EMBED_HOST, youtubeIframeHtml(startUrl), "text/html", "utf-8", null)
            2 -> {
                val st = try { android.net.Uri.parse(startUrl).getQueryParameter("start")?.toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
                wv.loadUrl("https://m.youtube.com/watch?v=$videoId" + (if (st > 0) "&t=${st}s" else ""))
            }
            else -> wv.loadDataWithBaseURL("https://" + EMBED_HOST, youtubePlayerHtml(startUrl), "text/html", "utf-8", null)
        }
    }
    LaunchedEffect(modeBox.value) {
        if (modeBox.value != loadedMode) holder[0]?.let { loadPlayer(it) }
    }

    // Any video plays full screen and landscape; leaving it restores the normal orientation and bars.
    DisposableEffect(fullscreen) {
        val a = act
        if (fullscreen && a != null) {
            a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val c = androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView)
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen && a != null) {
                a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        val wv = holder[0]
        if (custom != null) {
            customCb?.onCustomViewHidden()
            custom = null
        } else if (!isEmbed && wv != null && wv.canGoBack()) wv.goBack()
        else onClose()
    }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
        }
    }
    // After the screen turns to landscape, or the app comes back from the background, redraw the video layer so the picture returns.
    LaunchedEffect(fullscreen) {
        kotlinx.coroutines.delay(500)
        holder[0]?.let { it.requestLayout(); it.invalidate() }
    }
    DisposableEffect(act) {
        val lo = act as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            val wv = holder[0]
            if (wv != null && ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                wv.onResume()
                wv.postDelayed({ wv.requestLayout(); wv.invalidate() }, 300)
            }
        }
        lo?.lifecycle?.addObserver(obs)
        onDispose { lo?.lifecycle?.removeObserver(obs) }
    }

    Column(
        Modifier.fillMaxSize().background(if (fullscreen) Color.Black else C.bg)
            .then(if (fullscreen) Modifier else Modifier.systemBarsPadding())
    ) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Close", C.ink) { onClose() }
                Spacer(Modifier.width(10.dp))
                Text(title, color = C.ink, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Pill("Reload", C.ink) { holder[0]?.reload() }
            }
            if (progress in 1..99) Bar(progress / 100f, mod = Modifier.padding(horizontal = 12.dp))
        }
        if (isVideo && custom == null) {
            // our own bar, above the video, so it never sits on top of YouTube's header
            Row(
                Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Pill("Close", Color.White, Color(0x99000000), Color(0x33FFFFFF)) { onClose() }
                Pill("No picture? Try another player", Color.White, Color(0x99000000), Color(0x33FFFFFF)) {
                    modeBox.value = nextMode(modeBox.value)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { cx ->
                    WebView(cx).apply {
                        setBackgroundColor(if (isVideo) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        // Video needs a hardware layer; without it some phones play the sound and show nothing.
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        if (isEmbed || isYt) {
                            // Present as a normal mobile browser (not an embedded "wv" view) so the player serves its regular video stream.
                            settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        if (isVideo || isYt) {
                            // Many phones play the sound but show no picture when YouTube picks VP9/AV1 and the phone cannot decode it.
                            // This script runs before the player starts and hides those formats, so YouTube serves H.264, which every phone decodes.
                            try {
                                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                        this, H264_ONLY_JS,
                                        setOf("https://www.youtube-nocookie.com", "https://www.youtube.com", "https://m.youtube.com")
                                    )
                                }
                            } catch (ex: Exception) { }
                        }
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        if (isEmbed) {
                            addJavascriptInterface(
                                VideoBridge { what, code ->
                                    mainH.post {
                                        if (what == "ready") vState = "ready"
                                        else if (code == 100 || code == 101 || code == 150 || modeBox.value == 1) { vCode = code; vState = "error" }
                                        else modeBox.value = nextMode(modeBox.value)   // try the next player
                                    }
                                },
                                "Android"
                            )
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (isEmbed && modeBox.value != 2 && request.isForMainFrame) return true // the player page never navigates away
                                val u = request.url.toString()
                                if (u.startsWith("http://") || u.startsWith("https://")) {
                                    if (u.substringBefore('?').lowercase().endsWith(".pdf")) {
                                        vm.downloadBook(u, view.settings.userAgentString, CookieManager.getInstance().getCookie(u))
                                        return true
                                    }
                                    return false
                                }
                                return true // never hand off to another app
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (isEmbed && modeBox.value == 2) {
                                    vState = "ready"
                                    // hide the YouTube header and "Open App" banner (our own bar sits above), then start the video
                                    view.evaluateJavascript(
                                        "(function(){try{var s=document.createElement('style');s.textContent='ytm-mobile-topbar-renderer,ytm-app-promo-renderer,ytm-open-app-promo-renderer{display:none!important}';document.head.appendChild(s);var v=document.querySelector('video');if(v){v.play()}}catch(e){}})()",
                                        null
                                    )
                                }
                                if (isYt && !isEmbed) {
                                    // YouTube search and watch pages: hide only the "Open App" banners
                                    view.evaluateJavascript(
                                        "(function(){try{var s=document.createElement('style');s.textContent='ytm-app-promo-renderer,ytm-open-app-promo-renderer,ytm-mealbar-promo-renderer{display:none!important}';document.head.appendChild(s)}catch(e){}})()",
                                        null
                                    )
                                }
                                title = view.title ?: ""
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                if (isEmbed && request?.isForMainFrame == true) mainH.post { vCode = -2; vState = "error" }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            override fun getDefaultVideoPoster(): android.graphics.Bitmap? =
                                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                custom = view
                                customCb = callback
                            }
                            override fun onHideCustomView() {
                                custom = null
                                customCb = null
                            }
                        }
                        setDownloadListener { url, userAgent, _, _, _ ->
                            vm.downloadBook(url, userAgent, CookieManager.getInstance().getCookie(url))
                        }
                        holder[0] = this
                        if (isEmbed) loadPlayer(this)
                        else loadUrl(inAppUrl(startUrl))
                    }
                }
            )
            val cv = custom
            if (cv != null) {
                AndroidView(modifier = Modifier.fillMaxSize().background(Color.Black), factory = { cv })
            }
            if (isEmbed && vState != "ready" && cv == null) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    if (vState == "loading") {
                        CircularProgressIndicator(color = Color(0xFFCDD2D7), trackColor = Color(0xFF2A2E33), strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Loading video\u2026", color = Color(0xFFCDD2D7), fontSize = 14.sp)
                    } else {
                        Text("The video could not play", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            videoErrorText(vCode), color = Color(0xFF9AA3AD), fontSize = 13.5.sp, lineHeight = 19.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                        )
                        if (wvMajor in 1..99) {
                            Text(
                                "Your Android System WebView is old (version $wvMajor). Updating it in Google Play fixes most video problems.",
                                color = Color(0xFFE6B07A), fontSize = 12.5.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Btn("Try again", kind = BtnKind.Secondary, icon = "reload") {
                                    attempt++
                                    holder[0]?.let { loadPlayer(it) }
                                }
                                Btn("Another player", kind = BtnKind.Secondary) {
                                    modeBox.value = nextMode(modeBox.value)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Btn("Open in YouTube", icon = "play") {
                                    try {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")
                                            )
                                        )
                                    } catch (e: Exception) { }
                                }
                                if (wvMajor in 1..99) Btn("Update WebView", kind = BtnKind.Secondary) {
                                    try {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("market://details?id=com.google.android.webview")
                                            )
                                        )
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================= MY BOOKS

@Composable
fun LibraryScreen(vm: AppVM, onClose: () -> Unit, openPdf: (Int) -> Unit) {
    BackHandler { onClose() }
    val tick = vm.tick
    var url by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addBookFromUri(uri)
    }
    val books = vm.e.store.books()
    var del by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Back", C.ink) { onClose() }
            Spacer(Modifier.width(12.dp))
            Text("My books", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Label("Add a book", C.brand, size = 11)
                Spacer(Modifier.height(10.dp))
                BigBtn("Add PDF from phone", bg = C.brandSoft, fg = C.brand) { picker.launch(arrayOf("application/pdf")) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tfColors(),
                    placeholder = { Text("direct link to a PDF (https://...pdf)", color = C.muted) }
                )
                Spacer(Modifier.height(8.dp))
                BigBtn("Download PDF into the app", bg = C.brandSoft, fg = C.brand) {
                    var u = url.trim()
                    if (u.isNotEmpty()) {
                        if (!u.startsWith("http")) u = "https://$u"
                        vm.downloadBook(u)
                        url = ""
                    }
                }
                if (vm.status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.status, color = C.muted, fontSize = 12.5.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Tip: open any site in the app. If it links to a PDF, the app saves it here by itself.", color = C.muted, fontSize = 12.sp)
            }
            if (books.isEmpty()) {
                Panel(Modifier.fillMaxWidth()) { Text("No books yet.", color = C.muted, fontSize = 13.5.sp) }
            }
            for ((i, b) in books.withIndex()) {
                Panel(Modifier.fillMaxWidth(), border = C.brand, onClick = { openPdf(i) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = C.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (b.last > 0) "Continue from page ${b.last + 1}" else "Not opened yet", color = C.muted, fontSize = 12.5.sp)
                        }
                        Pill("Delete", C.muted) { del = i }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (del >= 0) {
        SpDialog(
            "Delete this book?", onDismiss = { del = -1 }, border = C.line, confirmText = "Delete", dismissText = "Cancel",
            onConfirm = { vm.deleteBook(del); del = -1 }
        ) { Text("It is removed from this app only.", color = C.muted, fontSize = 13.5.sp) }
    }
}

// ======================================================================= PDF READER

private class PdfDoc(file: File) {
    val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    private val lock = Any()
    val count: Int get() = renderer.pageCount

    fun render(i: Int, w: Int): Bitmap? = synchronized(lock) {
        try {
            val pg = renderer.openPage(i)
            val h = (w * pg.height.toFloat() / pg.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pg.close()
            bmp
        } catch (ex: Throwable) { null }
    }

    fun close() = synchronized(lock) {
        try { renderer.close(); pfd.close() } catch (ex: Throwable) {}
    }
}

@Composable
fun PdfScreen(vm: AppVM, idx: Int, onClose: () -> Unit) {
    BackHandler { onClose() }
    val book = vm.e.store.books().getOrNull(idx)
    if (book == null) { onClose(); return }
    val file = vm.bookFile(book)
    val doc: PdfDoc? = remember(file.path) { try { PdfDoc(file) } catch (ex: Throwable) { null } }
    DisposableEffect(file.path) { onDispose { doc?.close() } }

    val ctx = LocalContext.current
    val density = LocalDensity.current
    val basePx = ctx.resources.displayMetrics.widthPixels
    var zoom by remember { mutableStateOf(1f) }
    val widthPx = (basePx * zoom).toInt()
    val widthDp = with(density) { widthPx.toDp() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hs = rememberScrollState()
    var slider by remember { mutableStateOf(book.last.toFloat()) }

    LaunchedEffect(doc) {
        if (doc != null && book.last in 0 until doc.count) state.scrollToItem(book.last)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { p ->
            slider = p.toFloat()
            vm.e.store.setLastPage(idx, p)
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Close", C.ink) { onClose() }
            Spacer(Modifier.width(10.dp))
            Text(book.name, color = C.ink, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Pill("\u2212", C.ink) { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) }
            Spacer(Modifier.width(6.dp))
            Pill("+", C.ink) { zoom = (zoom + 0.25f).coerceAtMost(3f) }
        }
        if (doc == null) {
            Text("This PDF could not be opened (it may be password protected or damaged).", color = C.accent, modifier = Modifier.padding(16.dp))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(hs)) {
                LazyColumn(Modifier.width(widthDp).fillMaxHeight(), state = state) {
                    items(doc.count) { i ->
                        val bmp by produceState<Bitmap?>(null, i, widthPx) {
                            value = withContext(Dispatchers.IO) { doc.render(i, widthPx) }
                        }
                        val b = bmp
                        if (b != null) {
                            Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.width(widthDp).padding(bottom = 6.dp))
                        } else {
                            Box(Modifier.width(widthDp).height(widthDp * 1.4f).background(C.card))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Page ${slider.toInt() + 1} / ${doc.count}", color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { scope.launch { state.scrollToItem(slider.toInt()) } },
                    valueRange = 0f..(doc.count - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    colors = SliderDefaults.colors(thumbColor = C.brand, activeTrackColor = C.brand, inactiveTrackColor = C.line)
                )
            }
        }
    }
}
