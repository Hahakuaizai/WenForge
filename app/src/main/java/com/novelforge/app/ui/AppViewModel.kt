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
import com.novelforge.app.data.ModelConfig
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
import com.novelforge.app.net.BookHit
import com.novelforge.app.net.DownloadHub
import com.novelforge.app.net.DownloadService
import com.novelforge.app.net.Novels
import com.novelforge.app.net.SourceBook
import com.novelforge.app.net.UpdateClient
import com.novelforge.app.net.UpdateEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 余额查询的 UI 状态 */
data class BalanceUi(val loading: Boolean = false, val info: BalanceInfo? = null, val error: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store.shared(app)

    init {
        // 触感反馈的开关/强度先同步进 Haptics，设置页改了再由 setHaptics 更新
        Haptics.configure(
            store.data.settings.hapticsEnabled,
            store.data.settings.hapticsStrength
        )
    }

    /** 数据快照：每次写操作后刷新，驱动 UI 重组 */
    // 用 neverEqualPolicy：数据是原地改的，默认的结构相等会让重组失效
    var data by mutableStateOf(store.data, androidx.compose.runtime.neverEqualPolicy())
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
    var importHits by mutableStateOf<List<BookHit>>(emptyList())
        private set
    var importCatalog by mutableStateOf<SourceBook?>(null)
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

    /** 供应商相关的小改动只动这个计数器，避免整个 data 重组拖慢开关 */
    var providerRevision by mutableStateOf(0)
        private set

    private fun bumpProviderRevision() {
        providerRevision++
    }

    /** 开关一个供应商（关掉的不出现在写作切换里） */
    fun setProviderEnabled(id: String, enabled: Boolean) {
        val i = store.data.providers.indexOfFirst { it.id == id }
        if (i < 0) return
        // 只动这一套的总开关，模型各自的状态原样保留
        store.data.providers[i] = store.data.providers[i].copy(enabled = enabled)
        store.save()
        bumpProviderRevision()
    }

    /** 开关某个供应商里的一个模型 */
    fun setModelEnabled(providerId: String, modelName: String, enabled: Boolean) {
        val i = store.data.providers.indexOfFirst { it.id == providerId }
        if (i < 0) return
        val p = store.data.providers[i]
        val cfgs = p.modelConfigs.toMutableList()
        val j = cfgs.indexOfFirst { it.name == modelName }
        if (j >= 0) cfgs[j] = cfgs[j].copy(enabled = enabled)
        else cfgs.add(ModelConfig(name = modelName, enabled = enabled))
        store.data.providers[i] = p.copy(modelConfigs = cfgs)
        store.save()
        bumpProviderRevision()
    }

    /** 模型是否启用（没配置过就是启用） */
    fun isModelEnabled(providerId: String, modelName: String): Boolean {
        val p = store.data.providers.firstOrNull { it.id == providerId } ?: return true
        return p.modelConfigs.firstOrNull { it.name == modelName }?.enabled ?: true
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

    /** 批量删除书架上的书（novel = 自己写的，imported = 导入/下载的） */
    fun deleteBooks(items: List<Pair<String, String>>) {
        items.forEach { (kind, id) ->
            when (kind) {
                "novel" -> store.data.projects.removeAll { it.id == id }
                else -> store.data.importedBooks.removeAll { it.id == id }
            }
        }
        store.save()
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

    // —— 在线书库 ——

    /** 搜书名，所有书源并发 */
    fun searchOnline(keyword: String) {
        val q = keyword.trim()
        if (q.isBlank()) return
        importBusy = true
        importStatus = "正在搜索《$q》…"
        importCatalog = null
        importHits = emptyList()
        viewModelScope.launch {
            runCatching { Novels.searchAll(q) }.fold(
                onSuccess = { out ->
                    importHits = out.hits
                    importStatus = buildString {
                        append("找到 ${out.hits.size} 本")
                        val bySource = out.hits.groupBy { h -> h.sourceName }
                        if (bySource.size > 1) {
                            append("（")
                            append(bySource.entries.joinToString("、") { e -> "${e.key} ${e.value.size}" })
                            append("）")
                        }
                        if (out.errors.isNotEmpty()) append(" · ${out.errors.joinToString("、")}")
                    }
                },
                onFailure = { importStatus = "❌ ${it.message}" }
            )
            importBusy = false
        }
    }

    /** 点一本书，拉目录 */
    fun openOnlineBook(hit: BookHit) {
        importBusy = true
        importStatus = "正在读取《${hit.title}》的目录…"
        importCatalog = null
        viewModelScope.launch {
            runCatching { Novels.sourceOf(hit.sourceId).catalog(hit) }.fold(
                onSuccess = { book ->
                    importCatalog = book
                    importStatus = "《${book.title}》共 ${book.chapters.size} 章 · ${book.sourceName}"
                },
                onFailure = { importStatus = "❌ 目录读取失败：${it.message}" }
            )
            importBusy = false
        }
    }

    /** 直接粘目录页地址 */
    fun parseCatalog(url: String) {
        val target = url.trim()
        if (!target.startsWith("http")) return
        importBusy = true
        importStatus = "正在解析这个地址…"
        importCatalog = null
        importHits = emptyList()
        viewModelScope.launch {
            runCatching { Novels.catalog(target) }.fold(
                onSuccess = {
                    importCatalog = it
                    importStatus = "解析成功：《${it.title}》共 ${it.chapters.size} 章"
                },
                onFailure = { importStatus = "❌ ${it.message}" }
            )
            importBusy = false
        }
    }

    /** 整本下载：交给前台服务去跑，切走/息屏都继续 */
    fun startDownload(onDone: (String) -> Unit) {
        val catalog = importCatalog ?: return
        if (catalog.chapters.isEmpty()) return

        // 同一本书已经在下载就不重复开
        if (DownloadHub.state?.running == true) {
            importStatus = "已经有一本在下，先等它下完或者点停止"
            return
        }

        val ctx = getApplication<Application>()
        val refs = catalog.chapters.map {
            com.novelforge.app.data.PendingChapter(it.title, it.url)
        }

        // 已经下过 / 下到一半的同一本书，接着原来那本下，不再新建一本
        val existing = store.data.importedBooks.firstOrNull {
            it.origin == "crawl" &&
                it.title == catalog.title &&
                (it.sourceId.isEmpty() || it.sourceId == catalog.sourceId)
        }
        val book = if (existing != null) {
            // 换一套新地址表，但保留已下的章节，从断点继续
            existing.pendingChapters = refs
            if (existing.sourceUrl.isBlank()) existing.sourceUrl = catalog.sourceUrl
            if (existing.sourceId.isBlank()) existing.sourceId = catalog.sourceId
            existing
        } else {
            ImportedBook(
                title = catalog.title.ifBlank { "未命名" },
                author = catalog.author,
                sourceUrl = catalog.sourceUrl,
                origin = "crawl",
                sourceId = catalog.sourceId,
                pendingChapters = refs
            ).also { store.data.importedBooks.add(it) }
        }
        store.save()
        refresh()

        DownloadService.start(ctx, book.id)
        val done = book.chapters.size
        importStatus = if (done > 0) {
            "《${book.title}》已下 $done 章，从第 ${done + 1} 章接着下"
        } else {
            "已在后台开始下载，可以退出这个页面"
        }
        onDone(book.id)
    }

    /** 接着下：地址表是当初存下来的，还是同一个源、同一批地址 */
    fun resumeDownload(bookId: String) {
        val book = store.data.importedBooks.firstOrNull { it.id == bookId } ?: return
        if (book.pendingChapters.isEmpty()) return
        if (DownloadHub.state?.running == true) return
        DownloadService.start(getApplication(), bookId)
        importStatus = "继续下载《${book.title}》"
    }

    /** 未下完的书（有地址表但还没抓完） */
    fun unfinishedBooks(): List<ImportedBook> =
        store.data.importedBooks.filter { it.pendingChapters.size > it.chapters.size }

    fun removeImportedBook(id: String) {
        store.data.importedBooks.removeAll { it.id == id }
        store.save()
        refresh()
    }

    /** 服务在另一个地方写数据，界面要跟上 */
    fun syncFromStore() {
        data = store.data
    }

    /** 主动停止（只有这一个入口会停） */
    fun cancelImport() {
        DownloadService.stop(getApplication())
    }

    /** 界面用：当前下载进度 */
    fun downloadState() = DownloadHub.state

    fun clearDownloadBanner() {
        val st = DownloadHub.state ?: return
        if (!st.running) DownloadHub.state = null
    }

    fun clearOnlineSearch() {
        importHits = emptyList()
        importCatalog = null
        importStatus = ""
    }

    /** 导入本地 txt / md 文件 */
    /** 整本书的内容指纹：每章标题+正文一起算 SHA-256 */
    fun hashChapters(chapters: List<com.novelforge.app.data.ImportedChapter>): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        chapters.forEach { ch ->
            md.update(ch.title.toByteArray(Charsets.UTF_8))
            md.update(1)
            md.update(ch.content.toByteArray(Charsets.UTF_8))
            md.update(2)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 判重：文件名、文件格式、内容三样都一样才算重复。
     * 老数据没记这三样（sourceHash 为空），永远判不出重复 —— 宁可漏判，不误伤。
     */
    fun findDuplicateBook(
        fileName: String,
        chapters: List<com.novelforge.app.data.ImportedChapter>
    ): ImportedBook? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || chapters.isEmpty()) return null
        val base = fileName.substringBeforeLast('.')
        val hash = hashChapters(chapters)
        return store.data.importedBooks.firstOrNull {
            it.sourceHash == hash && it.sourceExt == ext &&
                it.sourceName.substringBeforeLast('.') == base
        }
    }

    /** 书架里没被占用的名字：占了就往后加（1）（2） */
    private fun uniqueImportedTitle(base: String): String {
        val taken = (store.data.importedBooks.map { it.title } +
            store.data.projects.map { it.title }).toSet()
        if (base !in taken) return base
        var n = 1
        while ("$base（$n）" in taken) n++
        return "$base（$n）"
    }

    fun importLocalBook(
        parsed: com.novelforge.app.data.BookParser.Parsed,
        titleOverride: String = "",
        sourceKey: String = "",
        sourceName: String = "",
        dedupeTitle: Boolean = false
    ): String {
        var title = titleOverride.ifBlank { parsed.title }.ifBlank { "未命名" }
        if (dedupeTitle) title = uniqueImportedTitle(title)
        val book = ImportedBook(
            title = title,
            author = parsed.author,
            chapters = parsed.chapters,
            origin = "local",
            sourceKey = sourceKey,
            sourceName = sourceName,
            sourceExt = sourceName.substringAfterLast('.', "").lowercase(),
            sourceHash = hashChapters(parsed.chapters)
        )
        store.data.importedBooks.add(book)
        store.save()
        refresh()
        return book.id
    }

    /** 覆盖：用新解析的内容替换书架里那本（书名、id、入库时间都不动） */
    fun overwriteImportedBook(
        id: String,
        parsed: com.novelforge.app.data.BookParser.Parsed,
        sourceName: String,
        sourceKey: String = ""
    ) {
        val b = store.data.importedBooks.firstOrNull { it.id == id } ?: return
        b.chapters = parsed.chapters
        b.author = parsed.author
        b.sourceName = sourceName
        b.sourceExt = sourceName.substringAfterLast('.', "").lowercase()
        b.sourceHash = hashChapters(parsed.chapters)
        if (sourceKey.isNotBlank()) b.sourceKey = sourceKey
        store.save()
        refresh()
    }

    /** 改导入书的书名 */
    fun renameImportedBook(id: String, title: String) {
        val b = store.data.importedBooks.firstOrNull { it.id == id } ?: return
        b.title = title.ifBlank { "未命名" }
        store.save()
        data = store.data
    }

    /** 保存阅读器偏好 */
    /** 触感反馈：开关 + 强度（设置页即时生效，不走「保存设置」按钮） */
    fun setHaptics(enabled: Boolean, strength: Float) {
        val st = store.data.settings
        st.hapticsEnabled = enabled
        st.hapticsStrength = strength
        store.save()
        data = store.data
        Haptics.configure(enabled, strength)
    }

    fun saveReaderPrefs(fontSize: Float, lineHeight: Float, theme: String, pageMode: String) {
        val st = store.data.settings
        st.readerFontSize = fontSize
        st.readerLineHeight = lineHeight
        st.readerTheme = theme
        st.readerPageMode = pageMode
        store.save()
        data = store.data
    }

    /** 书架编辑：改名 / 改作者，作品和导入的书都支持 */
    fun updateBookTitle(kind: String, id: String, title: String) {
        val name = title.trim().ifBlank { "未命名" }
        if (kind == "novel") {
            store.data.projects.firstOrNull { it.id == id }?.let {
                it.title = name
                store.save()
                refresh()
            }
        } else {
            store.data.importedBooks.firstOrNull { it.id == id }?.let {
                it.title = name
                store.save()
                refresh()
            }
        }
    }

    fun updateBookAuthor(kind: String, id: String, author: String) {
        val a = author.trim()
        if (kind == "novel") {
            store.data.projects.firstOrNull { it.id == id }?.let {
                it.protagonist = a
                store.save()
                refresh()
            }
        } else {
            store.data.importedBooks.firstOrNull { it.id == id }?.let {
                it.author = a
                store.save()
                refresh()
            }
        }
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

    // —— 更新 ——

    var updateChecking by mutableStateOf(false)
        private set
    var updateEntries by mutableStateOf<List<UpdateEntry>>(emptyList())
        private set
    var updateStatus by mutableStateOf("")
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0f)
        private set

    /** 启动时静默检查发现的新版本（主界面弹一次提示用） */
    var updateAvailable by mutableStateOf(false)
        private set

    /** GitHub Releases 渠道查到的最新版（和局域网的比，谁新用谁） */
    var ghUpdate by mutableStateOf<UpdateEntry?>(null)
        private set

    /** 两个渠道里最新的那个条目；directUrl 非空 = 来自 GitHub */
    val latestUpdate: UpdateEntry?
        get() = listOfNotNull(updateEntries.firstOrNull(), ghUpdate)
            .maxByOrNull { it.versionCode }

    // 一键更新：检查发现新版后先在后台把 APK 下好，点「立即更新」直接装，不用再等
    private var preparedApk: java.io.File? = null
    private var preparedCode: Int = -1

    fun currentVersion(): Pair<String, Int> = UpdateClient.installedVersion(getApplication())

    fun setUpdateServer(url: String) {
        store.data.settings.updateServerUrl = url.trim()
        store.save()
        data = store.data
    }

    /** 检查更新：两个渠道都查 —— 自己的电脑（局域网 manifest）和 GitHub Releases，
     *  谁的版本号新就用谁。silent=true 是启动时的自动检查：不写状态文字、连不上不打扰，
     *  只在确实有新版本时把 updateAvailable 置真，并顺手在后台预下载。 */
    fun checkUpdates(silent: Boolean = false) {
        val url = store.data.settings.updateServerUrl.ifBlank { "http://127.0.0.1:8787" }
        if (!silent) {
            updateChecking = true
            updateStatus = "正在连 ${url} …"
        }
        viewModelScope.launch {
            // 渠道一：自己的电脑
            val lan = runCatching { UpdateClient.fetch(url) }.getOrNull()
            if (lan != null) {
                updateEntries = lan
                val (_, cur) = currentVersion()
                store.data.settings.updateHistory = lan.take(10).map { e ->
                    com.novelforge.app.data.UpdateRecord(
                        versionName = e.versionName,
                        versionCode = e.versionCode,
                        time = e.time,
                        notes = e.notes,
                        installed = e.versionCode <= cur
                    )
                }
                store.save()
                data = store.data
            }
            // 渠道二：GitHub Releases（电脑不开着也能更）
            runCatching { com.novelforge.app.net.GithubUpdate.fetchLatest() }
                .getOrNull()?.let { ghUpdate = it }

            val (name, code) = currentVersion()
            val latest = latestUpdate
            updateAvailable = latest != null && latest.versionCode > code
            if (latest != null && latest.versionCode > code) {
                prepareInBackground(latest, url)
            }
            if (!silent) {
                updateStatus = when {
                    latest == null ->
                        if (lan == null) "❌ 电脑连不上，GitHub 也没查到（没网？）"
                        else "服务器上还没有版本"
                    latest.versionCode > code ->
                        "有新版本 ${latest.versionName}（当前 $name）" +
                            if (latest.directUrl.isNotBlank()) " · 来自 GitHub" else ""
                    else -> "已经是最新的 $name"
                }
                updateChecking = false
            }
        }
    }

    /** 后台预下载：点「立即更新」时不用再等下载 */
    private fun prepareInBackground(entry: UpdateEntry, serverUrl: String) {
        if (preparedCode == entry.versionCode && preparedApk?.exists() == true) return
        preparedCode = entry.versionCode
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            runCatching { UpdateClient.download(serverUrl, entry, ctx) {} }
                .onSuccess { preparedApk = it }
        }
    }

    fun scanForUpdateServer() {
        updateChecking = true
        updateStatus = "正在扫局域网… 最多十几秒"
        viewModelScope.launch {
            val found = runCatching { UpdateClient.scanLan() }.getOrNull()
            if (found != null) {
                setUpdateServer(found)
                updateStatus = "找到 $found"
                updateChecking = false
                checkUpdates()
            } else {
                updateChecking = false
                updateStatus = "没找到。确认手机和电脑同一个 WiFi，电脑上的更新服务开着"
            }
        }
    }

    /** 下载并交给系统安装 */
    fun installUpdate(entry: UpdateEntry) {
        val ctx = getApplication<Application>()
        val url = store.data.settings.updateServerUrl
        if (!UpdateClient.canInstall(ctx)) {
            updateStatus = "先去允许「安装未知应用」，再回来点一次"
            UpdateClient.askInstallPermission(ctx)
            return
        }
        val ready = preparedApk?.takeIf { preparedCode == entry.versionCode && it.exists() }
        if (ready != null) {
            updateStatus = "下载好了，接着点「安装」"
            UpdateClient.install(ctx, ready)
            return
        }
        updateDownloading = true
        updateProgress = 0f
        updateStatus = "正在下载 ${entry.versionName}…"
        viewModelScope.launch {
            runCatching {
                UpdateClient.download(url, entry, ctx) { p -> updateProgress = p }
            }.fold(
                onSuccess = { file ->
                    updateStatus = "下载好了，接着点「安装」"
                    UpdateClient.install(ctx, file)
                },
                onFailure = { updateStatus = "❌ ${it.message}" }
            )
            updateDownloading = false
        }
    }
}
