package com.studypulse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class Difficulty { BASIC, ADVANCED }

data class CourseLink(val provider: String, val url: String)
data class RecBook(val title: String, val author: String, val summary: String) {
    val url: String get() = "https://www.amazon.in/s?k=" + URLEncoder.encode("$title $author", "UTF-8")
}
data class ResourceBundle(val difficulty: Difficulty, val query: String, val courses: List<CourseLink>, val books: List<RecBook>)

/** Decides whether a topic is a quick standard topic or a steep one that deserves a structured course. */
object TopicClassifier {
    private val advanced = listOf(
        "derivative", "black-scholes", "stochastic", "monte carlo", "machine learning", "deep learning", "neural",
        "regression", "time series", "portfolio optim", "value at risk", "var model", "valuation", "dcf", "lbo", "m&a",
        "consolidation", "ifrs", "gaap", "audit", "taxation", "dynamic programming", "system design", "distributed",
        "compiler", "cryptograph", "quantitative", "econometric", "arbitrage", "hedging", "fixed income", "structured",
        "backtest", "algorithm", "options pricing", "greeks", "yield curve", "credit risk", "market risk"
    )
    private val basic = listOf(
        "introduction", "intro to", "basics", "overview", "fundamentals", "what is", "revision", "recap",
        "definition", "terminology", "glossary", "read about"
    )

    fun score(item: Item): Int {
        val t = item.text.lowercase()
        var s = 0
        for (w in advanced) if (t.contains(w)) s += 2
        for (w in basic) if (t.contains(w)) s -= 2
        if (item.minutes >= 120) s += 1
        if (t.length > 160) s += 1
        return s
    }

    fun classify(item: Item): Difficulty = if (score(item) >= 2) Difficulty.ADVANCED else Difficulty.BASIC

    /** For classifying with an LLM instead of keywords: send this prompt and read one word per line back. */
    fun llmPrompt(topics: List<String>): String = buildString {
        append("Classify each study topic as BASIC (a short video is enough) or ADVANCED (steep learning curve, needs structured training).\n")
        append("Answer with exactly one word per line, BASIC or ADVANCED, in the same order, nothing else.\n\nTopics:\n")
        topics.forEachIndexed { i, t -> append("${i + 1}. ").append(t).append('\n') }
    }
}

object ResourceBuilder {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val shelf: List<Pair<List<String>, List<RecBook>>> = listOf(
        listOf("derivative", "option", "futures", "hedging", "greeks") to listOf(
            RecBook("Options, Futures, and Other Derivatives", "John C. Hull", "The standard text on derivative pricing and risk management.")
        ),
        listOf("fixed income", "yield curve", "bond") to listOf(
            RecBook("Fixed Income Securities", "Bruce Tuckman and Angel Serrat", "Bond pricing, rates models and risk, with worked examples.")
        ),
        listOf("value at risk", "market risk", "credit risk", "risk model") to listOf(
            RecBook("Value at Risk", "Philippe Jorion", "How banks and funds measure and manage market risk.")
        ),
        listOf("valuation", "dcf", "lbo", "m&a") to listOf(
            RecBook("Investment Valuation", "Aswath Damodaran", "Every major valuation approach with practical guidance."),
            RecBook("Investment Banking: Valuation, LBOs, M&A", "Joshua Rosenbaum and Joshua Pearl", "Step-by-step models used on the banking desk.")
        ),
        listOf("financial statement", "accounting", "ifrs", "gaap", "consolidation") to listOf(
            RecBook("Financial Statement Analysis and Security Valuation", "Stephen Penman", "Reading statements to value a company."),
            RecBook("Financial Shenanigans", "Howard Schilit", "How to spot manipulated accounts.")
        ),
        listOf("audit") to listOf(
            RecBook("Auditing and Assurance Services", "Alvin Arens, Randal Elder and Mark Beasley", "Audit process, evidence and risk, widely used in courses.")
        ),
        listOf("portfolio", "value investing", "security analysis") to listOf(
            RecBook("The Intelligent Investor", "Benjamin Graham", "The classic on disciplined, value-based investing."),
            RecBook("Security Analysis", "Benjamin Graham and David Dodd", "The deeper, technical companion to value investing.")
        ),
        listOf("quantitative", "backtest", "trading", "hedge fund", "arbitrage") to listOf(
            RecBook("Quantitative Trading", "Ernest P. Chan", "Building and testing a systematic strategy from scratch."),
            RecBook("Advances in Financial Machine Learning", "Marcos Lopez de Prado", "Machine learning methods for finance, with pitfalls.")
        ),
        listOf("machine learning", "regression", "neural", "deep learning", "time series") to listOf(
            RecBook("An Introduction to Statistical Learning", "Gareth James, Daniela Witten, Trevor Hastie and Robert Tibshirani", "Clear, practical statistical learning."),
            RecBook("Hands-On Machine Learning with Scikit-Learn, Keras and TensorFlow", "Aurelien Geron", "Build working models step by step.")
        ),
        listOf("algorithm", "dynamic programming", "data structure") to listOf(
            RecBook("Introduction to Algorithms", "Thomas Cormen, Charles Leiserson, Ronald Rivest and Clifford Stein", "The comprehensive reference on algorithms.")
        ),
        listOf("system design", "distributed") to listOf(
            RecBook("Designing Data-Intensive Applications", "Martin Kleppmann", "How reliable, scalable data systems are built.")
        )
    )

    fun books(item: Item): List<RecBook> {
        val t = item.text.lowercase()
        val out = LinkedHashMap<String, RecBook>()
        for ((keys, list) in shelf) if (keys.any { t.contains(it) }) for (b in list) out.putIfAbsent(b.title, b)
        return out.values.take(2)
    }

    /** Search pages built from a plain phrase. They are searches, not specific courses. */
    fun courseLinks(q: String): List<CourseLink> = listOf(
        CourseLink("Coursera", "https://www.coursera.org/search?query=" + enc(q)),
        CourseLink("Udemy", "https://www.udemy.com/courses/search/?q=" + enc(q)),
        CourseLink("edX", "https://www.edx.org/search?q=" + enc(q))
    )

    fun forTopic(item: Item): ResourceBundle {
        val d = TopicClassifier.classify(item)
        val q = TopicLinkData.query(item)
        val courses = if (d == Difficulty.ADVANCED) listOf(
            CourseLink("Coursera", "https://www.coursera.org/search?query=" + enc(q)),
            CourseLink("Udemy", "https://www.udemy.com/courses/search/?q=" + enc(q)),
            CourseLink("edX", "https://www.edx.org/search?q=" + enc(q))
        ) else emptyList()
        return ResourceBundle(d, q, courses, books(item))
    }
}

/** Finds the best embeddable video for a topic with the YouTube Data API. Needs YOUTUBE_API_KEY in local.properties. */
object YoutubeLookup {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun firstVideoId(query: String): String? {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) return null
        cache[query]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val u = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true" +
                    "&maxResults=1&safeSearch=strict&relevanceLanguage=en&q=" + URLEncoder.encode(query, "UTF-8") + "&key=" + key
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                if (c.responseCode != 200) return@withContext null
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val id = JSONObject(body).optJSONArray("items")?.optJSONObject(0)?.optJSONObject("id")?.optString("videoId")
                if (id.isNullOrEmpty()) null else id.also { cache[query] = it }
            } catch (e: Exception) {
                null
            }
        }
    }
}
