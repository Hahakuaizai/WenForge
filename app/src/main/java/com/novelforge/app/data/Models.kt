package com.novelforge.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** 模型供应商配置：一套 = 一个接口地址 + 密钥 + 模型名。
 *  兼容 OpenAI 接口格式的服务（DeepSeek、OpenAI、各类中转、Magpie 网关等）都能用这一套。 */
@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "新供应商",
    /** 接口根地址，形如 https://api.deepseek.com/v1 或 Magpie 的 http://192.168.1.10:8317/v1 */
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** 勾选过的模型池：同一个接口下多选几个，生成时可以随时切换 */
    var models: MutableList<String> = mutableListOf(),
    /** 温度：写小说建议 0.7~1.0 */
    val temperature: Double = 0.85,
    /** 单次最大输出 token；章节正文建议 4000+ */
    val maxTokens: Int = 4096,
    /** 上下文窗口（token）。用来算"还能塞多少前文"，也决定什么时候压缩 */
    val contextWindow: Int = 128000,
    /** 这个模型能不能看图（部分接口返回的模型支持图像输入） */
    val supportsVision: Boolean = false,
    /** 当前选用的思考强度档位：off / low / high / max */
    var reasoningEffort: String = "off",
    /** 这个模型支持哪几档思考强度（添加模型时标出来），写作台只列这几档 */
    var supportedEfforts: MutableList<String> = mutableListOf("off"),
    /** 自动压缩阈值：上下文用量超过这个比例就开始压缩早期剧情，0.5~0.95 */
    var autoCompactAt: Float = 0.8f,
    /** 每个模型的独立参数；没单独设过的模型用上面的默认值 */
    var modelConfigs: MutableList<ModelConfig> = mutableListOf(),
    /** 订阅月额度（美元）：如 OpenCode Go 某条线 $100；0 = 没订阅/不统计剩余额度 */
    val monthlyQuotaUsd: Double = 0.0,
    /** 自定义单价（美元/百万 token），-1 = 用内置价格表。分开三项：输入未缓存 / 输入缓存命中 / 输出 */
    val priceInputPer1M: Double = -1.0,
    val priceCachedPer1M: Double = -1.0,
    val priceOutputPer1M: Double = -1.0,
    /** 是否启用；关掉的不出现在写作的切换列表里 */
    var enabled: Boolean = true,
)

/** 单个模型的独立参数。
 *  0 / 空 = 跟随所属服务商的默认值，这样只给特殊的模型单独设就行。 */
@Serializable
data class ModelConfig(
    var name: String = "",
    /** 上下文窗口 token；0 = 跟随服务商默认 */
    var contextWindow: Int = 0,
    var supportsVision: Boolean = false,
    /** 这个模型支持哪几档思考强度；空 = 用服务商的 */
    var supportedEfforts: MutableList<String> = mutableListOf(),
    /** 默认档位；空 = 用服务商的 */
    var reasoningEffort: String = "",
    /** 自动压缩阈值；0 = 用服务商的 */
    var autoCompactAt: Float = 0f,
    /** 单次输出上限；0 = 用服务商的 */
    var maxTokens: Int = 0,
    /** 是否显示在写作界面的切换列表里 */
    var enabled: Boolean = true
)

/** 章节 */
@Serializable
data class Chapter(
    val index: Int,                 // 从 1 开始
    var title: String = "",
    var outline: String = "",       // 本章细纲
    var content: String = "",       // 正文
    var done: Boolean = false
)

/** 一部小说项目 */
@Serializable
data class NovelProject(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "未命名",
    // —— 设定 ——
    var genre: String = "玄幻",          // 题材，见 Templates.genres
    var style: String = "爽文快节奏",     // 风格，见 Templates.styles
    var protagonist: String = "",        // 主角设定（姓名/身份/性格）
    var worldSetting: String = "",       // 世界观补充
    var goldenFinger: String = "",       // 金手指/核心设定，可空
    var perspective: String = "第三人称", // 第一人称 / 第三人称
    var chapterCount: Int = 10,
    var wordsPerChapter: Int = 2000,
    var extraRequirements: String = "",  // 其他自定义要求
    // —— 生成结果 ——
    var outline: String = "",            // 全书大纲
    var chapters: MutableList<Chapter> = mutableListOf(),
    /** 长程记忆库（跨对话互通的核心） */
    var memory: StoryMemory = StoryMemory(),
    // —— 用哪套供应商生成 ——
    var providerId: String = "",
    /** 这个项目实际用哪个模型；空 = 跟着 provider 的默认模型走 */
    var providerModel: String = "",
    /** 这个项目用的思考强度；空 = 跟着 provider 的默认档走 */
    var reasoningEffort: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val finishedChapters: Int get() = chapters.count { it.done }
    val totalWords: Int get() = chapters.sumOf { it.content.length }
}

/** 本地记下的更新记录（设置页看近 10 次） */
@Serializable
data class UpdateRecord(
    val versionName: String,
    val versionCode: Int,
    val time: String,
    val notes: List<String> = emptyList(),
    /** 是否已经装上去了 */
    val installed: Boolean = false
)

/** 全局设置 */
@Serializable
data class AppSettings(
    /** 分阶段模型路由：大纲阶段用哪套供应商（空 = 跟随作品自己的设置） */
    var outlineProviderId: String = "",
    /** 分阶段模型路由：正文阶段用哪套供应商（空 = 跟随作品自己的设置） */
    var bodyProviderId: String = "",
    /** 每一步失败时自动重试次数（0 = 不重试） */
    var autoRetry: Int = 2,
    /** 自定义系统提示词（空 = 用内置的作家角色提示词） */
    var customSystemPrompt: String = "",
    /** 美元兑人民币汇率（花费换算用，可改） */
    var usdToCny: Double = 7.2,
    /** 花费显示币种：USD / CNY（用量页可切换，选择会记住） */
    var displayCurrency: String = "USD",
    /** 上下文预算：每章生成时注入的记忆+前情最多多少字（超了按优先级裁剪） */
    var contextBudgetChars: Int = 6000,
    /** 记忆系统开关：关了就退回只带上一章结尾的老模式 */
    var memoryEnabled: Boolean = true,
    /** 触感反馈开关（全局设置里可关） */
    var hapticsEnabled: Boolean = true,
    /** 触感强度倍率：0.5 轻 / 1.0 标准 / 1.5 强 */
    var hapticsStrength: Float = 1.0f,
    // —— 阅读器偏好 ——
    var readerFontSize: Float = 17f,
    /** 行距倍数 */
    var readerLineHeight: Float = 1.85f,
    /** 阅读背景：paper / white / sepia / green / gray / dark */
    var readerTheme: String = "paper",
    /** 翻页方式：scroll 滚动 / chapter 章节横滑 / page 分页横滑 */
    var readerPageMode: String = "scroll",
    /** 护眼模式：叠加一层暖色滤镜，压蓝光 */
    var readerEyeCare: Boolean = false,
    /** 护眼强度 0..1（默认 0.18，很淡的暖黄） */
    var readerEyeCareStrength: Float = 0.18f,
    /**
     * 每本书读到哪了。key = "kind:bookId"，value = 章序号。
     * 章序号是「阅读器里的序号」（0 基），不是书里的章节号。
     */
    var readProgress: MutableMap<String, Int> = mutableMapOf(),
    // —— 更新 ——
    /** 更新服务地址；默认走 adb reverse 的本机端口，接 WiFi 时可改成电脑局域网地址 */
    var updateServerUrl: String = "http://127.0.0.1:8787",
    /** 近 10 次更新记录（新的在前） */
    var updateHistory: List<UpdateRecord> = emptyList()
)

/** 故事记忆库里的人物条目（跨章节、跨会话共享，随生成自动更新，也可手动改）。 */
@Serializable
data class MemoryCharacter(
    var name: String = "",
    var info: String = ""
)

/** 故事记忆库：一本书的长程记忆。
 *  每次生成章节是一次独立"对话"，靠这份记忆在对话之间互通：
 *  人物状态、已确立事实、未回收伏笔、前情摘要都存这里，生成下一章时按预算注入上下文。 */
@Serializable
data class StoryMemory(
    /** 早期剧情被压缩后的长程摘要（上下文压缩的产物） */
    var compressedSummary: String = "",
    /** 近期章节摘要（每章一条，攒多了会被压缩进 compressedSummary） */
    var recentSummaries: MutableList<String> = mutableListOf(),
    var characters: MutableList<MemoryCharacter> = mutableListOf(),
    /** 已确立的世界设定 / 关键事实 */
    var facts: MutableList<String> = mutableListOf(),
    /** 未回收的伏笔 / 悬念 */
    var threads: MutableList<String> = mutableListOf()
)

/** 一次模型调用的用量记录（本地计量：token、缓存命中、耗时、速度、花费）。
 *  只要接口按 OpenAI 格式返回 usage，这些数就是接口给的真实值；流式且接口没回 usage 时，
 *  输出 token 按字数估算并把 estimated 置 true。 */
@Serializable
data class UsageRecord(
    val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val providerId: String = "",
    val providerName: String = "",
    val model: String = "",
    /** 阶段：大纲 / 细纲 / 正文 / 重写 */
    val stage: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val chapterIndex: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    /** 输入里命中缓存的 token 数（命中率 = 它 / promptTokens） */
    val cachedTokens: Int = 0,
    /** 本次总耗时（毫秒） */
    val latencyMs: Long = 0,
    /** 首字延迟（毫秒，流式才有；非流式 = 总耗时） */
    val ttftMs: Long = 0,
    /** 本次花费（美元，按记录当时的价格快照算，之后改价不影响历史） */
    val costUsd: Double = 0.0,
    /** 是否定过价（没价的记录 costUsd 为 0 且此项为 false，统计里会标注） */
    val priced: Boolean = false,
    /** token 数是否为估算值 */
    val estimated: Boolean = false
) {
    val totalTokens: Int get() = promptTokens + completionTokens
    /** 输出速度 token/秒 */
    val tokensPerSecond: Double
        get() = if (latencyMs > 0) completionTokens * 1000.0 / latencyMs else 0.0
}

/** 从网上导入的整本书（爬取下载，和生成作品分开存）。 */
@Serializable
data class ImportedChapter(
    val index: Int,
    val title: String,
    var content: String = ""
)

/** 待下载章节的地址，随书一起存，续传用同一套地址 */
@Serializable
data class PendingChapter(
    val title: String = "",
    val url: String = ""
)

@Serializable
data class ImportedBook(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var author: String = "",
    var sourceUrl: String = "",
    var chapters: MutableList<ImportedChapter> = mutableListOf(),
    /** local = 本地文件，crawl = 在线书库 */
    var origin: String = "local",
    /** 这本书的完整章节地址表：未下完时留着，续传接着往下抓，不用重新搜 */
    var pendingChapters: List<PendingChapter> = emptyList(),
    /** 书源 id（qqxs / qbtr / generic） */
    var sourceId: String = "",
    /** 本地导入时源文件的键（路径或 uri）：扫描页靠它标「已入库」，老数据为空 */
    var sourceKey: String = "",
    /** 原文件名、格式（扩展名）、内容指纹（SHA-256）：三样都对上才算重复导入，老数据为空 */
    var sourceName: String = "",
    /** 内容指纹：章节标题+字数算出来的短哈希，判断是不是同一本书 */
    var fingerprint: String = "",
    var sourceExt: String = "",
    var sourceHash: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalWords: Int get() = chapters.sumOf { it.content.length }
}

/** 全 App 的持久化数据（一份 JSON 存本地） */
@Serializable
data class AppData(
    var providers: MutableList<ProviderConfig> = mutableListOf(),
    var projects: MutableList<NovelProject> = mutableListOf(),
    var activeProviderId: String = "",
    var settings: AppSettings = AppSettings(),
    var usage: MutableList<UsageRecord> = mutableListOf(),
    var importedBooks: MutableList<ImportedBook> = mutableListOf()
)
