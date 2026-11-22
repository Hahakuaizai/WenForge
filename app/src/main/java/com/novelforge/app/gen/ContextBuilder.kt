package com.novelforge.app.gen

import com.novelforge.app.data.Chapter
import com.novelforge.app.data.NovelProject

/** 上下文管理：把记忆库 + 前情 + 上一章结尾组装成一段可控大小的上下文，注入下一章的生成请求。
*
* 这是"不同对话互通"的关键：每次章节生成都是一次独立 API 对话，
* 靠这段组装好的上下文把之前所有对话积累的信息带进去。
* 超出预算时按优先级裁剪：先丢最旧的近期摘要，再缩上一章结尾，最后才动事实和人物。 */
object ContextBuilder {

/** 粗估 token 数（中文约 0.7 token/字），给 UI 预览和预算判断用。 */
fun estimateTokens(text: String): Int = (text.length * 0.7).toInt()

fun assemble(
project: NovelProject,
chapter: Chapter,
prevTail: String,
budgetChars: Int,
memoryEnabled: Boolean
): String {
if (!memoryEnabled) {
return if (prevTail.isNotBlank()) "\n$prevTail" else ""
}
val mem = project.memory

val characters = mem.characters.filter { it.name.isNotBlank()}
.map { "${it.name}：${it.info}"}.toMutableList()
val facts = mem.facts.toMutableList()
val threads = mem.threads.toMutableList()
val recents = mem.recentSummaries.toMutableList()
var prev = prevTail

fun build(): String {
val sb = StringBuilder()
if (characters.isNotEmpty()) sb.append("\n").append(characters.joinToString("\n")).append("\n\n")
if (facts.isNotEmpty()) sb.append("\n").append(facts.joinToString("\n") { "· $it"}).append("\n\n")
if (threads.isNotEmpty()) sb.append("\n").append(threads.joinToString("\n") { "· $it"}).append("\n\n")
val storySoFar = buildString {
if (mem.compressedSummary.isNotBlank()) append(mem.compressedSummary).append("\n")
append(recents.joinToString("\n"))
}.trim()
if (storySoFar.isNotBlank()) sb.append("\n").append(storySoFar).append("\n\n")
if (prev.isNotBlank()) sb.append("\n").append(prev)
return sb.toString().trim()
}

var text = build()
// 裁剪顺序：旧摘要 → 上一章结尾 → 事实 → 人物
while (text.length > budgetChars && recents.size > 1) {
recents.removeAt(0)
text = build()
}
if (text.length > budgetChars && prev.length > 300) {
prev = prev.take(300)
text = build()
}
if (text.length > budgetChars) {
prev = ""
text = build()
}
while (text.length > budgetChars && facts.size > 3) {
facts.removeAt(facts.size - 1)
text = build()
}
while (text.length > budgetChars && characters.size > 3) {
characters.removeAt(characters.size - 1)
text = build()
}
if (text.length > budgetChars) text = text.take(budgetChars)
return text
}
}
