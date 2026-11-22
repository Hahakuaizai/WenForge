package com.novelforge.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * 多格式小说解析。
 *
 * 支持：
 *  - txt / md / text          纯文本，自动猜编码
 *  - epub                     zip + OPF 清单，按 spine 顺序取正文
 *  - html / htm / xhtml       按 <h1>/<h2>/第X章 切分
 *  - docx                     word/document.xml 里抽段落
 *  - zip                      zip 里若只有一本书，解出来再按它的格式解析
 *
 * 每个入口都返回 Parsed（书名 + 作者 + 章节列表）。
 */
object BookParser {

    data class Parsed(
        val title: String,
        val author: String,
        val chapters: MutableList<ImportedChapter>
    )

    /** 能被导入的扩展名 */
    val supportedExtensions = setOf(
        "txt", "md", "markdown", "text", "epub", "html", "htm", "xhtml", "docx", "zip"
    )

    fun isSupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in supportedExtensions

    /** 主入口：按扩展名分派 */
    fun parse(bytes: ByteArray, fileName: String): Parsed {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val baseName = fileName.substringBeforeLast('.')
        return when (ext) {
            "epub" -> parseEpub(bytes, baseName)
            "docx" -> parseDocx(bytes, baseName)
            "html", "htm", "xhtml" -> parseHtml(decodeText(bytes), baseName)
            "zip" -> parseZip(bytes, baseName)
            else -> convert(LocalImporter.parse(decodeText(bytes), baseName))
        }
    }

    fun parse(file: File): Parsed =
        parse(file.readBytes(), file.name)

    // ── 编码 ────────────────────────────────────────────────────

    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8
        val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
        if (gbk != null && gbk.count { it == '\uFFFD' } < (utf8?.count { it == '\uFFFD' } ?: Int.MAX_VALUE)) {
            return gbk
        }
        return utf8 ?: String(bytes, Charsets.UTF_8)
    }

    // ── EPUB ────────────────────────────────────────────────────

    private fun parseEpub(bytes: ByteArray, baseName: String): Parsed {
        val entries = unzip(bytes) ?: return fallback(baseName, bytes)
        val opfPath = entries.entries
            .firstOrNull { it.key.endsWith(".opf", ignoreCase = true) }?.key
            ?: return fallback(baseName, bytes)

        val opf = entries[opfPath]?.let { decodeText(it) }.orEmpty()
        val opfDir = opfPath.substringBeforeLast('/', "")

        val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            .find(opf)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
            ?.takeIf { it.isNotBlank() } ?: baseName
        val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
            .find(opf)?.groupValues?.get(1)?.let { stripTags(it) }?.trim().orEmpty()

        // manifest: id -> href
        val manifest = mutableMapOf<String, String>()
        Regex("""<item\s+[^>]*>""").findAll(opf).forEach { m ->
            val tag = m.value
            val id = attr(tag, "id")
            val href = attr(tag, "href")
            if (id != null && href != null) manifest[id] = href
        }
        // spine 顺序
        val order = mutableListOf<String>()
        Regex("""<itemref\s+[^>]*>""").findAll(opf).forEach { m ->
            attr(m.value, "idref")?.let { ref -> manifest[ref]?.let { order.add(it) } }
        }

        val chapters = mutableListOf<ImportedChapter>()
        order.forEach { href ->
            val full = joinPath(opfDir, href.substringBefore('#'))
            val raw = entries[full] ?: entries.entries
                .firstOrNull { it.key.endsWith(href.substringBefore('#')) }?.value
                ?: return@forEach
            val html = decodeText(raw)
            val heading = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim().orEmpty()
            val body = htmlToText(html)
            if (body.isBlank()) return@forEach
            chapters.add(
                ImportedChapter(
                    index = chapters.size + 1,
                    title = heading.ifBlank { "第${chapters.size + 1}节" },
                    content = body
                )
            )
        }
        if (chapters.isEmpty()) return fallback(baseName, bytes)
        return Parsed(title, author, chapters)
    }

    // ── DOCX ────────────────────────────────────────────────────

    private fun parseDocx(bytes: ByteArray, baseName: String): Parsed {
        val entries = unzip(bytes) ?: return fallback(baseName, bytes)
        val xml = entries["word/document.xml"]?.let { decodeText(it) } ?: return fallback(baseName, bytes)
        val text = xml
            .replace(Regex("""</w:p>"""), "\n")
            .replace(Regex("""<w:br[^>]*/>"""), "\n")
            .replace(Regex("<[^>]+>"), "")
            .let { unescapeXml(it) }
        return convert(LocalImporter.parse(text, baseName))
    }

    // ── HTML ────────────────────────────────────────────────────

    private fun parseHtml(html: String, baseName: String): Parsed {
        val title = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
            ?.takeIf { it.isNotBlank() } ?: baseName
        val text = htmlToText(html)
        return convert(LocalImporter.parse(text, title))
    }

    // ── ZIP（里面是一本书） ─────────────────────────────────────

    private fun parseZip(bytes: ByteArray, baseName: String): Parsed {
        val entries = unzip(bytes) ?: return fallback(baseName, bytes)
        // 里面若还有 epub/docx/txt，挑最大的那个
        val inner = entries.entries
            .filter { isSupported(it.key) && !it.key.endsWith("/") }
            .maxByOrNull { it.value.size }
            ?: return fallback(baseName, bytes)
        val innerName = inner.key.substringAfterLast('/')
        return parse(inner.value, innerName)
    }

    // ── 工具 ────────────────────────────────────────────────────

    private fun convert(p: LocalImporter.Parsed): Parsed =
        Parsed(p.title, p.author, p.chapters)

    private fun fallback(baseName: String, bytes: ByteArray): Parsed =
        convert(LocalImporter.parse(decodeText(bytes), baseName))

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray>? = runCatching {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zis.copyTo(buf)
                    out[entry.name] = buf.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (out.isEmpty()) null else out
    }.getOrNull()

    private fun joinPath(dir: String, name: String): String =
        if (dir.isBlank()) name else "$dir/$name"

    private fun attr(tag: String, name: String): String? =
        Regex("""$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

    private fun stripTags(s: String): String =
        unescapeXml(s.replace(Regex("<[^>]+>"), ""))

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&nbsp;", " ").replace("&#160;", " ")
        .replace("&amp;", "&")

    /** HTML → 纯文本，保留段落换行 */
    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("""<script.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("""<style.*?</style>""", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""</(p|div|h[1-6]|li|tr)>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = unescapeXml(s)
        return s.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
