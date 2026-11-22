package com.novelforge.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class SearchCandidate(val title: String, val url: String)
data class ChapterLink(val title: String, val url: String)
data class Catalog(
    val title: String,
    val author: String,
    val sourceUrl: String,
    val chapters: List<ChapterLink>
)

/** 小说站爬取：按书名搜候选站、解析目录页、逐章抓正文。
 *  通用启发式实现（目录链接识别 + 正文区块提取 + GBK/UTF-8 自动识别），
 *  对多数传统小说站有效；重 JS 渲染或强反爬的站点可能抓不到，会明确报错。
 *  请只下载你有权下载的内容（公版书、已授权或你自己的作品）。 */
object NovelCrawler {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** 下载网页并按正确编码解码（不少小说站是 GBK）。 */
    private fun fetchHtml(url: String, attempt: Int = 0): String {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}（$url）")
                val bytes = resp.body?.bytes() ?: error("空响应")
                val headerCharset = resp.body?.contentType()?.charset()?.name()
                decode(bytes, headerCharset)
            }
        } catch (e: Exception) {
            // 网络抖一下很常见，给两次重试
            if (attempt < 2) {
                runCatching { Thread.sleep(700L * (attempt + 1)) }
                fetchHtml(url, attempt + 1)
            } else throw e
        }
    }

    private fun decode(bytes: ByteArray, headerCharset: String?): String {
        if (headerCharset != null) {
            runCatching { return String(bytes, Charset.forName(headerCharset)) }
        }
        val head = String(bytes.copyOfRange(0, minOf(bytes.size, 4096)), Charsets.US_ASCII)
        val meta = Regex("""charset\s*=\s*["']?\s*([a-zA-Z0-9\-_]+)""").find(head)?.groupValues?.get(1)
        if (meta != null && !meta.equals("utf-8", ignoreCase = true)) {
            runCatching { return String(bytes, Charset.forName(meta)) }
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        // UTF-8 解出来一堆替换符，多半是 GBK
        return if (utf8.count { it == '�' } > 10) {
            runCatching { String(bytes, Charset.forName("GBK")) }.getOrDefault(utf8)
        } else utf8
    }

    /** 按书名搜索候选站点（Bing 结果页解析，点某个结果再解析它的目录）。 */
    suspend fun searchByName(name: String): Result<List<SearchCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode("$name 小说 目录", "UTF-8")
            val results = mutableListOf<SearchCandidate>()

            // 主源：Bing
            runCatching {
                val html = fetchHtml("https://www.bing.com/search?q=$q&count=20")
                val regex = Regex(
                    """<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                regex.findAll(html).forEach { m ->
                    val url = m.groupValues[1]
                    val title = cleanInline(m.groupValues[2])
                    if (url.startsWith("http") && !url.contains("bing.com") && !url.contains("microsoft.com")) {
                        results.add(SearchCandidate(title.ifBlank { url }, url))
                    }
                }
            }

            // 备用：DuckDuckGo html 版
            if (results.isEmpty()) {
                runCatching {
                    val html = fetchHtml("https://html.duckduckgo.com/html/?q=$q")
                    val regex = Regex(
                        """<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
                        RegexOption.DOT_MATCHES_ALL
                    )
                    regex.findAll(html).forEach { m ->
                        var url = m.groupValues[1]
                        if (url.contains("uddg=")) {
                            val enc = url.substringAfter("uddg=").substringBefore('&')
                            url = runCatching {
                                java.net.URLDecoder.decode(enc, "UTF-8")
                            }.getOrDefault(url)
                        }
                        val title = cleanInline(m.groupValues[2])
                        if (url.startsWith("http")) {
                            results.add(SearchCandidate(title.ifBlank { url }, url))
                        }
                    }
                }
            }

            results.distinctBy { it.url }.take(12)
                .ifEmpty { error("两个搜索源都没结果。可以直接粘贴小说目录页地址，用「按地址解析目录」") }
        }
    }

    private val chapterTitleRegex = Regex("""第\s*[0-9一二三四五六七八九十百千万两]+\s*[章卷节回]""")
    private val navWords = listOf("上一章", "下一章", "上一页", "下一页", "返回", "目录", "书页", "首页", "加入书签", "推荐", "收藏", "投票")

    /** 解析目录页：书名、作者、全部章节链接（按阅读顺序）。 */
    suspend fun fetchCatalog(pageUrl: String): Result<Catalog> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetchHtml(pageUrl)
            val base = URL(pageUrl)

            val ogTitle = Regex("""<meta[^>]+property="og:title"[^>]+content="([^"]+)"""").find(html)?.groupValues?.get(1)
            val docTitle = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?.groupValues?.get(1)?.let { cleanInline(it) }.orEmpty()
            val title = (ogTitle ?: docTitle)
                .substringBefore("-").substringBefore("_").substringBefore("|").trim()
                .ifBlank { "未命名" }
            val author = Regex("""作\s*者\s*[:：]\s*([^\s<，,]+)""").find(html)?.groupValues?.get(1)?.trim().orEmpty()

            val anchorRegex = Regex("""<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val seen = LinkedHashMap<String, ChapterLink>()
            anchorRegex.findAll(html).forEach { m ->
                val href = m.groupValues[1].trim()
                val text = cleanInline(m.groupValues[2])
                if (href.isBlank() || href.startsWith("javascript") || href.startsWith("#")) return@forEach
                if (text.length < 2 || text.length > 60) return@forEach
                if (navWords.any { text == it }) return@forEach
                val resolved = runCatching { URL(base, href).toString() }.getOrNull() ?: return@forEach
                if (!resolved.startsWith("http")) return@forEach
                val isChapter = chapterTitleRegex.containsMatchIn(text)
                if (!isChapter) return@forEach
                if (!seen.containsKey(resolved)) seen[resolved] = ChapterLink(text, resolved)
            }
            var chapters = seen.values.toList()
            if (chapters.size < 3) {
                error("这个页面没识别到章节目录（可能不是目录页，或站点结构特殊/需要 JS 渲染）")
            }
            // 有的站目录是倒序（最新在前），用章节号判断并翻正
            fun numOf(t: String): Int? = Regex("""第\s*(\d+)\s*章""").find(t)?.groupValues?.get(1)?.toIntOrNull()
            val first = chapters.firstNotNullOfOrNull { numOf(it.title) }
            val last = chapters.mapNotNull { numOf(it.title) }.lastOrNull()
            if (first != null && last != null && first > last) chapters = chapters.reversed()
            Catalog(title = title, author = author, sourceUrl = pageUrl, chapters = chapters)
        }
    }

    /** 抓一章正文并清洗成纯文本。 */
    suspend fun fetchChapterText(chapterUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetchHtml(chapterUrl)
            // 候选 1：常见正文容器（id/class 含 content、chapter、txt、read 等）
            var best = ""
            val containerRegex = Regex(
                """<div[^>]+(?:id|class)="[^"]*(?:content|chapter|txt|read|article)[^"]*"[^>]*>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            containerRegex.findAll(html).forEach { m ->
                val cleaned = htmlToText(m.groupValues[1])
                if (cleaned.length > best.length) best = cleaned
            }
            // 候选 2：全页所有 <p> 段落
            if (best.length < 300) {
                val ps = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(html).joinToString("\n") { htmlToText(it.groupValues[1]) }
                if (ps.length > best.length) best = ps
            }
            best.takeIf { it.length >= 50 } ?: error("这一章没提取到正文（站点结构特殊或被拦截）")
        }
    }

    private fun cleanInline(html: String): String =
        decodeEntities(html.replace(Regex("<[^>]+>"), "")).trim()

    /** HTML 片段转纯文本：换行归一、去标签、解实体、滤掉导航垃圾行。 */
    private fun htmlToText(fragment: String): String {
        var s = fragment
        s = s.replace(Regex("""<script.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("""<style.*?</style>""", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = decodeEntities(s)
        val lines = s.lines().map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !navWords.contains(line) &&
                    !line.startsWith("本章") &&
                    !line.contains("最新网址") &&
                    !line.contains("请记住本书") &&
                    line.length > 1
            }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun decodeEntities(s: String): String {
        var out = s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        out = Regex("""&#(\d+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { if (it in 1..0x10FFFF) it.toChar().toString() else "" } ?: ""
        }
        return out.replace("&amp;", "&")
    }
}
