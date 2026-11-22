package com.novelforge.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 本地文件导入：txt / md / 无格式纯文本。
 *
 * 干三件事：
 * 1. 猜编码（UTF-8 / GBK / UTF-16），中文 txt 一多半是 GBK
 * 2. 按常见章节标题切分
 * 3. 清掉 md 记号、多余空行、首尾空白
 */
object LocalImporter {

    /** 章节标题的几种写法，按优先级试 */
    private val chapterPatterns = listOf(
        // 第一章 / 第12章 / 第三回 / 第 7 节  后面可能跟标题
        Regex(
            """^[\s　]*第\s*[0-9０-９一二三四五六七八九十百千零两]{1,6}\s*[章回节話话卷][^\n]{0,40}$""",
            setOf(RegexOption.MULTILINE)
        ),
        // Chapter 12 / CHAPTER XII
        Regex(
            """^[\s　]*chapter\s*[0-9ivxlcdm]{1,6}[^\n]{0,50}$""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        ),
        // markdown 标题
        Regex("""^[\s　]*#{1,3}\s+\S[^\n]{0,50}$""", setOf(RegexOption.MULTILINE))
    )

    data class Parsed(val title: String, val author: String, val chapters: MutableList<ImportedChapter>)

    /** 从流里读出文本，自动猜编码 */
    fun readText(input: InputStream): String {
        val bytes = input.use { ins ->
            val out = ByteArrayOutputStream()
            ins.copyTo(out)
            out.toByteArray()
        }
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): String {
        // BOM 优先
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // 先按 UTF-8 严格解，失败就退 GBK
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8 != null && utf8.none { it == '\uFFFD' }) return utf8
        val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
        if (gbk != null && gbk.count { it == '\uFFFD' } < utf8.orEmpty().count { it == '\uFFFD' }) return gbk
        return utf8 ?: String(bytes, Charsets.UTF_8)
    }

    /**
     * 解析整本书。fileName 用来兜底书名。
     */
    fun parse(raw: String, fileName: String): Parsed {
        val text = normalize(raw)
        val lines = text.split("\n")

        // 找章节标题行
        val hits = mutableListOf<Int>()
        lines.forEachIndexed { i, line ->
            val t = line.trim()
            if (t.isEmpty() || t.length > 60) return@forEachIndexed
            if (chapterPatterns.any { it.matches(t) }) hits.add(i)
        }

        // 一个标题都没找到：整本当一章
        if (hits.isEmpty()) {
            val body = text.trim()
            val chapters = mutableListOf<ImportedChapter>()
            if (body.isNotEmpty()) {
                chapters.add(ImportedChapter(index = 1, title = "正文", content = body))
            }
            return Parsed(guessTitle(fileName, text), "", chapters)
        }

        // 第一章之前的内容（书名、作者、简介）
        val head = lines.subList(0, hits.first()).joinToString("\n").trim()
        val title = guessTitle(fileName, head)
        val author = guessAuthor(head)

        val chapters = mutableListOf<ImportedChapter>()
        hits.forEachIndexed { idx, start ->
            val end = if (idx + 1 < hits.size) hits[idx + 1] else lines.size
            val titleLine = lines[start].trim().trim('#', ' ', '　')
            val body = lines.subList(start + 1, end)
                .joinToString("\n")
                .trim()
                .replace(Regex("\n{3,}"), "\n\n")
            if (body.isBlank() && titleLine.isBlank()) return@forEachIndexed
            chapters.add(
                ImportedChapter(
                    index = chapters.size + 1,
                    title = titleLine.ifBlank { "第${chapters.size + 1}节" },
                    content = body
                )
            )
        }
        if (chapters.isEmpty()) {
            chapters.add(ImportedChapter(index = 1, title = "正文", content = text.trim()))
        }
        return Parsed(title, author, chapters)
    }

    /** 去掉 md 记号、统一换行、压掉多余空行 */
    private fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\uFEFF", "")
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")      // 图片
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")   // 链接保留文字
        .replace(Regex("""^[ \t]*>[ \t]?""", RegexOption.MULTILINE), "")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""^[ \t]*[-*+][ \t]+""", RegexOption.MULTILINE), "")
        .replace(Regex("""`{1,3}"""), "")
        .replace(Regex("""\n{4,}"""), "\n\n\n")
        .trim()

    /**
     * 猜书名：优先用文件名（最可靠），文件名没有意义时才看正文开头。
     * 正文第一行经常是第一章的正文，绝不能当书名。
     */
    private fun guessTitle(fileName: String, head: String): String {
        val fromName = fileName
            .substringBeforeLast('.')
            .replace(Regex("""[\[(（].*?[\])）]"""), "")
            .replace(Regex("""(?i)(txt|md|epub|docx|html?|xhtml|zip|全本|完结|精校|下载|最新章节)"""), "")
            .replace(Regex("""[-_—]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        if (fromName.length in 2..40) return fromName

        // 退而求其次：看开头有没有《书名》这种标记
        val quoted = Regex("""《([^》]{1,40})》""").find(head)?.groupValues?.get(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted

        return fromName.ifBlank { "" }
    }

    private fun guessAuthor(head: String): String {
        val m = Regex("""(?:作者|著者|作\s*者)\s*[:：]\s*(\S{1,20})""").find(head)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }
}
