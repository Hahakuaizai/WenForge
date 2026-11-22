package com.novelforge.app.data

import android.content.Context
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 导出模块：把一本书导成 TXT / Markdown / EPUB，写到缓存目录供系统分享。
 *  生成作品和爬取导入的书共用这一套。 */
object Exporter {

    enum class Format(val label: String, val ext: String) {
        TXT("TXT", "txt"),
        MARKDOWN("Markdown", "md"),
        EPUB("EPUB", "epub")
    }

    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "novel" }

    fun buildTxt(title: String, metaLines: List<String>, chapters: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append(title).append("\n")
        metaLines.forEach { sb.append(it).append("\n") }
        sb.append("\n")
        chapters.forEach { (chapterTitle, content) ->
            sb.append(chapterTitle).append("\n\n")
            sb.append(content).append("\n\n")
        }
        return sb.toString()
    }

    fun buildMarkdown(title: String, metaLines: List<String>, chapters: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")
        metaLines.forEach { sb.append(it).append("\n\n") }
        chapters.forEach { (chapterTitle, content) ->
            sb.append("## ").append(chapterTitle).append("\n\n")
            content.split("\n").forEach { line ->
                if (line.isNotBlank()) sb.append(line).append("\n\n")
            }
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** 生成标准 EPUB（mimetype 不压缩置首 + OPF + NCX + 每章一个 XHTML）。 */
    fun buildEpub(
        context: Context,
        title: String,
        author: String,
        chapters: List<Pair<String, String>>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, sanitize(title) + ".epub")
        val bookId = "novelforge-${file.nameWithoutExtension.hashCode().toUInt()}"
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // mimetype 必须第一个且不压缩
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                this.crc = crc.value
            }
            zip.putNextEntry(mimeEntry); zip.write(mimeBytes); zip.closeEntry()

            fun addText(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            addText(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
            )

            val manifest = chapters.indices.joinToString("\n") { i ->
                """<item id="ch${i + 1}" href="ch${i + 1}.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spine = chapters.indices.joinToString("\n") { i -> """<itemref idref="ch${i + 1}"/>""" }
            addText(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:identifier id="bookid">$bookId</dc:identifier>
    <dc:title>${xmlEscape(title)}</dc:title>
    <dc:creator>${xmlEscape(author.ifBlank { "未知" })}</dc:creator>
    <dc:language>zh-CN</dc:language>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifest
  </manifest>
  <spine toc="ncx">
$spine
  </spine>
</package>"""
            )

            val navPoints = chapters.mapIndexed { i, (chapterTitle, _) ->
                """<navPoint id="np${i + 1}" playOrder="${i + 1}"><navLabel><text>${xmlEscape(chapterTitle)}</text></navLabel><content src="ch${i + 1}.xhtml"/></navPoint>"""
            }.joinToString("\n")
            addText(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="$bookId"/></head>
  <docTitle><text>${xmlEscape(title)}</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
            )

            chapters.forEachIndexed { i, (chapterTitle, content) ->
                val paragraphs = content.split("\n")
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "<p>${xmlEscape(it.trim())}</p>" }
                addText(
                    "OEBPS/ch${i + 1}.xhtml",
                    """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${xmlEscape(chapterTitle)}</title></head>
<body><h2>${xmlEscape(chapterTitle)}</h2>
$paragraphs
</body></html>"""
                )
            }
        }
        return file
    }

    private fun writeTextFile(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, fileName).apply { writeText(content, Charsets.UTF_8) }
    }

    /** 导出生成的作品（指定格式） */
    fun writeProject(context: Context, project: NovelProject, format: Format): File {
        val chapters = project.chapters.filter { it.done }
            .map { "第${it.index}章 ${it.title}" to it.content }
        val meta = listOf("${project.genre} · ${project.style}", "共 ${chapters.size} 章，约 ${project.totalWords} 字")
        return when (format) {
            Format.TXT -> writeTextFile(context, sanitize(project.title) + ".txt", buildTxt(project.title, meta, chapters))
            Format.MARKDOWN -> writeTextFile(context, sanitize(project.title) + ".md", buildMarkdown(project.title, meta, chapters))
            Format.EPUB -> buildEpub(context, project.title, "小说工坊生成", chapters)
        }
    }

    /** 导出爬取导入的书（指定格式） */
    fun writeImported(context: Context, book: ImportedBook, format: Format): File {
        val chapters = book.chapters.map { it.title to it.content }
        val meta = listOfNotNull(
            book.author.takeIf { it.isNotBlank() }?.let { "作者：$it" },
            "来源：${book.sourceUrl}",
            "共 ${chapters.size} 章，约 ${book.totalWords} 字"
        )
        return when (format) {
            Format.TXT -> writeTextFile(context, sanitize(book.title) + ".txt", buildTxt(book.title, meta, chapters))
            Format.MARKDOWN -> writeTextFile(context, sanitize(book.title) + ".md", buildMarkdown(book.title, meta, chapters))
            Format.EPUB -> buildEpub(context, book.title, book.author, chapters)
        }
    }
}
