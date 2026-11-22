package com.novelforge.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** 书城搜索到的一条结果 */
data class BookHit(
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val author: String,
    val intro: String,
    val url: String
)

data class SourceChapter(val title: String, val url: String)

data class SourceBook(
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val author: String,
    val intro: String,
    val sourceUrl: String,
    val chapters: List<SourceChapter>
)

/** 一个书源。三个动作：搜索、拿目录、拿正文。 */
interface NovelSource {
    val id: String
    val name: String
    val hosts: List<String>

    suspend fun search(keyword: String): List<BookHit>
    suspend fun catalog(hit: BookHit): SourceBook
    suspend fun chapter(url: String): String
}

// ———————————————————— 网络 ————————————————

private val UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0 Mobile Safari/537.36"

internal val sourceHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal fun decodeBody(text: ByteArray, headerCharset: String?): String {
    headerCharset?.let { cs ->
        if (!cs.equals("iso-8859-1", true)) {
            runCatching { return String(text, Charset.forName(cs)) }
        }
    }
    val head = String(text.copyOfRange(0, minOf(text.size, 3000)), Charsets.ISO_8859_1)
    val meta = Regex("""charset\s*=\s*["']?\s*([a-zA-Z0-9_\-]+)""")
        .find(head)?.groupValues?.getOrNull(1)
    if (meta != null && !meta.equals("utf-8", true) && !meta.equals("utf8", true)) {
        runCatching { return String(text, Charset.forName(meta)) }
    }
    val utf8 = String(text, Charsets.UTF_8)
    if (utf8.count { it == '\uFFFD' } > 3) {
        runCatching { String(text, Charset.forName("GBK")) }.let { if (it.isSuccess) return it.getOrThrow() }
    }
    return utf8
}

internal fun httpGet(url: String, referer: String? = null, attempt: Int = 0): String {
    return try {
        val b = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        referer?.let { b.header("Referer", it) }
        sourceHttp.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: error("空响应")
            decodeBody(bytes, resp.body?.contentType()?.charset()?.name())
        }
    } catch (e: Exception) {
        if (attempt < 2) {
            Thread.sleep(600L * (attempt + 1))
            httpGet(url, referer, attempt + 1)
        } else throw e
    }
}

internal fun httpPost(
    url: String,
    fields: List<Pair<String, String>>,
    bodyCharset: String,
    referer: String? = null,
    attempt: Int = 0
): String {
    return try {
        val enc = Charset.forName(bodyCharset)
        val body = FormBody.Builder(enc).apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val b = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .post(body)
        referer?.let { b.header("Referer", it) }
        sourceHttp.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: error("空响应")
            decodeBody(bytes, resp.body?.contentType()?.charset()?.name())
        }
    } catch (e: Exception) {
        if (attempt < 2) {
            Thread.sleep(600L * (attempt + 1))
            httpPost(url, fields, bodyCharset, referer, attempt + 1)
        } else throw e
    }
}

// ———————————————————— 解析小工具 ————————————————

internal fun stripTags(html: String): String =
    html.replace(Regex("(?is)<script.*?</script>"), "")
        .replace(Regex("(?is)<style.*?</style>"), "")
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("(?s)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

/** 清掉站点广告、导航、报错提示这类行 */
internal val junkLine = Regex(
    "本章未完|点击下一页|加入书签|投推荐票|求收藏|求订阅|手机用户请|一秒记住|" +
        "本站网址|最新网址|请记住本站|百度搜索|记住本站|天才一秒|章节报错|" +
        "请收藏本站|首发地址|无弹窗|广告|最新章节目录|全文阅读尽在|小说网.*域名|" +
        "内容加载|请稍后|作者有话说|如果您觉得|手机版阅读网址"
)

private val quoteOnly = Regex("^[\"'“”‘’「」]+$")

internal fun cleanText(raw: String): String {
    val sb = StringBuilder()
    raw.split("\n").forEach { line ->
        val s = line.replace('\u3000', ' ')
            .replace(Regex("[ \t\u00a0]+"), " ")
            .trim()
        if (s.isEmpty()) return@forEach
        if (junkLine.containsMatchIn(s)) return@forEach
        if (s.length < 2 && !quoteOnly.containsMatchIn(s)) return@forEach
        sb.append(s).append('\n')
    }
    return sb.toString().trim()
}

/** 从页面里挑出正文：优先 content 类容器，否则用最长的一段纯文字 */
internal fun extractArticle(html: String): String {
    val candidates = mutableListOf<String>()
    val containerRe = Regex(
        """(?is)<div[^>]*(?:id|class)\s*=\s*["'][^"']*(content|chapter[_\-]?detail|read[_\-]?content|txt|article|showtxt|text)[^"']*["'][^>]*>(.*?)</div>"""
    )
    containerRe.findAll(html).forEach { m ->
        val body = m.groupValues[2]
        val txt = cleanText(stripTags(body))
        if (txt.length > 200) candidates.add(txt)
    }
    if (candidates.isNotEmpty()) return candidates.maxByOrNull { it.length }!!

    val body = html.substringAfter("<body", html).substringAfter('>', html)
    val txt = cleanText(stripTags(body))
    return if (txt.length > 100) txt else ""
}

/** 有些站把正文 base64 塞进 document.writeln(任意函数('...'))，直接解出来比渲染快得多 */
internal fun decodeWritelnText(html: String): String {
    val re = Regex("""writeln\(\s*[\w.$]+\(\s*'([A-Za-z0-9+/=]{20,})'\s*\)\s*\)""")
    val sb = StringBuilder()
    var n = 0
    re.findAll(html).forEach { m ->
        runCatching {
            val raw = android.util.Base64.decode(m.groupValues[1], android.util.Base64.DEFAULT)
            sb.append(String(raw, Charsets.UTF_8))
            n++
        }
    }
    if (n == 0) return ""
    return cleanText(stripTags(sb.toString()))
}

internal fun decodeEntities(s: String): String = stripTags(s).trim()

/** 章节标题去掉站点后缀 */
internal fun cleanChapterTitle(raw: String, bookTitle: String = ""): String {
    var t = decodeEntities(raw)
    t = t.substringBefore("_").substringBefore(" - ").trim()
    if (bookTitle.isNotBlank()) t = t.replace(bookTitle, "").trim()
    t = t.removePrefix("-").removePrefix("｜").trim()
    return t.ifBlank { "正文" }
}

// ———————————————————— 书源 1：千千小说 ————————————————

/**
 * 千千小说（qqxs.cc）
 * 搜索：POST /search.html  s=关键字
 * 目录：/info/{id}.html，翻页 /info/{id}/{page}.html
 * 正文：/chapter/{book}/{chap}.html，容器 div.content
 */
object QianQianSource : NovelSource {
    override val id = "qqxs"
    override val name = "千千小说"
    override val hosts = listOf("qqxs.cc")

    private const val BASE = "https://www.qqxs.cc"

    override suspend fun search(keyword: String): List<BookHit> = withContext(Dispatchers.IO) {
        val html = httpPost(
            "$BASE/search.html",
            listOf("s" to keyword),
            "UTF-8",
            referer = "$BASE/"
        )
        val out = LinkedHashMap<String, BookHit>()
        val re = Regex("""href="(/info/\d+\.html)"[^>]*>\s*([^<]{1,60}?)\s*<""")
        re.findAll(html).forEach { m ->
            val url = BASE + m.groupValues[1]
            val title = decodeEntities(m.groupValues[2])
            if (title.isBlank() || title.length > 40) return@forEach
            if (out.containsKey(url)) return@forEach
            out[url] = BookHit(id, name, title, "", "", url)
        }
        // 搜作者信息：结果块里通常跟着「作者：xxx」
        val authorRe = Regex("""/info/(\d+)\.html"[^>]*>[^<]*<[^>]*>.*?作者[:：]\s*([^<\s]{1,16})""", RegexOption.DOT_MATCHES_ALL)
        authorRe.findAll(html).forEach { m ->
            val key = BASE + "/info/" + m.groupValues[1] + ".html"
            out[key]?.let { hit ->
                if (hit.author.isBlank()) out[key] = hit.copy(author = decodeEntities(m.groupValues[2]))
            }
        }
        out.values.toList()
    }

    override suspend fun catalog(hit: BookHit): SourceBook = withContext(Dispatchers.IO) {
        val idMatch = Regex("""/info/(\d+)""").find(hit.url) ?: error("链接格式不对：${hit.url}")
        val bookId = idMatch.groupValues[1]
        val first = httpGet(hit.url, referer = BASE)

        val docTitle = Regex("""(?is)<title>(.*?)</title>""").find(first)?.groupValues?.get(1)?.trim()
            .orEmpty()
            .substringBefore("最新章节")
            .trim()
        val author = Regex("""作者[:：]\s*</?[^>]*>?\s*([^<\s]{1,16})""")
            .find(first)?.groupValues?.get(1)?.trim().orEmpty()
        val intro = Regex(
            """(?is)<div[^>]*class="[^"]*(?:intro|desc|book_intro)[^"]*"[^>]*>(.*?)</div>"""
        ).find(first)?.groupValues?.get(1)?.let { cleanText(stripTags(it)).take(300) }.orEmpty()

        val chapters = LinkedHashMap<String, SourceChapter>()
        var page = 1
        while (page <= 80) {
            val html = try {
                httpGet("$BASE/info/$bookId/$page.html", referer = hit.url)
            } catch (e: Exception) {
                break
            }
            val before = chapters.size
            Regex("""href="(/chapter/\d+/\d+\.html)"[^>]*>\s*([^<]{0,60}?)\s*<""").findAll(html).forEach { m ->
                val url = BASE + m.groupValues[1]
                val title = decodeEntities(m.groupValues[2])
                if (title.contains("开始阅读")) return@forEach
                if (title.isBlank()) return@forEach
                if (!chapters.containsKey(url)) chapters[url] = SourceChapter(title, url)
            }
            if (chapters.size == before) break
            page++
            delay(120)
        }
        if (chapters.isEmpty()) {
            // 退一步：直接从详情页抽
            Regex("""href="(/chapter/\d+/\d+\.html)"[^>]*>\s*([^<]{0,60}?)\s*<""").findAll(first).forEach { m ->
                val url = BASE + m.groupValues[1]
                val title = decodeEntities(m.groupValues[2])
                if (title.isNotBlank() && !title.contains("开始阅读")) chapters[url] = SourceChapter(title, url)
            }
        }
        val title = hit.title.ifBlank { docTitle }
        SourceBook(id, name, title, hit.author.ifBlank { author }, intro, hit.url, chapters.values.toList())
    }

    override suspend fun chapter(url: String): String = withContext(Dispatchers.IO) {
        val html = httpGet(url, referer = BASE)
        val decoded = decodeWritelnText(html)
        if (decoded.length > 150) decoded else extractArticle(html)
    }
}

// ———————————————————— 书源 2：同人小说网 ————————————————

/**
 * 全本同人小说网（qbtr.cc），帝国CMS，GBK 编码
 * 搜索：POST /e/search/index.php  keyboard=关键字
 * 目录：/tongren/{id}.html，章节 /tongren/{id}/{n}.html
 */
object TongRenSource : NovelSource {
    override val id = "qbtr"
    override val name = "同人小说网"
    override val hosts = listOf("qbtr.cc")

    private const val BASE = "http://www.qbtr.cc"

    override suspend fun search(keyword: String): List<BookHit> = withContext(Dispatchers.IO) {
        val html = httpPost(
            "$BASE/e/search/index.php",
            listOf(
                "keyboard" to keyword,
                "show" to "title",
                "classid" to "0",
                "tempid" to "1"
            ),
            "GBK",
            referer = "$BASE/"
        )
        val out = LinkedHashMap<String, BookHit>()
        Regex("""href="(/tongren/\d+\.html)"[^>]*>\s*([^<]{1,60}?)\s*<""").findAll(html).forEach { m ->
            val url = BASE + m.groupValues[1]
            val title = decodeEntities(m.groupValues[2])
            if (title.isNotBlank()) out.putIfAbsent(url, BookHit(id, name, title, "", "", url))
        }
        out.values.toList()
    }

    override suspend fun catalog(hit: BookHit): SourceBook = withContext(Dispatchers.IO) {
        val html = httpGet(hit.url, referer = BASE)
        val docTitle = Regex("""(?is)<title>(.*?)</title>""").find(html)?.groupValues?.get(1)
            ?.substringBefore("(")?.trim().orEmpty()
        val author = Regex("""作者[:：]\s*(?:</?[^>]+>\s*)*([^<\s]{1,16})""")
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()

        val idMatch = Regex("""/tongren/(\d+)""").find(hit.url)
        val bookId = idMatch?.groupValues?.get(1)

        // 章节列表：/tongren/{id}/{n}.html
        val chapters = LinkedHashMap<String, SourceChapter>()
        Regex("""href="(/tongren/\d+/\d+\.html)"[^>]*>\s*([^<]{0,40}?)\s*<""").findAll(html).forEach { m ->
            val url = BASE + m.groupValues[1]
            val title = decodeEntities(m.groupValues[2])
            if (title.isNotBlank() && !title.contains("在线阅读")) {
                chapters.putIfAbsent(url, SourceChapter(title, url))
            }
        }
        // 详情页只给前几章时，顺着 n 往上补
        if (bookId != null && chapters.size <= 3) {
            var n = 1
            var miss = 0
            while (n <= 5000 && miss < 6) {
                val url = "$BASE/tongren/$bookId/$n.html"
                if (chapters.containsKey(url)) { n++; continue }
                val ok = runCatching {
                    val page = httpGet(url, referer = hit.url)
                    val t = Regex("""(?is)<title>(.*?)</title>""").find(page)?.groupValues?.get(1).orEmpty()
                    if (t.contains("404") || !t.contains("在线阅读")) null
                    else SourceChapter(cleanChapterTitle(t, docTitle), url)
                }.getOrNull()
                if (ok != null) {
                    chapters[url] = ok
                    miss = 0
                } else miss++
                n++
                if (n % 40 == 0) delay(200)
            }
        }
        if (chapters.isEmpty()) error("这个页面没找到章节列表")
        SourceBook(id, name, hit.title.ifBlank { docTitle }, hit.author.ifBlank { author }, "", hit.url, chapters.values.toList())
    }

    override suspend fun chapter(url: String): String = withContext(Dispatchers.IO) {
        val html = httpGet(url, referer = BASE)
        val decoded = decodeWritelnText(html)
        if (decoded.length > 150) decoded else extractArticle(html)
    }
}

// ———————————————————— 书源 3：任意地址 ————————————————

/** 粘贴任意小说站目录页，用通用规则解析。 */
object GenericSource : NovelSource {
    override val id = "generic"
    override val name = "通用解析"
    override val hosts = emptyList<String>()

    override suspend fun search(keyword: String): List<BookHit> = emptyList()

    override suspend fun catalog(hit: BookHit): SourceBook = withContext(Dispatchers.IO) {
        val html = httpGet(hit.url)
        val docTitle = Regex("""(?is)<title>(.*?)</title>""").find(html)?.groupValues?.get(1)
            ?.substringBefore("_")?.substringBefore("-")?.trim().orEmpty()
        val author = Regex("""作者[:：]\s*(?:</?[^>]+>\s*)*([^<\s]{1,16})""")
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()

        val host = runCatching { java.net.URI(hit.url).let { "${it.scheme}://${it.host}" } }.getOrDefault("")
        val seen = LinkedHashMap<String, SourceChapter>()

        // 章节链接：文本像章节名，或路径是纯数字页
        val linkRe = Regex("""<a[^>]+href=["']([^"'#]+)["'][^>]*>\s*([^<]{1,60}?)\s*</a>""")
        linkRe.findAll(html).forEach { m ->
            var url = m.groupValues[1]
            val text = decodeEntities(m.groupValues[2])
            if (text.length < 2 || text.length > 50) return@forEach
            val looksChapter = Regex("""第\s*[0-9一二三四五六七八九十百千万零两]+\s*[章节回卷集]""").containsMatchIn(text) ||
                Regex("""^\d{1,5}$""").containsMatchIn(text)
            val looksPage = Regex("""/\d{3,}\.html?$""").containsMatchIn(url)
            if (!looksChapter && !looksPage) return@forEach
            if (url.startsWith("javascript") || url.startsWith("mailto")) return@forEach
            if (!url.startsWith("http")) url = if (url.startsWith("/")) host + url else host + "/" + url
            seen.putIfAbsent(url, SourceChapter(text, url))
        }
        if (seen.size < 3) error("没从这个页面识别到章节列表，换一个目录页地址试试")
        SourceBook(id, name, hit.title.ifBlank { docTitle }, hit.author.ifBlank { author }, "", hit.url, seen.values.toList())
    }

    override suspend fun chapter(url: String): String = withContext(Dispatchers.IO) {
        extractArticle(httpGet(url))
    }
}

// ———————————————————— 对外入口 ————————————————

object Novels {

    val sources: List<NovelSource> = listOf(QianQianSource, TongRenSource)

    fun sourceOf(id: String): NovelSource = sources.firstOrNull { it.id == id } ?: GenericSource

    fun sourceForUrl(url: String): NovelSource {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return sources.firstOrNull { s -> s.hosts.any { host.contains(it) } } ?: GenericSource
    }

    data class SearchOutcome(
        val hits: List<BookHit>,
        val errors: List<String>
    )

    /** 全部书源并发搜，谁先回谁先显示；某个源挂了只记一条错误 */
    suspend fun searchAll(keyword: String): SearchOutcome = coroutineScope {
        val results = sources.map { s ->
            async {
                runCatching { s.search(keyword).map { it.copy(sourceId = s.id, sourceName = s.name) } }
                    .fold(
                        onSuccess = { s.name to it },
                        onFailure = { s.name to null }
                    )
            }
        }.awaitAll()

        val hits = results.flatMap { it.second.orEmpty() }
            .distinctBy { it.sourceId + "|" + it.url }
        val errors = results.filter { it.second == null }.map { "${it.first} 搜索失败" }
        if (hits.isEmpty()) {
            throw IllegalStateException(
                if (errors.isEmpty()) "没搜到《$keyword》，换个书名或换个网站试试"
                else "全部书源都失败了（${errors.joinToString("、")}），检查一下网络"
            )
        }
        SearchOutcome(hits, errors)
    }

    suspend fun catalog(url: String): SourceBook = withContext(Dispatchers.IO) {
        val src = sourceForUrl(url)
        src.catalog(
            BookHit(src.id, src.name, "", "", "", url)
        )
    }

    /** 正文：先走普通请求，拿到的像广告壳就换成 WebView 真渲染一遍 */
    suspend fun chapter(url: String, ctx: android.content.Context? = null): String {
        val src = sourceForUrl(url)
        val plain = runCatching { withContext(Dispatchers.IO) { src.chapter(url) } }.getOrDefault("")
        if (!looksLikeJunk(plain) || ctx == null) return plain
        val rendered = runCatching {
            WebFetcher.text(ctx, url, referer = null)
        }.getOrDefault("")
        val clean = cleanText(rendered)
        return if (clean.length > plain.length) clean else plain
    }

    private fun looksLikeJunk(t: String): Boolean {
        if (t.length < 300) return true
        if (t.contains("请勿开启浏览器阅读模式")) return true
        if (t.contains("目录") && t.contains("存书签") && t.contains("一秒记住")) return true
        return false
    }
}

internal fun encodeFor(s: String, charset: String): String =
    URLEncoder.encode(s, charset)
