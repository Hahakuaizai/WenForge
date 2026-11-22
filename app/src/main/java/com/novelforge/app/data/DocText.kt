package com.novelforge.app.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 从 docx / epub 里抽出纯文本，用来判断「是不是小说」。
 *
 * docx 和 epub 本质都是 zip，但压缩方式不同（docx 用 deflate，epub 的 mimetype 是 stored），
 * 所以这里用 ZipInputStream 流式读，只取需要的那几个条目，不整包解压。
 */
object DocText {

    /** 抽出来的文本上限，够判断就行 */
    private const val LIMIT = 120_000

    fun docx(f: File): String = f.inputStream().use { docx(it) }

    fun docx(input: InputStream): String {
        val sb = StringBuilder()
        // word/document.xml 是正文；页眉页脚、脚注也算一点内容
        readEntries(input) { name, bytes ->
            if (name == "word/document.xml" || name.startsWith("word/footnotes")) {
                sb.append(stripXml(bytes))
            }
            sb.length < LIMIT
        }
        return sb.toString()
    }

    fun epub(f: File): String = f.inputStream().use { epub(it) }

    fun epub(input: InputStream): String {
        val sb = StringBuilder()
        readEntries(input) { name, bytes ->
            val lower = name.lowercase()
            val isHtml = lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
            // 跳过导航和目录页，它们会把章节名堆成目录，干扰判断
            val isNav = lower.contains("nav") || lower.contains("toc") || lower.contains("cover")
            if (isHtml && !isNav) {
                sb.append(stripHtml(bytes))
                sb.append('\n')
            }
            sb.length < LIMIT
        }
        return sb.toString()
    }

    /** 遍历 zip，把每个条目的内容交给回调；回调返回 false 就停 */
    private inline fun readEntries(input: InputStream, onEntry: (name: String, bytes: ByteArray) -> Boolean) {
        val zis = ZipInputStream(input.buffered())
        val buf = ByteArray(8 * 1024)
        while (true) {
            val entry = zis.nextEntry ?: break
            val name = entry.name
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = zis.read(buf)
                if (n <= 0) break
                // 单个条目也不无限读，防压缩炸弹
                if (total < 2 * 1024 * 1024) out.write(buf, 0, n)
                total += n
            }
            val keep = onEntry(name, out.toByteArray())
            zis.closeEntry()
            if (!keep) break
        }
        runCatching { zis.close() }
    }

    /** docx 的 document.xml → 纯文本，段落之间换行 */
    private fun stripXml(bytes: ByteArray): String {
        val xml = String(bytes, Charsets.UTF_8)
        val sb = StringBuilder(xml.length / 3)
        var i = 0
        while (i < xml.length) {
            val c = xml[i]
            if (c == '<') {
                // <w:p .../> 或 </w:p> 视为换行
                val close = xml.indexOf('>', i)
                if (close < 0) break
                val tag = xml.substring(i, close + 1)
                if (tag.startsWith("</w:p") || tag.startsWith("<w:p ") || tag == "<w:p>" || tag.startsWith("<w:br")) {
                    sb.append('\n')
                } else if (tag.startsWith("</w:tc") || tag.startsWith("<w:tab")) {
                    sb.append('\t')
                }
                i = close + 1
            } else {
                sb.append(c)
                i++
            }
        }
        return unescape(sb.toString())
    }

    /** epub 的 html → 纯文本，块级标签换行 */
    private fun stripHtml(bytes: ByteArray): String {
        val html = String(bytes, Charsets.UTF_8)
        val sb = StringBuilder(html.length / 3)
        var i = 0
        var inTag = false
        var tagStart = 0
        while (i < html.length) {
            val c = html[i]
            if (!inTag) {
                if (c == '<') {
                    inTag = true
                    tagStart = i
                } else {
                    sb.append(c)
                }
            } else if (c == '>') {
                val tag = html.substring(tagStart, i + 1).lowercase()
                if (tag.startsWith("</p") || tag.startsWith("</div") || tag.startsWith("</h") ||
                    tag == "<br>" || tag == "<br/>" || tag == "<br />" || tag.startsWith("</li")
                ) {
                    sb.append('\n')
                }
                inTag = false
            }
            i++
        }
        // <script>/<style> 内容会被当正文，先粗暴去掉
        var t = sb.toString()
        t = t.replace(Regex("(?is)<script.*?</script>"), " ")
        t = t.replace(Regex("(?is)<style.*?</style>"), " ")
        return unescape(t)
    }

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
