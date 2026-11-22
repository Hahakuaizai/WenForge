package com.novelforge.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** 计价引擎：把 token 用量换算成美元花费。
 *
 *  价格来源优先级：供应商自定义单价（编辑页里填的）> 内置价格表 > 无价（记为未定价）。
 *  内置表是 2026-11 核对的官方价，平台改价后请在供应商编辑页填自定义价覆盖。 */
object Pricing {

    /** 每百万 token 的价格（美元）：输入未缓存 / 输入缓存命中 / 输出 */
    data class Price(val inputMiss: Double, val inputHit: Double, val output: Double, val peakAware: Boolean = false)

    private val table: List<Pair<List<String>, Price>> = listOf(
        // DeepSeek V4.1 Flash（deepseek-chat 现指向它）：谷时价，峰时翻倍
        listOf("deepseek-chat", "deepseek-flash", "v4.1-flash", "v4-flash") to
            Price(0.15, 0.003, 0.60, peakAware = true),
        // DeepSeek V4 Pro（deepseek-reasoner 现指向它）：谷时价，峰时翻倍
        listOf("deepseek-reasoner", "v4-pro") to
            Price(0.66, 0.022, 1.98, peakAware = true),
        listOf("gpt-4o-mini") to Price(0.15, 0.075, 0.60),
        listOf("gpt-4o") to Price(2.50, 1.25, 10.00)
    )

    /** DeepSeek 峰时：UTC 周一至周五 01:00–04:00、06:00–10:00（= 北京 9:00–12:00、14:00–18:00）。
     *  中国法定节假日官方算谷时，这里没做节假日表，节假日当天在 App 里会按峰时多算一点。 */
    fun isDeepSeekPeak(timeMs: Long): Boolean {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC)
        if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) return false
        val h = t.hour
        return h in 1..3 || h in 6..9
    }

    /** 解析某次调用实际用的单价；完全没价返回 null。 */
    fun resolve(provider: ProviderConfig, model: String, timeMs: Long): Price? {
        val m = model.lowercase()
        val base = table.firstOrNull { (keys, _) -> keys.any { m.contains(it) } }?.second
        val hasCustom = provider.priceInputPer1M >= 0 || provider.priceCachedPer1M >= 0 || provider.priceOutputPer1M >= 0
        if (base == null && !hasCustom) return null
        var price = Price(
            inputMiss = if (provider.priceInputPer1M >= 0) provider.priceInputPer1M else base?.inputMiss ?: 0.0,
            inputHit = if (provider.priceCachedPer1M >= 0) provider.priceCachedPer1M else base?.inputHit ?: 0.0,
            output = if (provider.priceOutputPer1M >= 0) provider.priceOutputPer1M else base?.output ?: 0.0,
            peakAware = base?.peakAware ?: false
        )
        if (price.peakAware && isDeepSeekPeak(timeMs)) {
            price = price.copy(
                inputMiss = price.inputMiss * 2,
                inputHit = price.inputHit * 2,
                output = price.output * 2
            )
        }
        return price
    }

    /** 计算一次调用的花费（美元）；没价返回 null。 */
    fun costUsd(
        provider: ProviderConfig,
        model: String,
        promptTokens: Int,
        cachedTokens: Int,
        completionTokens: Int,
        timeMs: Long
    ): Double? {
        val price = resolve(provider, model, timeMs) ?: return null
        val hit = cachedTokens.coerceIn(0, promptTokens)
        val miss = promptTokens - hit
        return (miss * price.inputMiss + hit * price.inputHit + completionTokens * price.output) / 1_000_000.0
    }
}
