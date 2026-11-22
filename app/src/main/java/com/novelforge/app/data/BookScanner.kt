package com.novelforge.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** 手机里扫到的候选文件 */
data class FoundBook(
    val path: String,
    /** MediaStore 的 uri；有它就不用管分区存储 */
    val uri: String = "",
    val name: String,
    val sizeBytes: Long,
    val ext: String,
    /** 看起来像小说（有章节结构 / 大段中文正文） */
    val looksLikeNovel: Boolean = true,
    /** 判断依据，给用户看 */
    val hint: String = ""
) {
    /** 列表用的唯一键：优先路径，没有路径就用 uri，都没有就退回文件名+大小 */
    val key: String
        get() = path.ifBlank { uri }.ifBlank { "$name@$sizeBytes" }

    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)
            sizeBytes >= 1024 -> String.format("%.0f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

/**
 * 扫手机里的书籍文件。
 *
 * 两条路一起走：
 *  1. MediaStore 查（Android 10+ 不需要全盘权限，最稳）
 *  2. 直接遍历常见目录（有 MANAGE_EXTERNAL_STORAGE 时能补到 MediaStore 漏掉的）
 *
 * 扫到的文件会顺手判断像不像小说：有章节标记、正文以中文为主、行数够多 → 小说；
 * 否则归到「其他文件」里折叠，用户想加也能加。
 */
object BookScanner {

    /** 上一次扫描过程中的错误，给界面显示用 */
    val errs: MutableList<String> = mutableListOf()

    /**
     * 读文件内容。有 uri 就走 ContentResolver（MediaStore 的文件没有真实路径，
     * 分区存储下也不给 File 直接读），否则退回按路径读。
     */
    fun readBytes(ctx: Context, book: FoundBook): ByteArray {
        if (book.uri.isNotBlank()) {
            return ctx.contentResolver.openInputStream(Uri.parse(book.uri))?.use { it.readBytes() }
                ?: throw IllegalStateException("打不开文件：${book.name}")
        }
        if (book.path.isNotBlank()) return File(book.path).readBytes()
        throw IllegalStateException("没有可用地址：${book.name}")
    }

    private val dirHints = listOf(
        "Download", "Downloads", "Documents", "Books", "Book", "Novels", "Novel",
        "小说", "电子书", "书籍", "书", "小说下载", "本地书籍", "BaiduNetdisk", "Quark",
        "tencent/MicroMsg/Download", "tencent/QQfile_recv",
        "Android/data/com.tencent.mm/MicroMsg/Download"
    )

    private val skipDirs = setOf(
        "Android/obb", "Android/data", ".thumbnails", "cache", "Cache",
        "LOST.DIR", ".trash", "trash", "Android/media"
    )

    // —————————————————— 对外 ————————————————

    /**
     * 扫描。返回全部符合格式的文件，用 looksLikeNovel 区分。
     * @param deep true = 遍历整个外部存储（慢），false = 只扫常见目录
     */
    fun scan(
        ctx: Context,
        deep: Boolean = false,
        maxFiles: Int = 2000
    ): List<FoundBook> {
        errs.clear()
        val out = LinkedHashMap<String, FoundBook>()

        // 1) MediaStore
        runCatching {
            queryMediaStore(ctx, maxFiles).forEach { fb ->
                out.putIfAbsent(fb.path.ifBlank { fb.uri }, fb)
            }
        }.onFailure { errs.add("媒体库：" + (it.message ?: it.javaClass.simpleName)) }

        // 2) 目录遍历（能读到才补）
        val roots = if (deep) defaultRoots(ctx) else quickRoots(ctx)
        if (roots.isEmpty()) errs.add("没有可扫描的目录")
        roots.forEach { root ->
            if (out.size >= maxFiles) return@forEach
            runCatching { walk(root, 0, if (deep) 8 else 5, maxFiles, out) }
                .onFailure { errs.add(root.name + "：" + (it.message ?: it.javaClass.simpleName)) }
        }

        return out.values.sortedWith(
            compareByDescending<FoundBook> { it.looksLikeNovel }
                .thenByDescending { it.sizeBytes }
        )
    }

    // —————————————————— MediaStore ————————————————

    private fun queryMediaStore(ctx: Context, maxFiles: Int): List<FoundBook> {
        val out = mutableListOf<FoundBook>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val exts = BookParser.supportedExtensions.toList()
        // 用 selection 粗筛，避免把图片视频全查出来
        val sel = exts.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" }
        val args = exts.map { "%.$it" }.toTypedArray()

        // DATA 列在 Android 10+ 已移除，查它会抛异常，这里只查通用列
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )

        runCatching {
            ctx.contentResolver.query(
                collection, projection, sel, args,
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (c.moveToNext() && out.size < maxFiles) {
                    val name = c.getString(nameCol) ?: continue
                    val size = c.getLong(sizeCol)
                    if (size < 1024) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in exts) continue
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val path = ""
                    val verdict = judgeNovel(ctx, uri, name, size)
                    out.add(
                        FoundBook(
                            path = path,
                            uri = uri,
                            name = name,
                            sizeBytes = size,
                            ext = ext,
                            looksLikeNovel = verdict.first,
                            hint = verdict.second
                        )
                    )
                }
            }
        }
        return out
    }

    // —————————————————— 目录遍历 ————————————————

    private fun walk(
        dir: File,
        level: Int,
        maxDepth: Int,
        maxFiles: Int,
        out: MutableMap<String, FoundBook>
    ) {
        if (out.size >= maxFiles || level > maxDepth) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (out.size >= maxFiles) return
            if (f.isDirectory) {
                if (f.name.startsWith(".")) continue
                if (skipDirs.any { f.absolutePath.contains(it, ignoreCase = true) }) continue
                walk(f, level + 1, maxDepth, maxFiles, out)
            } else {
                if (!BookParser.isSupported(f.name)) continue
                if (f.length() < 1024) continue
                val key = f.absolutePath
                if (out.containsKey(key)) continue
                val verdict = judgeNovelFile(f)
                out[key] = FoundBook(
                    path = key,
                    name = f.name,
                    sizeBytes = f.length(),
                    ext = f.name.substringAfterLast('.', "").lowercase(),
                    looksLikeNovel = verdict.first,
                    hint = verdict.second
                )
            }
        }
    }

    // —————————————————— 像不像小说 ————————————————

    /**
     * 判断是不是小说。docx / epub 也要读正文，不能因为「是文档格式」就直接算书 ——
     * 期末复习资料、题库、劳动实践报告全都能存成 docx。
     */
    private fun judgeNovelFile(f: File): Pair<Boolean, String> {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val head = when (ext) {
            "docx" -> runCatching { DocText.docx(f) }.getOrDefault("")
            "epub" -> runCatching { DocText.epub(f) }.getOrDefault("")
            else -> runCatching {
                f.inputStream().use { ins ->
                    val buf = ByteArray(minOf(64 * 1024, f.length().toInt().coerceAtLeast(1024)))
                    val n = ins.read(buf)
                    decodeText(buf, n)
                }
            }.getOrDefault("")
        }
        if (head.isBlank() && (ext == "docx" || ext == "epub")) {
            return false to "读不出正文"
        }
        val v = NovelDetector.judge(head, f.name)
        return v.isNovel to v.reason
    }

    private fun judgeNovel(ctx: Context, uri: String, name: String, size: Long): Pair<Boolean, String> {
        val ext = name.substringAfterLast('.', "").lowercase()
        val head = when (ext) {
            "docx" -> runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { DocText.docx(it) }
            }.getOrNull().orEmpty()
            "epub" -> runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { DocText.epub(it) }
            }.getOrNull().orEmpty()
            else -> runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { ins ->
                    val buf = ByteArray(minOf(64 * 1024, size.toInt().coerceAtLeast(1024)))
                    val n = ins.read(buf)
                    decodeText(buf, n)
                }
            }.getOrNull().orEmpty()
        }
        if (head.isBlank() && (ext == "docx" || ext == "epub")) {
            return false to "读不出正文"
        }
        val v = NovelDetector.judge(head, name)
        return v.isNovel to v.reason
    }

    /**
     * 自动识别 UTF-8 / GBK。
     * 注意：按字节截断会切在汉字中间，末尾出现半个字符是正常的，
     * 所以只看开头部分有没有乱码，并把末尾可能残缺的 3 个字节丢掉。
     */
    private fun decodeText(buf: ByteArray, len: Int): String {
        val end = len.coerceAtLeast(0)
        if (end == 0) return ""
        // 先严格解一次，失败就退 1~3 字节再试（可能截在多字节字符中间）
        for (trim in 0..3) {
            val n = end - trim
            if (n <= 0) break
            val bytes = buf.copyOfRange(0, n)
            val strict = runCatching { java.nio.charset.Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
            if (strict != null) return strict
        }
        // UTF-8 解不了，多半是 GBK
        val bytes = buf.copyOfRange(0, end)
        return runCatching {
            String(bytes, java.nio.charset.Charset.forName("GB18030"))
        }.getOrDefault(String(bytes, Charsets.UTF_8))
    }

    // —————————————————— 根目录 ————————————————

    fun defaultRoots(ctx: Context): List<File> {
        val list = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory()?.let { list.add(it) } }
        runCatching {
            ctx.getExternalFilesDirs(null).forEach { f ->
                var p = f?.parentFile
                repeat(4) { p = p?.parentFile }
                p?.takeIf { it.exists() }?.let { list.add(it) }
            }
        }
        return list.distinct()
    }

    fun quickRoots(ctx: Context): List<File> {
        val base = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?: return emptyList()
        val direct = dirHints.map { File(base, it) }.filter { it.exists() }
        // 常见目录不存在时退回扫根目录一层
        return if (direct.isEmpty()) listOf(base) else direct
    }
}
