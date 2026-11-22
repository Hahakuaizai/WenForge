package com.novelforge.app.data

import android.content.Context
import android.os.Environment
import java.io.File

/** 手机里扫到的候选文件 */
data class FoundBook(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val ext: String
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)
            sizeBytes >= 1024 -> String.format("%.0f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

/**
 * 扫手机存储里的小说文件。
 *
 * 只扫常见目录，不遍历整个 /storage（几十万文件会卡死）：
 * Download、Documents、Books、Bookshelf、小说、电子书、WeiXin、QQ 接收目录…
 */
object BookScanner {

    private val dirHints = listOf(
        "Download", "Downloads", "Documents", "Books", "Book", "Novels", "Novel",
        "小说", "电子书", "书籍", "书", "小说下载", "本地书籍",
        "tencent/MicroMsg/Download", "tencent/QQfile_recv",
        "Android/data/com.tencent.mm/MicroMsg/Download"
    )

    /** 排除的目录名，避免进回收站、缓存 */
    private val skipDirs = setOf(
        "Android/obb", "Android/data", ".thumbnails", "cache", "Cache",
        "LOST.DIR", ".trash", "trash"
    )

    /**
     * 扫描。depth 限制递归层数，默认 4 层足够。
     */
    fun scan(
        ctx: Context,
        roots: List<File>? = null,
        maxFiles: Int = 800,
        depth: Int = 4
    ): List<FoundBook> {
        val out = mutableListOf<FoundBook>()
        val seen = mutableSetOf<String>()

        val startDirs = roots ?: defaultRoots(ctx)

        fun walk(dir: File, level: Int) {
            if (out.size >= maxFiles || level > depth) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in children) {
                if (out.size >= maxFiles) return
                if (f.isDirectory) {
                    val rel = f.absolutePath
                    if (skipDirs.any { rel.contains(it, ignoreCase = true) }) continue
                    if (f.name.startsWith(".")) continue
                    walk(f, level + 1)
                } else {
                    if (!BookParser.isSupported(f.name)) continue
                    if (f.length() < 2048) continue          // 太小，多半不是书
                    if (!seen.add(f.absolutePath)) continue
                    out.add(
                        FoundBook(
                            path = f.absolutePath,
                            name = f.name,
                            sizeBytes = f.length(),
                            ext = f.name.substringAfterLast('.', "").lowercase()
                        )
                    )
                }
            }
        }

        startDirs.forEach { d ->
            if (d.exists() && d.isDirectory) walk(d, 0)
        }
        return out.sortedByDescending { it.sizeBytes }
    }

    /** 默认扫描根目录：内部存储 + 各常见目录 */
    fun defaultRoots(ctx: Context): List<File> {
        val list = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null) {
            list.add(ext)
        }
        runCatching {
            ctx.getExternalFilesDirs(null).forEach { f ->
                f?.parentFile?.parentFile?.parentFile?.parentFile?.let { root ->
                    if (root.exists()) list.add(root)
                }
            }
        }
        return list.distinct()
    }

    /** 只扫提示目录，快一些 */
    fun quickRoots(ctx: Context): List<File> {
        val base = Environment.getExternalStorageDirectory() ?: return emptyList()
        return dirHints.map { File(base, it) }.filter { it.exists() }
    }
}
