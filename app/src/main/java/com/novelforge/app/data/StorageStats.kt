package com.novelforge.app.data

import android.content.Context
import java.io.File
import java.util.Locale

data class UsageSlice(
    val key: String,
    val label: String,
    val bytes: Long,
    val detail: String = ""
)

/** 明细里的一项：可以是文件、书、章（children 是再下一层，如书→章） */
data class StorageItem(
    val name: String,
    val detail: String = "",
    val bytes: Long,
    val children: List<StorageItem> = emptyList()
)

data class StorageReport(
    val slices: List<UsageSlice>,
    val dataFileBytes: Long,
    val cacheBytes: Long,
    val apkBytes: Long,
    val totalBytes: Long
)

object StorageStats {

    private fun utf8(s: String): Long = s.toByteArray(Charsets.UTF_8).size.toLong()

    fun report(ctx: Context, data: AppData): StorageReport {
        var novelBytes = 0L
        var novelChapters = 0
        data.projects.forEach { p ->
            p.chapters.forEach { c ->
                novelBytes += utf8(c.content)
                novelChapters++
            }
        }

        var downloadBytes = 0L
        var downloadBooks = 0
        var localBytes = 0L
        var localBooks = 0
        data.importedBooks.forEach { b ->
            val size = b.chapters.sumOf { utf8(it.content) }
            if (b.origin == "crawl") {
                downloadBytes += size
                downloadBooks++
            } else {
                localBytes += size
                localBooks++
            }
        }

        val dataFile = File(ctx.filesDir, "novel-forge-data.json").let {
            if (it.exists()) it.length() else 0L
        }
        // 章节正文之外的设置、目录、地址表等等
        val otherBytes = (dataFile - novelBytes - downloadBytes - localBytes).coerceAtLeast(0L)

        val cacheBytes = dirSize(ctx.cacheDir)
        val apkBytes = runCatching {
            File(ctx.applicationInfo.sourceDir).length()
        }.getOrDefault(0L)

        val slices = listOf(
            UsageSlice("novels", "我写的小说", novelBytes,
                "${data.projects.size} 部 · $novelChapters 章"),
            UsageSlice("download", "在线下载的小说", downloadBytes,
                "$downloadBooks 本"),
            UsageSlice("local", "本地导入的小说", localBytes,
                "$localBooks 本"),
            UsageSlice("meta", "设置与目录数据", otherBytes, "章节地址表等"),
            UsageSlice("cache", "缓存", cacheBytes, "可清理"),
            UsageSlice("apk", "程序本体", apkBytes, "App 安装包")
        )
        val total = slices.sumOf { it.bytes }
        return StorageReport(slices, dataFile, cacheBytes, apkBytes, total)
    }

    /** 某一部分点开后看的明细：书的部分列到书，书还能再展开到章 */
    fun sliceItems(ctx: Context, data: AppData, key: String): List<StorageItem> {
        fun chapterItems(
            titles: List<Pair<String, String>>
        ): List<StorageItem> {
            val all = titles.map { (name, content) ->
                StorageItem(name, "", utf8(content))
            }.sortedByDescending { it.bytes }
            return if (all.size > 50) {
                all.take(50) + StorageItem(
                    "其余 ${all.size - 50} 章", "",
                    all.drop(50).sumOf { it.bytes }
                )
            } else all
        }
        return when (key) {
            "novels" -> data.projects.map { p ->
                val chs = chapterItems(p.chapters.map { c ->
                    c.title.ifBlank { "第${c.index}章" } to c.content
                })
                StorageItem("《${p.title}》", "${p.chapters.size} 章", chs.sumOf { it.bytes }, chs)
            }.sortedByDescending { it.bytes }

            "download", "local" -> data.importedBooks
                .filter { (it.origin == "crawl") == (key == "download") }
                .map { b ->
                    val chs = chapterItems(b.chapters.map { c ->
                        c.title.ifBlank { "第${c.index}章" } to c.content
                    })
                    StorageItem("《${b.title}》", "${b.chapters.size} 章", chs.sumOf { it.bytes }, chs)
                }.sortedByDescending { it.bytes }

            "cache" -> {
                val files = mutableListOf<StorageItem>()
                fun walk(dir: File, prefix: String) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isDirectory) walk(f, prefix + f.name + "/")
                        else files.add(StorageItem(prefix + f.name, "", f.length()))
                    }
                }
                runCatching { if (ctx.cacheDir.exists()) walk(ctx.cacheDir, "") }
                val sorted = files.sortedByDescending { it.bytes }
                if (sorted.size > 100) {
                    sorted.take(100) + StorageItem(
                        "其余 ${sorted.size - 100} 个小文件", "",
                        sorted.drop(100).sumOf { it.bytes }
                    )
                } else sorted
            }

            "apk" -> {
                val f = File(ctx.applicationInfo.sourceDir)
                listOf(StorageItem(f.name, f.path, runCatching { f.length() }.getOrDefault(0L)))
            }

            "meta" -> {
                val items = mutableListOf<StorageItem>()
                val dataFile = File(ctx.filesDir, "novel-forge-data.json")
                if (dataFile.exists()) {
                    items.add(
                        StorageItem(
                            dataFile.name, "全部书、设置、目录都在这一个文件里",
                            dataFile.length()
                        )
                    )
                }
                ctx.filesDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name != dataFile.name) {
                        items.add(StorageItem(f.name, "", f.length()))
                    }
                }
                items.sortedByDescending { it.bytes }
            }

            else -> emptyList()
        }
    }

    /** 清缓存：cacheDir 整个清空（导出文件、下载的 APK、网页缓存都在里面） */
    fun clearCache(ctx: Context): Long {
        val freed = dirSize(ctx.cacheDir)
        clearDir(ctx.cacheDir, keepSelf = true)
        ctx.cacheDir.mkdirs()
        return freed
    }

    fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var sum = 0L
        dir.listFiles()?.forEach { f ->
            sum += if (f.isDirectory) dirSize(f) else f.length()
        }
        return sum
    }

    private fun clearDir(dir: File?, keepSelf: Boolean) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { f ->
            runCatching { f.deleteRecursively() }
        }
        if (!keepSelf) runCatching { dir.deleteRecursively() }
    }

    fun format(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L ->
            String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024L ->
            String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L ->
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
