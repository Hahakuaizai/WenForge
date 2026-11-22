package com.novelforge.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelforge.app.data.AppSettings
import com.novelforge.app.data.Chapter
import com.novelforge.app.data.Exporter
import com.novelforge.app.data.ImportedBook
import com.novelforge.app.data.ImportedChapter
import com.novelforge.app.data.NovelProject
import com.novelforge.app.data.Pricing
import com.novelforge.app.data.ProviderConfig
import com.novelforge.app.data.Store
import com.novelforge.app.data.StoryMemory
import com.novelforge.app.data.UsageRecord
import com.novelforge.app.gen.Generator
import com.novelforge.app.net.AiClient
import com.novelforge.app.net.BalanceClient
import com.novelforge.app.net.BalanceInfo
import com.novelforge.app.net.Catalog
import com.novelforge.app.net.NovelCrawler
import com.novelforge.app.net.SearchCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 余额查询的 UI 状态 */
data class BalanceUi(val loading: Boolean = false, val info: BalanceInfo? = null, val error: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    /** 数据快照：每次写操作后刷新，驱动 UI 重组 */
    var data by mutableStateOf(store.data)
        private set

    // —— 生成状态 ——
    var generatingProjectId by mutableStateOf<String?>(null)
        private set
    var genStatus by mutableStateOf("")
    /** 当前这一步的模型思考过程（可为空） */
    var genReasoning by mutableStateOf("")
        private set
    var genError by mutableStateOf<String?>(null)
        private set
    private var genJob: Job? = null

    // —— 测试连接 / 拉模型状态 ——
    var testResult by mutableStateOf<String?>(null)
        private set
    var testing by mutableStateOf(false)
        private set
    var fetchedModels by mutableStateOf<List<String>>(emptyList())
        private set
    var fetchingModels by mutableStateOf(false)
        private set

    // —— 余额状态（按供应商 id） ——
    var balances by mutableStateOf<Map<String, BalanceUi>>(emptyMap())
        private set

    // —— 导入状态 ——
    var importCandidates by mutableStateOf<List<SearchCandidate>>(emptyList())
        private set
    var importCatalog by mutableStateOf<Catalog?>(null)
        private set
    var importStatus by mutableStateOf("")
        private set
    var importBusy by mutableStateOf(false)
        private set
    private var importJob: Job? = null

    private fun refresh() {
        data = store.data
    }

    // —— 供应商 ——
    /** 把所有服务商和模型的参数拉到最大：1M 上下文 / 16K 输出 / 视觉开 / 四档强度全开 */
    fun maximizeAll() {
        val efforts = mutableListOf("off", "low", "high", "max")
        store.data.providers.forEach { p ->
            val base = p.copy(
                contextWindow = 1_000_000,
                maxTokens = 16384,
                supportsVision = true,
                autoCompactAt = 0.8f
            )
            base.supportedEfforts = efforts.toMutableList()
            // 强度四档全开，默认选「最高」
            if (base.reasoningEffort == "off" || base.reasoningEffort.isBlank()) {
                base.reasoningEffort = "max"
            }
            base.modelConfigs = p.modelConfigs.map { mc ->
                mc.copy(
                    contextWindow = 1_000_000,
                    maxTokens = 16384,
                    supportsVision = true,
                    autoCompactAt = 0.8f,
                    supportedEfforts = efforts.toMutableList(),
                    reasoningEffort = if (mc.reasoningEffort.isBlank() || mc.reasoningEffort == "off") "max"
                    else mc.reasoningEffort
                )
            }.toMutableList()
            store.upsertProvider(base)
        }
        refresh()
    }

    fun saveProvider(p: ProviderConfig) {
        store.upsertProvider(p)
        refresh()
    }

    fun deleteProvider(id: String) {
        store.deleteProvider(id)
        refresh()
    }

    fun setActiveProvider(id: String) {
        store.data.activeProviderId = id
        store.save()
        refresh()
    }

    fun testProvider(p: ProviderConfig) {
        testing = true
        testResult = null
        viewModelScope.launch {
            testResult = AiClient(p).testConnection()
                .fold(onSuccess = { "✅ $it" }, onFailure = { "❌ ${it.message}" })
            testing = false
        }
    }

    fun fetchModels(p: ProviderConfig) {
        fetchingModels = true
        viewModelScope.launch {
            AiClient(p).listModels().fold(
                onSuccess = { fetchedModels = it },
                onFailure = { testResult = "❌ 拉取模型列表失败：${it.message}" }
            )
            fetchingModels = false
        }
    }

    fun clearTestResult() {
        testResult = null
    }

    fun fetchBalance(providerId: String) {
        val provider = store.providerById(providerId) ?: return
        balances = balances + (providerId to BalanceUi(loading = true))
        viewModelScope.launch {
            val ui = BalanceClient.fetch(provider).fold(
                onSuccess = { BalanceUi(info = it) },
                onFailure = { BalanceUi(error = it.message ?: "查询失败") }
            )
            balances = balances + (providerId to ui)
        }
    }

    // —— 全局设置 ——
    fun saveSettings(s: AppSettings) {
        store.data.settings = s
        store.save()
        refresh()
    }

    // —— 项目 ——
    fun createProject(p: NovelProject): String {
        if (p.providerId.isBlank()) p.providerId = store.activeProvider()?.id ?: ""
        store.upsertProject(p)
        refresh()
        return p.id
    }

    fun deleteProject(id: String) {
        if (generatingProjectId == id) cancelGenerate()
        store.deleteProject(id)
        refresh()
    }

    /** 改作品的基本信息（书名、题材、主角等） */
    fun updateProjectInfo(p: NovelProject) {
        p.updatedAt = System.currentTimeMillis()
        store.upsertProject(p)
        data = store.data
    }

    fun project(id: String): NovelProject? = store.projectById(id)

    /** 章节手动编辑保存（标题 / 细纲 / 正文） */
    fun updateChapter(projectId: String, edited: Chapter) {
        val project = store.projectById(projectId) ?: return
        val target = project.chapters.firstOrNull { it.index == edited.index } ?: return
        target.title = edited.title
        target.outline = edited.outline
        target.content = edited.content
        target.done = edited.content.isNotBlank()
        store.upsertProject(project)
        refresh()
    }

    /** 记忆库手动编辑保存 */
    fun saveMemory(projectId: String, memory: StoryMemory) {
        val project = store.projectById(projectId) ?: return
        project.memory = memory
        store.upsertProject(project)
        refresh()
    }

    /** 导出生成作品为指定格式，返回文件给 UI 分享 */
    fun exportFile(projectId: String, format: Exporter.Format): File? {
        val project = store.projectById(projectId) ?: return null
        if (project.finishedChapters == 0) return null
        return Exporter.writeProject(getApplication(), project, format)
    }

    // —— 生成 ——

    /** 阶段路由：全局设置指定了就用全局的，否则用作品自己的供应商，再否则用当前激活的。 */
    private fun resolveProvider(stageId: String, project: NovelProject): ProviderConfig? {
        val s = store.data.settings
        val stageProviderId = when (stageId) {
            "outline" -> s.outlineProviderId
            "body" -> s.bodyProviderId
            else -> ""
        }
        val base = store.providerById(stageProviderId)
            ?: store.providerById(project.providerId)
            ?: store.activeProvider()
            ?: return null
        // 项目里单独选过模型 / 思考强度就用它，否则用 provider 的默认值
        val model = project.providerModel.ifBlank { base.model }
        val mc = base.modelConfigs.firstOrNull { it.name == model }
        var merged = base.copy(model = model)
        if (mc != null) {
            // 0 / 空 表示"跟随服务商默认"
            merged = merged.copy(
                contextWindow = if (mc.contextWindow > 0) mc.contextWindow else base.contextWindow,
                supportsVision = mc.supportsVision || base.supportsVision,
                supportedEfforts = mc.supportedEfforts.ifEmpty { base.supportedEfforts },
                maxTokens = if (mc.maxTokens > 0) mc.maxTokens else base.maxTokens,
                autoCompactAt = if (mc.autoCompactAt > 0f) mc.autoCompactAt else base.autoCompactAt
            )
        }
        val effort = project.reasoningEffort.ifBlank {
            mc?.reasoningEffort?.takeIf { it.isNotBlank() } ?: base.reasoningEffort
        }
        return merged.copy(reasoningEffort = effort)
    }

    /** 切换某个项目用哪个服务商 + 模型（可以跨服务商） */
    fun setProjectProvider(projectId: String, providerId: String, model: String) {
        val p = project(projectId) ?: return
        p.providerId = providerId
        p.providerModel = model
        p.updatedAt = System.currentTimeMillis()
        store.save()
        data = store.data
    }

    /** 切换某个项目用哪一档思考强度（只在该模型支持的档位里选） */
    fun setProjectEffort(projectId: String, effort: String) {
        val p = project(projectId) ?: return
        p.reasoningEffort = effort
        p.updatedAt = System.currentTimeMillis()
        store.save()
        data = store.data
    }

    /** 切换某个项目用哪个模型（从该 provider 勾选的模型池里挑） */
    fun setProjectModel(projectId: String, model: String) {
        val p = project(projectId) ?: return
        p.providerModel = model
        p.updatedAt = System.currentTimeMillis()
        store.save()
        data = store.data
    }

    private fun buildGenerator(project: NovelProject): Generator? {
        val outlineProvider = resolveProvider("outline", project) ?: return null
        val bodyProvider = resolveProvider("body", project) ?: return null
        val s = store.data.settings
        val outlineClient = AiClient(outlineProvider)
        val bodyClient = AiClient(bodyProvider)
        return Generator(
            store = store,
            outlineClient = outlineClient,
            bodyClient = bodyClient,
            refreshProviders = {
                resolveProvider("outline", project)?.let { outlineClient.updateProvider(it) }
                resolveProvider("body", project)?.let { bodyClient.updateProvider(it) }
            },
            autoRetry = s.autoRetry,
            customSystemPrompt = s.customSystemPrompt,
            recorder = { stage, chapterIndex, provider, metrics ->
                val now = System.currentTimeMillis()
                val cost = Pricing.costUsd(
                    provider, provider.model,
                    metrics.promptTokens, metrics.cachedTokens, metrics.completionTokens, now
                )
                store.data.usage.add(
                    UsageRecord(
                        time = now,
                        providerId = provider.id,
                        providerName = provider.name,
                        model = provider.model,
                        stage = stage,
                        projectId = project.id,
                        projectTitle = project.title,
                        chapterIndex = chapterIndex,
                        promptTokens = metrics.promptTokens,
                        completionTokens = metrics.completionTokens,
                        cachedTokens = metrics.cachedTokens,
                        latencyMs = metrics.latencyMs,
                        ttftMs = metrics.ttftMs,
                        costUsd = cost ?: 0.0,
                        priced = cost != null,
                        estimated = metrics.estimated
                    )
                )
                // 只保留最近 3000 条，防数据文件无限膨胀
                while (store.data.usage.size > 3000) store.data.usage.removeAt(0)
                store.save()
            },
            onReasoning = { genReasoning = it },
            memoryEnabled = s.memoryEnabled,
            contextBudgetChars = run {
                // 按模型的上下文窗口 × 压缩阈值折算成字符预算（中文约 1.4 字符/token），
                // 再和全局设置里那个值取小；两个都管住才不会撑爆窗口
                val auto = (bodyProvider.contextWindow * bodyProvider.autoCompactAt * 1.4f).toInt()
                val base = if (s.contextBudgetChars > 0) minOf(s.contextBudgetChars, auto) else auto
                base.coerceIn(2000, 80000)
            }
        )
    }

    private fun validateProvider(p: ProviderConfig?): String? = when {
        p == null -> "还没有配置模型供应商，先去「模型设置」加一套"
        p.baseUrl.isBlank() || p.model.isBlank() -> "供应商「${p.name}」的接口地址或模型名没填完整"
        else -> null
    }

    /** 加更：在当前章节后面再规划 count 章细纲 */
    fun appendChapters(projectId: String, count: Int) {
        if (generatingProjectId != null) return
        val project = store.projectById(projectId) ?: return
        val generator = buildGenerator(project)
        if (generator == null) {
            genError = "还没有配置模型供应商，先去「模型设置」加一套"
            return
        }
        generatingProjectId = projectId
        genError = null
        genReasoning = ""
        genStatus = "准备续写…"
        viewModelScope.launch {
            try {
                generator.appendChapters(project, count) { genStatus = it }
                data = store.data
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                genError = e.message ?: "续写失败"
            } finally {
                generatingProjectId = null
                genStatus = ""
            }
        }
    }

    fun startGenerate(projectId: String) {
        val project = store.projectById(projectId) ?: return
        val generator = buildGenerator(project)
        val error = validateProvider(resolveProvider("body", project)) ?: validateProvider(resolveProvider("outline", project))
        if (generator == null || error != null) {
            genError = error ?: "还没有配置模型供应商，先去「模型设置」加一套"
            return
        }
        genError = null
        genReasoning = ""
        generatingProjectId = projectId
        genStatus = "准备中…"
        genJob = viewModelScope.launch {
            try {
                generator.generate(project) { status ->
                    genStatus = status
                    refresh()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    genStatus = "已停止（已生成的内容都保存好了）"
                } else {
                    genError = e.message ?: "生成失败"
                    genStatus = "生成中断"
                }
            } finally {
                generatingProjectId = null
                refresh()
            }
        }
    }

    fun rewriteChapter(projectId: String, chapterIndex: Int) {
        val project = store.projectById(projectId) ?: return
        val chapter = project.chapters.firstOrNull { it.index == chapterIndex } ?: return
        val generator = buildGenerator(project) ?: run {
            genError = "还没有可用的模型供应商"
            return
        }
        genError = null
        generatingProjectId = projectId
        genJob = viewModelScope.launch {
            try {
                generator.rewriteChapter(project, chapter) { status ->
                    genStatus = status
                    refresh()
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    genError = e.message ?: "重写失败"
                }
            } finally {
                generatingProjectId = null
                refresh()
            }
        }
    }

    fun cancelGenerate() {
        genJob?.cancel()
    }

    // —— 爬取导入 ——

    fun searchNovel(name: String) {
        if (name.isBlank()) return
        importBusy = true
        importStatus = "正在按书名搜索候选站点…"
        importCatalog = null
        viewModelScope.launch {
            NovelCrawler.searchByName(name.trim()).fold(
                onSuccess = {
                    importCandidates = it
                    importStatus = "找到 ${it.size} 个候选，点一个解析它的目录"
                },
                onFailure = { importStatus = "❌ ${it.message}" }
            )
            importBusy = false
        }
    }

    fun parseCatalog(url: String) {
        val target = url.trim()
        if (target.isBlank()) return
        importBusy = true
        importStatus = "正在解析目录页…"
        viewModelScope.launch {
            NovelCrawler.fetchCatalog(target).fold(
                onSuccess = {
                    importCatalog = it
                    importStatus = "解析成功：《${it.title}》共 ${it.chapters.size} 章"
                },
                onFailure = { importStatus = "❌ ${it.message}" }
            )
            importBusy = false
        }
    }

    /** 开始整本下载：逐章抓取，每 25 章落盘一次，中途取消已下部分保留。 */
    fun startImportDownload(onDone: (String) -> Unit) {
        val catalog = importCatalog ?: return
        importBusy = true
        val book = ImportedBook(
            title = catalog.title,
            author = catalog.author,
            sourceUrl = catalog.sourceUrl
        )
        store.data.importedBooks.add(book)
        store.save()
        refresh()
        importJob = viewModelScope.launch {
            var failures = 0
            val total = minOf(catalog.chapters.size, 3000)
            try {
                for (i in 0 until total) {
                    val link = catalog.chapters[i]
                    importStatus = "正在下载 ${i + 1}/$total：${link.title}"
                    var text = NovelCrawler.fetchChapterText(link.url).getOrElse { "" }
                    // 失败或明显太短，再试一次
                    if (text.length < 80) {
                        kotlinx.coroutines.delay(500)
                        val retry = NovelCrawler.fetchChapterText(link.url).getOrElse { "" }
                        if (retry.length > text.length) text = retry
                    }
                    if (text.isBlank()) {
                        failures++
                        text = "（本章下载失败，可稍后重试）"
                    }
                    book.chapters.add(ImportedChapter(i + 1, link.title, text))
                    if (book.chapters.size % 25 == 0) {
                        store.save()
                        refresh()
                    }
                    delay(200)   // 对站点客气一点，别把人家打挂
                }
                importStatus = "下载完成：${book.chapters.size} 章" +
                    if (failures > 0) "（$failures 章失败）" else ""
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    importStatus = "已停止，已下载的 ${book.chapters.size} 章保留在书架"
                } else {
                    importStatus = "❌ 下载中断：${e.message}（已下载部分已保留）"
                }
            } finally {
                store.save()
                refresh()
                importBusy = false
                onDone(book.id)
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    /** 导入本地 txt / md 文件 */
    fun importLocalBook(
        parsed: com.novelforge.app.data.BookParser.Parsed,
        titleOverride: String = ""
    ): String {
        val book = ImportedBook(
            title = titleOverride.ifBlank { parsed.title }.ifBlank { "未命名" },
            author = parsed.author,
            chapters = parsed.chapters,
            origin = "local"
        )
        store.data.importedBooks.add(book)
        store.save()
        refresh()
        return book.id
    }

    /** 改导入书的书名 */
    fun renameImportedBook(id: String, title: String) {
        val b = store.data.importedBooks.firstOrNull { it.id == id } ?: return
        b.title = title.ifBlank { "未命名" }
        store.save()
        data = store.data
    }

    /** 保存阅读器偏好 */
    fun saveReaderPrefs(fontSize: Float, lineHeight: Float, theme: String, pageMode: String) {
        val st = store.data.settings
        st.readerFontSize = fontSize
        st.readerLineHeight = lineHeight
        st.readerTheme = theme
        st.readerPageMode = pageMode
        store.save()
        data = store.data
    }

    fun importedBook(id: String): ImportedBook? = store.data.importedBooks.firstOrNull { it.id == id }

    fun deleteImportedBook(id: String) {
        store.data.importedBooks.removeAll { it.id == id }
        store.save()
        refresh()
    }

    fun exportImportedFile(bookId: String, format: Exporter.Format): File? {
        val book = importedBook(bookId) ?: return null
        if (book.chapters.isEmpty()) return null
        return Exporter.writeImported(getApplication(), book, format)
    }
}
