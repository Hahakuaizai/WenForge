package com.novelforge.app.data

/** 内置设定库：题材 × 风格 × 视角，点选组合即可。
* 每条都带一句给模型的「写法指引」，拼提示词时用。 */
object Templates {

data class Option(val name: String, val guide: String)

val genres = listOf(
Option("玄幻", "修炼体系清晰、境界分明，战斗场面要有画面感和压迫感"),
Option("都市", "贴近现实生活，场景和对白接地气，冲突来自人际与利益"),
Option("言情", "情感线细腻，心理描写丰富，拉扯感和心动瞬间要写足"),
Option("科幻", "设定要有科学逻辑支撑，突出未知探索感和技术带来的伦理冲突"),
Option("悬疑", "线索层层递进，每章埋钩子，信息释放节奏严格控制"),
Option("历史", "服从时代背景与礼制细节，人物言行符合所处年代"),
Option("仙侠", "意境飘逸，注重道心与情感羁绊，文笔可偏古典"),
Option("末世", "资源稀缺与人性考验并重，生存压迫感贯穿始终"),
Option("游戏", "数值与规则明确，升级反馈强，副本与竞技节奏紧凑"),
Option("轻小说", "对话驱动，吐槽与日常感强，角色个性鲜明讨喜")
)

val styles = listOf(
Option("爽文快节奏", "节奏快、打脸爽点密集，每章至少一个小高潮，结尾留强钩子"),
Option("文学细腻", "文笔优美，注重意象与留白，节奏舒缓但情绪饱满"),
Option("轻松幽默", "语言诙谐，对话带梗，整体氛围轻松治愈"),
Option("暗黑压抑", "氛围沉重，善恶界限模糊，冲突残酷且代价真实"),
Option("热血燃系", "情绪高昂，战斗与成长线并行，关键场面要有爆发感"),
Option("慢热种田", "重日常经营与积累，温馨治愈，冲突柔和但不断线")
)

val perspectives = listOf("第三人称", "第一人称")

fun genreGuide(name: String): String = genres.firstOrNull { it.name == name}?.guide?: ""
fun styleGuide(name: String): String = styles.firstOrNull { it.name == name}?.guide?: ""

/** 供应商预置：点一下把地址/示例模型填好，只需补 Key。 */
data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val model: String,
    val note: String = "",
    val group: String = "服务商",
    val keyHint: String = ""
)

/** 思考强度档位：键 = 接口里的值，值 = 界面文字 */
val effortLevels = listOf(
    "off" to "关闭",
    "low" to "低",
    "high" to "高",
    "max" to "最高"
)

/** 按 group 分组的顺序 */
val providerPresetGroups = listOf("订阅网关", "国内直连", "海外服务", "本地运行")

val providerPresets = listOf(
    ProviderPreset("Magpie 网关", "", "",
        "电脑上的订阅反代，地址填 http://电脑IP:3425/v1", "订阅网关"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat",
        "中文文笔好，价格最低", "国内直连", "sk-"),
    ProviderPreset("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-128k",
        "超长上下文，适合长篇", "国内直连", "sk-"),
    ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus",
        "国产，有免费额度", "国内直连", ""),
    ProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
        "阿里云，稳定", "国内直连", "sk-"),
    ProviderPreset("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3",
        "国内中转，模型多", "国内直连", "sk-"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o",
        "逻辑强，价格高", "海外服务", "sk-"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "",
        "一个 Key 通吃各家模型", "海外服务", "sk-or-"),
    ProviderPreset("本地 Ollama", "http://127.0.0.1:11434/v1", "qwen2.5:14b",
        "本机跑，不走网络", "本地运行", "任意")
)

/** 设定摘要：拼进每个提示词，让模型全程不跑偏。 */
fun settingSummary(p: NovelProject): String = buildString {
appendLine("题材：${p.genre}（${genreGuide(p.genre)}）")
appendLine("风格：${p.style}（${styleGuide(p.style)}）")
appendLine("叙事视角：${p.perspective}")
if (p.protagonist.isNotBlank()) appendLine("主角设定：${p.protagonist}")
if (p.worldSetting.isNotBlank()) appendLine("世界观：${p.worldSetting}")
if (p.goldenFinger.isNotBlank()) appendLine("核心设定/金手指：${p.goldenFinger}")
if (p.extraRequirements.isNotBlank()) appendLine("额外要求：${p.extraRequirements}")
appendLine("全书计划 ${p.chapterCount} 章，每章约 ${p.wordsPerChapter} 字")
}
}

/** 提示词拼装：三级流水线（大纲 → 分章细纲 → 正文）各自的提示词。 */
object Prompts {

private const val WRITER_ROLE =
"你是一位经验丰富的网络小说作家，擅长长篇连载，文风稳定，能严格遵守给定设定。无论推理还是正文，一律使用中文。"

fun outline(p: NovelProject): List<Pair<String, String>> = listOf(
"system" to WRITER_ROLE,
"user" to """
|请根据以下设定，为一部长篇小说创作全书大纲。
|
|${Templates.settingSummary(p)}
|要求：
|1. 给出书名建议（一个即可）
|2. 用 300 字左右概括核心卖点与主线
|3. 介绍主要人物（每人 2~3 句：身份、性格、与主角关系）
|4. 按卷/阶段划分剧情走向，写清每一阶段的目标、主要冲突与转折
|5. 写明结局方向
|只输出大纲本身，不要解释，不要寒暄。
""".trimMargin()
)

fun appendOutlines(p: NovelProject, from: Int, to: Int): List<Pair<String, String>> {
    val recent = p.chapters.takeLast(3).map { c ->
        "第${c.index}章《${c.title}》：${c.outline.take(120)}"
    }.joinToString(separator = " ｜ ")
    return listOf(
        "system" to WRITER_ROLE,
        "user" to """
|《${p.title}》已经写到第 ${from - 1} 章，现在要接着往下写。
|请规划第 $from 到第 $to 章的细纲，人物、设定、节奏都要和前文接得上。
|
|全书大纲（节选）：
|${p.outline.take(2000)}
|
|最近三章：$recent
|
|要求：每章一行，格式「第N章|标题|细纲（60~120字，写清这一章发生什么、结尾留什么钩子）」
|只输出这些行，不要解释，不要寒暄。
""".trimMargin()
    )
}

fun chapterOutlines(p: NovelProject): List<Pair<String, String>> = listOf(
"system" to WRITER_ROLE,
"user" to """
|这是小说《${p.title}》的设定与全书大纲：
|
|${Templates.settingSummary(p)}
|
|${p.outline}
|
|请把全书拆成 ${p.chapterCount} 章，逐章给出细纲。严格按以下格式输出，每章一块，不要多余文字：
|第1章|章节标题|本章细纲（80~150字：出场人物、主要事件、冲突、结尾钩子）
|第2章|章节标题|本章细纲
|……直到第${p.chapterCount}章
""".trimMargin()
)

fun chapter(p: NovelProject, chapter: Chapter, contextBlock: String): List<Pair<String, String>> = listOf(
"system" to WRITER_ROLE,
"user" to """
|正在写小说《${p.title}》的第 ${chapter.index} 章。
|
|${Templates.settingSummary(p)}
|
|【全书大纲（节选）】
|${p.outline.take(1500)}
|
|【本章细纲】
|${chapter.outline}
|${if (contextBlock.isNotBlank()) "\n【记忆与前情（务必与之保持一致，不得矛盾）】\n$contextBlock\n" else ""}
|要求：
|1. 标题：${chapter.title}（第一行只写标题本身）
|2. 正文约 ${p.wordsPerChapter} 字，上下浮动不超过 20%
|3. 严格按本章细纲推进，人物言行与记忆、设定一致
|4. 结尾留一个钩子，让人想看下一章
|5. 只输出标题和正文，不要任何解释
""".trimMargin()
)

/** 章节写完后抽取记忆：摘要 + 人物状态 + 新事实 + 伏笔，结构化行格式便于程序解析。 */
fun memoryUpdate(p: NovelProject, chapter: Chapter): List<Pair<String, String>> = listOf(
"system" to "你是一个严谨的故事档案员，只做事实抽取，不做评论。",
"user" to """
|下面是小说《${p.title}》第 ${chapter.index} 章《${chapter.title}》的正文。请抽取需要记入长程记忆库的信息，严格按以下格式逐行输出，没有的类别就不输出：
|摘要|（用 100~180 字概括本章剧情：谁做了什么、结果如何、局势变化）
|人物|人物名|该人物在本章后的最新状态（身份/实力/关系/伤势/持有物等变化，一句话）
|事实|本章新确立、后文不能矛盾的一条设定或事实（一行一条）
|伏笔|本章新埋下的悬念或线索（一行一条）；若本章回收了旧伏笔，写成：回收|伏笔内容的关键词
|
|正文：
|${chapter.content.take(6000)}
""".trimMargin()
)

/** 上下文压缩：把一批旧章节摘要压成一份更短的长程摘要。 */
fun compress(p: NovelProject, oldText: String): List<Pair<String, String>> = listOf(
"system" to "你是一个严谨的故事档案员，擅长把长篇剧情压缩成准确的梗概。",
"user" to """
|下面是小说《${p.title}》较早部分的剧情摘要（可能包含一份已有的长程摘要和若干章节摘要）。
|请把它们合并压缩成一份 600 字以内的连贯梗概：保留主线进展、关键转折、人物关系与状态、未回收的伏笔；删掉细节描写和次要事件。
|只输出梗概本身。
|
|$oldText
""".trimMargin()
)
}
