package com.novelforge.app.gen

import com.novelforge.app.data.Chapter
import com.novelforge.app.data.NovelProject
import com.novelforge.app.data.Prompts
import com.novelforge.app.data.ProviderConfig
import com.novelforge.app.data.Store
import com.novelforge.app.net.AiClient
import com.novelforge.app.net.CallMetrics
import com.novelforge.app.net.ChatMessage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** 生成流水线：大纲 → 分章细纲 → 逐章正文。
 *
 *  v2 升级点：
 *  - 分阶段模型：大纲/细纲用 outlineClient，正文用 bodyClient（可在全局设置里分开指定）
 *  - 每一步失败自动重试（autoRetry 次，间隔递增）
 *  - 正文走流式输出，进度里实时报已生成字数；接口不支持流式时自动回退非流式
 *  - 自定义系统提示词（全局设置里填了就替换内置作家角色）
 *  每一步完成立刻落盘，中断不丢内容；重跑自动跳过已完成章节（断点续写）。 */
class Generator(
    private val store: Store,
    private val outlineClient: AiClient,
    private val bodyClient: AiClient,
    private val autoRetry: Int = 2,
    private val customSystemPrompt: String = "",
    /** 计量记录回调：(阶段, 章号, 供应商, 计量) —— 由 ViewModel 落盘成用量记录 */
    private val recorder: (String, Int, ProviderConfig, CallMetrics) -> Unit = { _, _, _, _ -> },
    /** 每章开始前重新解析供应商：这样生成途中换模型 / 换档位能立刻生效 */
    private val refreshProviders: () -> Unit = {},
    /** 模型思考过程的实时回调（DeepSeek 这类会吐 reasoning_content） */
    private val onReasoning: (String) -> Unit = {},
    /** 记忆系统开关与上下文预算（来自全局设置） */
    private val memoryEnabled: Boolean = true,
    private val contextBudgetChars: Int = 6000
) {

    // 当前阶段标签，onCall 回调打标用
    private var outlineStage = "大纲"
    private var bodyStage = "正文"
    private var bodyChapter = 0

    init {
        outlineClient.onCall = { m -> recorder(outlineStage, 0, outlineClient.providerConfig, m) }
        bodyClient.onCall = { m -> recorder(bodyStage, bodyChapter, bodyClient.providerConfig, m) }
    }

    private fun List<Pair<String, String>>.toMessages(): List<ChatMessage> = map { (role, content) ->
        ChatMessage(role, if (role == "system" && customSystemPrompt.isNotBlank()) customSystemPrompt else content)
    }

    /** 带自动重试的非流式调用。 */
    private suspend fun chatWithRetry(
        client: AiClient,
        messages: List<ChatMessage>,
        label: String,
        onProgress: (String) -> Unit
    ): String {
        var lastError: Throwable? = null
        repeat(autoRetry + 1) { attempt ->
            if (attempt > 0) {
                onProgress("$label 失败，正在重试（第 $attempt 次）…")
                delay(1500L * attempt)
            }
            client.chat(messages).fold(
                onSuccess = { return it.text },
                onFailure = { lastError = it }
            )
        }
        throw lastError ?: IllegalStateException("$label 失败")
    }

    /** 正文专用：先流式，失败回退非流式；整体再套自动重试。 */
    private suspend fun writeBody(
        messages: List<ChatMessage>,
        label: String,
        onProgress: (String) -> Unit
    ): String {
        var lastError: Throwable? = null
        repeat(autoRetry + 1) { attempt ->
            if (attempt > 0) {
                onProgress("$label 失败，正在重试（第 $attempt 次）…")
                delay(1500L * attempt)
            }
            val streamed = bodyClient.chatStream(
                messages,
                { chars -> onProgress("$label（已生成 $chars 字）…") },
                onReasoning
            )
            streamed.fold(
                onSuccess = { return it.text },
                onFailure = { streamError ->
                    // 有些网关不支持流式，回退到普通调用再试一次
                    bodyClient.chat(messages).fold(
                        onSuccess = { return it.text },
                        onFailure = { lastError = it }
                    )
                    if (lastError == null) lastError = streamError
                }
            )
        }
        throw lastError ?: IllegalStateException("$label 失败")
    }

    /** 续写：在现有章节后面再规划 count 章，不碰已写好的内容 */
    suspend fun appendChapters(project: NovelProject, count: Int, onProgress: (String) -> Unit) {
        val from = project.chapters.size + 1
        val to = project.chapters.size + count
        onProgress("正在规划第 $from–$to 章…")
        val raw = chatWithRetry(
            outlineClient,
            Prompts.appendOutlines(project, from, to).toMessages(),
            "续写细纲",
            onProgress
        )
        val parsed = parseChapterOutlines(raw, count)
        if (parsed.isEmpty()) {
            error("细纲解析失败：模型没按「第N章|标题|细纲」格式输出，可以重试")
        }
        // 章节号强制连续，防止模型从 1 重新编号
        val fixed = parsed.mapIndexed { i, c -> c.copy(index = from + i) }
        project.chapters.addAll(fixed)
        project.chapterCount = project.chapters.size
        project.updatedAt = System.currentTimeMillis()
        store.upsertProject(project)
        onProgress("已规划第 $from–$to 章，全书共 ${project.chapters.size} 章")
    }

    suspend fun generate(project: NovelProject, onProgress: (String) -> Unit) {
        val ctx = currentCoroutineContext()

        // 1) 全书大纲
        if (project.outline.isBlank()) {
            outlineStage = "大纲"
            onProgress("正在生成全书大纲…")
            project.outline = chatWithRetry(outlineClient, Prompts.outline(project).toMessages(), "生成大纲", onProgress)
            store.upsertProject(project)
            ctx.ensureActive()
        }

        // 2) 分章细纲
        if (project.chapters.isEmpty()) {
            outlineStage = "细纲"
            onProgress("正在拆分 ${project.chapterCount} 章细纲…")
            val raw = chatWithRetry(outlineClient, Prompts.chapterOutlines(project).toMessages(), "生成细纲", onProgress)
            project.chapters = parseChapterOutlines(raw, project.chapterCount)
            if (project.chapters.isEmpty()) {
                error("细纲解析失败：模型没有按「第N章|标题|细纲」格式输出，可以重试")
            }
            store.upsertProject(project)
            ctx.ensureActive()
        }

        // 3) 逐章正文（跳过已完成的，支持断点续写）
        for (chapter in project.chapters) {
            if (chapter.done) continue
            ctx.ensureActive()
            refreshProviders()
            bodyStage = "正文"
            bodyChapter = chapter.index
            val label = "正在写第 ${chapter.index}/${project.chapters.size} 章《${chapter.title}》"
            onProgress("$label…")
            val prevTail = project.chapters
                .lastOrNull { it.index < chapter.index && it.done }
                ?.content?.takeLast(600).orEmpty()
            val contextBlock = ContextBuilder.assemble(project, chapter, prevTail, contextBudgetChars, memoryEnabled)
            val text = writeBody(Prompts.chapter(project, chapter, contextBlock).toMessages(), label, onProgress)
            chapter.content = stripTitleLine(text, chapter.title)
            chapter.done = true
            store.upsertProject(project)   // 每写完一章立刻保存
            if (memoryEnabled) updateMemory(project, chapter, onProgress)
        }
        onProgress("全部完成：共 ${project.chapters.size} 章，约 ${project.totalWords} 字")
    }

    /** 单章重写（工作台里对不满意的章节用） */
    suspend fun rewriteChapter(project: NovelProject, chapter: Chapter, onProgress: (String) -> Unit) {
        bodyStage = "重写"
        bodyChapter = chapter.index
        val label = "正在重写第 ${chapter.index} 章《${chapter.title}》"
        onProgress("$label…")
        val prevTail = project.chapters
            .lastOrNull { it.index < chapter.index && it.done }
            ?.content?.takeLast(600).orEmpty()
        val contextBlock = ContextBuilder.assemble(project, chapter, prevTail, contextBudgetChars, memoryEnabled)
        val text = writeBody(Prompts.chapter(project, chapter, contextBlock).toMessages(), label, onProgress)
        chapter.content = stripTitleLine(text, chapter.title)
        chapter.done = true
        store.upsertProject(project)
        if (memoryEnabled) {
            // 重写后把这一章的旧摘要撤掉，按新正文重新抽取
            project.memory.recentSummaries.removeAll { it.startsWith("第${chapter.index}章") }
            updateMemory(project, chapter, onProgress)
        }
        onProgress("第 ${chapter.index} 章已重写")
    }

    /** 章节完成后抽取记忆（摘要/人物/事实/伏笔）写进记忆库；失败只跳过，绝不拖垮正文生成。 */
    private suspend fun updateMemory(project: NovelProject, chapter: Chapter, onProgress: (String) -> Unit) {
        try {
            outlineStage = "记忆"
            val raw = outlineClient.chat(Prompts.memoryUpdate(project, chapter).toMessages())
                .getOrThrow().text
            val mem = project.memory
            raw.lineSequence().forEach { rawLine ->
                val parts = rawLine.trim().trim('*', '#', ' ').split("|", "｜")
                    .map { it.trim() }
                when (parts.firstOrNull()) {
                    "摘要" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        mem.recentSummaries.add("第${chapter.index}章《${chapter.title}》：$it")
                    }
                    "人物" -> {
                        val name = parts.getOrNull(1).orEmpty()
                        val info = parts.getOrNull(2).orEmpty()
                        if (name.isNotBlank() && info.isNotBlank()) {
                            val existing = mem.characters.firstOrNull { it.name == name }
                            if (existing != null) existing.info = info
                            else mem.characters.add(com.novelforge.app.data.MemoryCharacter(name, info))
                        }
                    }
                    "事实" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        if (mem.facts.none { f -> f == it }) mem.facts.add(it)
                    }
                    "伏笔" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        if (mem.threads.none { t -> t == it }) mem.threads.add(it)
                    }
                    "回收" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { kw ->
                        mem.threads.removeAll { t -> t.contains(kw.take(6)) || kw.contains(t.take(6)) }
                    }
                }
            }
            store.upsertProject(project)
            maybeCompress(project, onProgress)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            onProgress("（本章记忆更新失败，已跳过，不影响正文）")
        }
    }

    /** 上下文压缩：近期摘要攒得太长时，把较旧的一批压进长程摘要，只保留最近 4 章的摘要原文。 */
    private suspend fun maybeCompress(project: NovelProject, onProgress: (String) -> Unit) {
        val mem = project.memory
        val total = mem.recentSummaries.sumOf { it.length }
        if (total <= 3500 || mem.recentSummaries.size <= 5) return
        try {
            val keep = 4
            val oldOnes = mem.recentSummaries.dropLast(keep)
            val oldText = buildString {
                if (mem.compressedSummary.isNotBlank()) append(mem.compressedSummary).append("\n")
                append(oldOnes.joinToString("\n"))
            }
            outlineStage = "压缩"
            onProgress("正在压缩早期剧情记忆…")
            val compressed = outlineClient.chat(Prompts.compress(project, oldText).toMessages())
                .getOrThrow().text
            mem.compressedSummary = compressed.trim()
            repeat(oldOnes.size) { mem.recentSummaries.removeAt(0) }
            store.upsertProject(project)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 压缩失败不丢数据：摘要原样保留，下次再试
        }
    }

    /** 解析「第N章|标题|细纲」格式；容忍模型偶尔加的 Markdown 符号和空格。 */
    private fun parseChapterOutlines(raw: String, expected: Int): MutableList<Chapter> {
        val result = mutableListOf<Chapter>()
        val lineRegex = Regex("""第\s*(\d+)\s*章\s*[|｜]\s*(.+?)\s*[|｜]\s*(.+)""")
        raw.lineSequence().forEach { line ->
            val m = lineRegex.find(line.trim().trim('*', '#', ' ')) ?: return@forEach
            val idx = m.groupValues[1].toIntOrNull() ?: return@forEach
            result.add(
                Chapter(
                    index = idx,
                    title = m.groupValues[2].trim(),
                    outline = m.groupValues[3].trim()
                )
            )
        }
        return result.sortedBy { it.index }.take(expected).toMutableList()
    }

    /** 模型常把标题又写一遍，去掉正文第一行里重复的标题，阅读时单独展示。 */
    private fun stripTitleLine(text: String, title: String): String {
        val trimmed = text.trim()
        val firstLine = trimmed.lineSequence().firstOrNull()?.trim()?.trim('#', ' ', '*').orEmpty()
        return if (firstLine.isNotBlank() && (firstLine == title || firstLine.endsWith(title))) {
            trimmed.substringAfter('\n', "").trim()
        } else trimmed
    }
}
