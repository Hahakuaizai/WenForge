package com.novelforge.app.data

/**
 * 判断「要导入的这本书」是不是书架上已经有了。
 *
 * 依据三样：内容指纹、文件名、格式。
 *  · 内容指纹 —— 章节数 + 每章标题 + 总字数，同一本书怎么改名都躲不掉
 *  · 文件名   —— 用户说的「文件名称一样」
 *  · 格式     —— 扩展名
 *
 * 内容和文件名都对得上，才算「同一本」，避免不同书撞名误判。
 */
object DuplicateFinder {

    data class Verdict(
        /** 书架里已存在的那本 */
        val existing: ImportedBook,
        /** 内容完全一致 */
        val sameContent: Boolean,
        /** 文件名（去扩展名）一致 */
        val sameName: Boolean,
        /** 格式一致 */
        val sameExt: Boolean
    ) {
        val isSameBook: Boolean get() = sameContent && sameName
    }

    /** 章节标题+字数算出来的短指纹。内容变了指纹就变，改名不影响 */
    fun fingerprintOf(chapters: List<ImportedChapter>): String {
        if (chapters.isEmpty()) return ""
        val sb = StringBuilder()
        chapters.forEach { ch ->
            sb.append(ch.title.trim()).append('|').append(ch.content.length).append('\n')
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val dig = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return dig.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun baseName(name: String): String = name.substringBeforeLast('.').trim()

    private fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /**
     * 在书架上找和 incoming 重复的书。
     * 只有「内容指纹一致」或「文件名+格式一致」才算候选。
     */
    fun find(
        books: List<ImportedBook>,
        incomingTitle: String,
        incomingSourceName: String,
        incomingFingerprint: String,
        incomingChapterCount: Int
    ): Verdict? {
        val inName = baseName(incomingSourceName.ifBlank { incomingTitle })
        val inExt = extOf(incomingSourceName)

        var best: Verdict? = null
        books.forEach { b ->
            // 指纹：优先用存下来的，老数据没有就现算
            val bFp = b.fingerprint.ifBlank { fingerprintOf(b.chapters) }
            val sameContent = incomingFingerprint.isNotBlank() &&
                bFp.isNotBlank() &&
                bFp == incomingFingerprint

            val bName = baseName(b.sourceName.ifBlank { b.title })
            val sameName = inName.isNotBlank() && bName.equals(inName, ignoreCase = true)
            val bExt = extOf(b.sourceName)
            val sameExt = inExt.isNotBlank() && bExt.isNotBlank() && inExt == bExt

            // 内容一致 → 肯定是同一本
            // 名字+格式一致但内容不同 → 同名不同书，也算重复（让用户决定要不要共存）
            val hit = sameContent || (sameName && sameExt)
            if (!hit) return@forEach

            val v = Verdict(b, sameContent, sameName, sameExt)
            // 内容一致的优先，其次名字一致的
            val cur = best
            if (cur == null ||
                (v.sameContent && !cur.sameContent) ||
                (v.sameContent == cur.sameContent && v.sameName && !cur.sameName)
            ) {
                best = v
            }
        }
        return best
    }

    /**
     * 生成不重复的书名：原名已被占用就加（1）（2）…
     * 「（1）」这种后缀会先剥掉，避免出现《书名（1）（2）》
     */
    fun uniqueTitle(desired: String, taken: Collection<String>): String {
        val base = desired.replace(Regex("""[（(]\d+[）)]$"""), "").trim().ifBlank { "未命名" }
        val used = taken.map { it.trim() }.toSet()
        if (base !in used) return base
        var i = 1
        while (true) {
            val candidate = "$base（$i）"
            if (candidate !in used) return candidate
            i++
        }
    }
}
