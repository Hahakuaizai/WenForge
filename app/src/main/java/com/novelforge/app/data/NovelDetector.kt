package com.novelforge.app.data

/**
 * 判断一份文本「是不是小说」。
 *
 * 判定顺序（先否定，后肯定）：
 *  1. 文件名里写明「试卷/题库/劳动实践/大作业/复习资料」→ 直接不是
 *  2. 正文里有大量题号选项（A. B. C. D. / 一、二、三）→ 不是
 *  3. 小说必须同时有「书名 + 章节名 + 章节号」：
 *     开头几行像书名（短、无标点），后面能数出「第X章 / 第X卷 / Chapter N」
 *  4. 有章节结构才继续算分：对话密度、长段落、中文比例
 *
 * 关键是第 3 步 —— 没有章节结构的，不管正文多像散文，都不当小说。
 */
object NovelDetector {

    data class Verdict(
        val isNovel: Boolean,
        val score: Int,
        val reason: String
    )

    // 小说章节：第X章 / 第X回 / 第X卷 / 第X集 / 第X篇 / Chapter N
    // 刻意不含「节」「讲」「课」—— 那是教材/讲义的结构，小说几乎不用
    private val chapterLine = Regex(
        """^\s*(第\s*[0-9一二三四五六七八九十百千万零两]{1,8}\s*[章回卷集篇]|Chapter\s+\d+|CHAPTER\s+\d+).{0,40}$"""
    )

    // 教材/讲义结构：第X讲、第X节、第X课、第X单元、第X部分
    private val courseLine = Regex(
        """^\s*(第\s*[0-9一二三四五六七八九十百千万零两]{1,8}\s*[讲节课]|第\s*[0-9一二三四五六七八九十]{1,3}\s*(单元|部分)|Unit\s+\d+|Lesson\s+\d+).{0,40}$"""
    )

    // 分卷：第X卷 / 卷一 / 上部
    private val volumeLine = Regex(
        """^\s*(第\s*[0-9一二三四五六七八九十百千万零两]{1,8}\s*[卷部]|卷\s*[0-9一二三四五六七八九十]{1,4}|(上|中|下)部).{0,30}$"""
    )

    // 负分：试卷 / 课件 / 文档特征（正文里出现）
    private val examWords = listOf(
        "单项选择题", "多项选择题", "选择题", "填空题", "判断题", "简答题", "论述题",
        "计算题", "参考答案", "答案", "题库", "真题", "模拟题", "练习题", "习题",
        "一、", "二、", "三、", "四、", "五、",
        "考试", "试卷", "期中", "期末", "复习", "复习资料", "复习要点", "重点内容",
        "大纲", "提纲", "笔记", "知识点", "考点", "思维导图",
        "摘要", "关键词", "参考文献", "目录", "引言", "绪论", "实验报告",
        "成绩", "学分", "课程", "作业", "大作业", "劳动实践", "社会实践",
        "心得体会", "总结", "报告", "论文"
    )

    /** 文件名里出现这些词 —— 基本可以确定不是小说，直接判死 */
    private val titleNotNovel = listOf(
        // 作业 / 实践 / 课程类 —— 最常见的那批
        "大作业", "小作业", "劳动实践", "社会实践", "实践报告", "实践", "实训",
        "实验报告", "实验", "实习", "心得体会", "体会", "感想", "总结", "自我鉴定",
        "思想汇报", "汇报", "周记", "日记本", "读书笔记", "读书报告",
        "试卷", "答案", "参考答案", "题库", "真题", "模拟题", "练习题", "习题",
        "笔记", "课堂笔记", "复习", "复习资料", "复习要点", "提纲", "大纲",
        "知识点", "考点", "思维导图", "速成", "必背", "刷题", "大题", "客观题",
        "论文", "文献", "综述", "开题报告", "调查报告", "研究报告", "调研",
        "课件", "讲义", "教案", "ppt", "PPT", "作业",
        "简历", "合同", "协议", "通知", "公告", "方案", "策划",
        "计划", "清单", "表格", "数据", "统计", "报表", "台账", "记录",
        "说明书", "手册", "规范", "标准", "流程", "制度", "条例", "办法",
        "章程", "会议纪要", "纪要", "申请", "审批", "报销", "发票",
        "日志", "代码", "配置", "接口", "文档", "帮助", "教程", "安装",
        "成绩单", "课表", "培养方案", "招生", "报名", "志愿"
    )

    /** 文件名里出现这些词 —— 很像小说，加分 */
    private val titleLikelyNovel = listOf(
        "小说", "全集", "完结", "连载", "番外", "外传", "前传", "续集",
        "长篇", "中篇", "短篇", "文集", "作品集", "合集",
        "第一卷", "第二卷", "卷一", "卷二", "正文"
    )

    // 行首题号：A. / (1) / 1. 
    private val optionLine = Regex("""^\s*(?:[A-D][\.、．\)]|\(\d+\)|\d+[\.、．]\s)""")
    // 行内选项：docx 抽出来后 A. B. C. D. 会挤在同一行
    private val inlineOption = Regex("""[ABCD][\.、．]\s*[\u4e00-\u9fa5]""")
    private val codeish = Regex("""[{}<>;=]\s*$|[a-zA-Z_]{6,}\(|function |import |class |def |return """)

    /**
     * @param text 文件正文（读前 64KB 就够）
     * @param fileName 文件名，含扩展名也可以。用来先做标题判断
     */
    fun judge(text: String, fileName: String = ""): Verdict {
        val name = fileName.substringBeforeLast('.').trim()
        val t = text.take(120_000)

        // —— 0. 文件名先判死 ——
        val badTitle = titleNotNovel.firstOrNull { name.contains(it) }
        if (badTitle != null) {
            return Verdict(false, -100, "标题像「$badTitle」，不是小说")
        }

        if (t.isBlank()) return Verdict(false, 0, "读不到内容")

        val lines = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Verdict(false, 0, "没有正文")
        val body = t.replace(Regex("\\s"), "")
        val n = body.length.coerceAtLeast(1)

        // —— 1. 正文里的题目特征：命中就直接不是 ——
        val inlineOpts = inlineOption.findAll(t).count()
        if (inlineOpts >= 30) {
            return Verdict(false, -100, "正文有${inlineOpts}处选项（A. B. C. D.），像题库")
        }
        val optionLines = lines.count { optionLine.containsMatchIn(it) }
        val optionRate = optionLines.toDouble() / lines.size
        if (optionRate > 0.18) {
            return Verdict(false, -100, "大量题号行，像试卷/题库")
        }
        val examHits = examWords.count { w -> t.contains(w) }
        if (examHits >= 6) {
            return Verdict(false, -100, "试卷/复习资料特征明显")
        }

        // —— 2. 章节结构：小说必须有 ——
        val chapterHits = lines.count { chapterLine.matches(it) }
        val volumeHits = lines.count { volumeLine.matches(it) }
        val courseHits = lines.count { courseLine.matches(it) }
        val chapterRate = chapterHits.toDouble() / lines.size

        var score = 0
        val notes = mutableListOf<String>()

        // 「第X讲/第X节/第X课」多而「第X章」少 → 教材讲义，不是小说
        if (courseHits >= 3 && courseHits > chapterHits) {
            return Verdict(false, -100, "是教材/讲义结构（第X讲、第X节）")
        }

        if (chapterHits == 0 && volumeHits == 0) {
            return Verdict(false, -100, "没有章节名/章节号，不像小说")
        }

        when {
            chapterHits >= 10 -> {
                score += 50
                notes.add("${chapterHits}章")
            }
            chapterHits >= 3 -> {
                score += 40
                notes.add("${chapterHits}章")
            }
            chapterHits >= 1 -> {
                score += 20
                notes.add("有章节名")
            }
        }
        if (volumeHits > 0) {
            score += 10
            notes.add("${volumeHits}卷")
        }
        // 一半以上都是「第X章」→ 目录页，不是正文
        if (chapterRate > 0.4) {
            score -= 25
            notes.add("疑似目录")
        }

        // —— 3. 书名：开头几行里有没有像书名的短行 ——
        val head = lines.take(6)
        val titleLine = head.firstOrNull {
            it.length in 2..24 &&
                !chapterLine.matches(it) &&
                !it.endsWith("。") && !it.endsWith("，") &&
                !it.contains("作者") && !it.contains("简介")
        }
        val goodTitle = titleLikelyNovel.firstOrNull { name.contains(it) }
        val hasBookMark = name.contains('《') || name.contains('》') || t.take(500).contains('《')
        if (goodTitle != null) {
            score += 20
            notes.add("标题像小说")
        } else if (hasBookMark) {
            score += 12
            notes.add("书名号")
        } else if (titleLine != null) {
            score += 10
            notes.add("开头有书名行")
        }

        // —— 4. 对话密度 ——
        val quotes = t.count { it == '“' || it == '”' || it == '「' || it == '」' } / 2
        val quoteRate = quotes.toDouble() / n * 1000
        when {
            quoteRate > 4 -> {
                score += 25
                notes.add("对话密集")
            }
            quoteRate > 1.5 -> {
                score += 15
                notes.add("有对话")
            }
            quoteRate > 0.4 -> score += 6
        }

        // —— 5. 叙述段落 ——
        val longParas = lines.count { it.length > 40 }
        val paraRate = longParas.toDouble() / lines.size
        if (paraRate > 0.35 && n > 3000) {
            score += 12
            notes.add("长段落叙述")
        }
        val avgLen = lines.sumOf { it.length }.toDouble() / lines.size
        if (avgLen < 12) {
            score -= 15
            notes.add("短行过多")
        }

        // —— 6. 中文正文比例 ——
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        val cjkRate = cjk.toDouble() / n
        when {
            cjkRate > 0.75 -> score += 12
            cjkRate > 0.5 -> score += 6
            cjkRate < 0.3 -> {
                score -= 25
                notes.add("中文太少")
            }
        }

        // —— 7. 零散负分 ——
        if (examHits >= 3) {
            score -= 30
            notes.add("试卷/文档特征")
        } else if (examHits >= 2) {
            score -= 12
        }
        val codeLines = lines.count { codeish.containsMatchIn(it) }
        if (codeLines.toDouble() / lines.size > 0.15) {
            score -= 30
            notes.add("像代码")
        }
        if (n < 2000) {
            score -= 20
            notes.add("内容太短")
        }

        val isNovel = score >= 45
        val reason = when {
            isNovel -> notes.joinToString("、").ifBlank { "像小说正文" }
            notes.isNotEmpty() -> notes.joinToString("、")
            else -> "不像小说"
        }
        return Verdict(isNovel, score, reason)
    }
}
