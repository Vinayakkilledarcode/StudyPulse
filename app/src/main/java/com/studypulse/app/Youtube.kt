package com.studypulse.app

import java.net.URI

/** Host of every embed link: YouTube's privacy-friendly embed domain. Change this one line to switch hosts. */
const val EMBED_HOST = "www.youtube-nocookie.com"

/** Older builds wrote embed links with this host, which is not a real domain. Links saved with it are still understood. */
const val LEGACY_EMBED_HOST = "www.yout-ube-nocookie.com"

private val ID_RE = Regex("^[A-Za-z0-9_-]{11}$")

private fun queryParam(rawQuery: String?, key: String): String? =
    rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == key }?.getOrNull(1)

/** Video ID from a raw ID, youtube.com/watch?v=, youtu.be/, /embed/, /shorts/, /live/ or /v/ link. Null when it is not a video. */
fun extractYoutubeId(urlOrId: String): String? {
    val s = urlOrId.trim()
    if (ID_RE.matches(s)) return s
    val uri = try { URI(if (s.contains("://")) s else "https://$s") } catch (e: Exception) { return null }
    val host = (uri.host ?: return null).lowercase().removePrefix("www.").removePrefix("m.")
    val path = uri.path ?: ""
    val id = when (host) {
        "youtu.be" -> path.trim('/').substringBefore('/')
        "youtube.com", "youtube-nocookie.com", "yout-ube-nocookie.com", "music.youtube.com" -> when {
            path == "/watch" -> queryParam(uri.rawQuery, "v")
            Regex("^/(embed|shorts|live|v)/.+").matches(path) -> path.split('/').getOrNull(2)
            else -> null
        }
        else -> null
    }
    return id?.takeIf { ID_RE.matches(it) }
}

/**
 * Standard embed link for every video in the app (host in EMBED_HOST).
 * [startSeconds] fills the trailing start= value, empty when not given.
 */
fun formatYoutubeEmbedUrl(urlOrId: String, startSeconds: Int? = null): String {
    val id = extractYoutubeId(urlOrId) ?: throw IllegalArgumentException("Not a YouTube video link or ID: $urlOrId")
    return "https://$EMBED_HOST/embed/$id?playlist=$id&autoplay=1&iv_load_policy=3&loop=1&start=" +
        (startSeconds?.coerceAtLeast(0)?.toString() ?: "")
}

fun formatYoutubeEmbedUrlOrNull(urlOrId: String, startSeconds: Int? = null): String? =
    try { formatYoutubeEmbedUrl(urlOrId, startSeconds) } catch (e: IllegalArgumentException) { null }

fun isYoutubeEmbed(u: String) = u.startsWith("https://$EMBED_HOST/embed/") || u.startsWith("https://$LEGACY_EMBED_HOST/embed/")

/**
 * The player page. It uses the YouTube IFrame API so the app learns whether the video loaded or why it did not
 * (removed, embedding off, no network) through the "Android" bridge, instead of showing a blank black screen.
 */
fun youtubePlayerHtml(embedUrl: String): String {
    val id = extractYoutubeId(embedUrl) ?: return ""
    val start = Regex("[?&]start=(\\d+)").find(embedUrl)?.groupValues?.get(1) ?: "0"
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style></head>
<body><div id="p"></div>
<script>
function post(f,a){try{Android[f](a)}catch(e){}}
var t=document.createElement('script');t.src='https://www.youtube.com/iframe_api';t.onerror=function(){post('error',-3)};document.head.appendChild(t);
function onYouTubeIframeAPIReady(){
  new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',host:'https://$EMBED_HOST',
    playerVars:{autoplay:1,playsinline:1,rel:0,iv_load_policy:3,loop:1,playlist:'$id',start:$start,fs:0},
    events:{onReady:function(e){post('ready',0);try{e.target.playVideo()}catch(x){}},onStateChange:function(e){if(e.data===1||e.data===3)post('ready',0)},onError:function(e){post('error',e.data)}}});
}
</script></body></html>"""
}

/** Player #2: the same embed in a plain frame (no player script), for phones where the scripted player shows no picture. */
fun youtubeIframeHtml(embedUrl: String): String =
    "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style></head>" +
        "<body><iframe src=\"" + embedUrl.replace("&", "&amp;") + "&playsinline=1\" allow=\"autoplay; encrypted-media; picture-in-picture; fullscreen\" allowfullscreen></iframe></body></html>"
