package com.novelforge.app.net

import com.novelforge.app.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 账户余额查询结果（金额按接口返回的币种原样展示）。 */
data class BalanceInfo(
    val currency: String,
    val total: String,
    val detail: String
)

/** 各家官方余额接口适配。
 *  只有部分平台开放了余额查询；没有开放的（如 OpenAI、多数网关）会明确返回不支持，
 *  那种情况用量以 App 本地统计为准。 */
object BalanceClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(provider: ProviderConfig): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val base = provider.baseUrl.trimEnd('/')
            val host = base.lowercase()
            when {
                "deepseek" in host -> deepSeek(provider, base)
                "siliconflow" in host -> siliconFlow(provider, base)
                "moonshot" in host || "kimi" in host -> moonshot(provider, base)
                else -> error("这个接口没有开放余额查询，用量请看本地统计")
            }
        }
    }

    private fun get(provider: ProviderConfig, url: String): String {
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}：${body.take(150)}")
            return body
        }
    }

    /** DeepSeek：GET https://api.deepseek.com/user/balance（注意不在 /v1 下） */
    private fun deepSeek(provider: ProviderConfig, base: String): BalanceInfo {
        val root = base.removeSuffix("/v1")
        val body = get(provider, "$root/user/balance")
        val rootObj = json.parseToJsonElement(body).jsonObject
        val info = rootObj["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("接口没有返回余额信息")
        val currency = info["currency"]?.jsonPrimitive?.content ?: ""
        val total = info["total_balance"]?.jsonPrimitive?.content ?: "0"
        val granted = info["granted_balance"]?.jsonPrimitive?.content ?: "0"
        val topped = info["topped_up_balance"]?.jsonPrimitive?.content ?: "0"
        return BalanceInfo(currency, total, "充值余额 $topped · 赠送余额 $granted")
    }

    /** 硅基流动：GET https://api.siliconflow.cn/v1/user/info */
    private fun siliconFlow(provider: ProviderConfig, base: String): BalanceInfo {
        val body = get(provider, "$base/user/info")
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?: error("接口没有返回账户信息")
        val balance = data["balance"]?.jsonPrimitive?.content ?: "0"
        val total = data["totalBalance"]?.jsonPrimitive?.content ?: balance
        return BalanceInfo("CNY", balance, "累计充值 $total")
    }

    /** Moonshot：GET https://api.moonshot.cn/v1/users/me/balance */
    private fun moonshot(provider: ProviderConfig, base: String): BalanceInfo {
        val body = get(provider, "$base/users/me/balance")
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?: error("接口没有返回余额信息")
        val available = data["available_balance"]?.jsonPrimitive?.content ?: "0"
        val cash = data["cash_balance"]?.jsonPrimitive?.content ?: "0"
        val voucher = data["voucher_balance"]?.jsonPrimitive?.content ?: "0"
        return BalanceInfo("CNY", available, "现金 $cash · 代金券 $voucher")
    }
}
