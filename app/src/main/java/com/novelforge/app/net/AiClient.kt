package com.novelforge.app.net

import com.novelforge.app.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

/** 一次调用的计量数据（token、缓存命中、耗时）。 */
data class CallMetrics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val latencyMs: Long = 0,
    val ttftMs: Long = 0,
    val estimated: Boolean = false
)

data class CallResult(val text: String, val metrics: CallMetrics, val reasoning: String = "")

/** OpenAI 兼容接口客户端。
 *  POST {baseUrl}/chat/completions —— DeepSeek / OpenAI / 各类中转 / Magpie 网关都吃这一套。
 *  每次成功的 chat 调用都会把计量数据交给 onCall（由上层落成用量记录）。 */
class AiClient(private var provider: ProviderConfig) {

    /** 生成途中换了模型时，就地换掉这套配置，下一个请求立刻生效 */
    fun updateProvider(p: ProviderConfig) {
        provider = p
    }

    /** 计量回调：上层（Generator/ViewModel）设置，负责打阶段标签并落盘。 */
    var onCall: ((CallMetrics) -> Unit)? = null

    val providerConfig: ProviderConfig get() = provider

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)   // 写整章正文可能很久，给足 5 分钟
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun endpoint(path: String): String =
        provider.baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun auth(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", "Bearer ${provider.apiKey}")

    /** 测试连接：GET /models，能通且密钥被接受即算成功。 */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = auth(Request.Builder().url(endpoint("models"))).get().build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val body = resp.body?.string().orEmpty()
                        val count = runCatching {
                            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.size
                        }.getOrNull()
                        if (count != null) "连接成功，该接口下有 $count 个模型" else "连接成功"
                    }
                    resp.code == 401 || resp.code == 403 ->
                        error("密钥被拒绝（HTTP ${resp.code}），检查 API Key 是否正确")
                    resp.code == 404 ->
                        error("接口返回 404：多半是接口地址不对（要填到 /v1 这一层）")
                    else -> error("HTTP ${resp.code}：${resp.body?.string()?.take(200)}")
                }
            }
        }
    }

    /** 拉取该接口下的模型 id 列表（设置页里点选模型用）。 */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = auth(Request.Builder().url(endpoint("models"))).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?: error("接口没有返回模型列表")
                data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                    .filter { it.isNotBlank() }
                    .sorted()
            }
        }
    }

    private fun payload(messages: List<ChatMessage>, stream: Boolean) = buildJsonObject {
        put("model", provider.model)
        put("temperature", provider.temperature)
        put("max_tokens", provider.maxTokens)
        // 思考强度：只在实际开启时才传，免得不支持的接口报 400
        if (provider.reasoningEffort.isNotBlank() && provider.reasoningEffort != "off") {
            // 接口只认 low/medium/high，"最高" 落到 high
            put("reasoning_effort", if (provider.reasoningEffort == "max") "high" else provider.reasoningEffort)
        }
        if (stream) {
            put("stream", true)
            // 要接口在流结束时把 usage 一起发回来（DeepSeek/OpenAI 都支持）
            putJsonObject("stream_options") { put("include_usage", true) }
        }
        putJsonArray("messages") {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        }
    }

    /** 从 usage 对象里解析 token 数，兼容 DeepSeek 与 OpenAI 两种缓存字段。 */
    private fun parseUsage(usage: JsonObject?): CallMetrics {
        if (usage == null) return CallMetrics()
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val completion = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val cached = usage["prompt_cache_hit_tokens"]?.jsonPrimitive?.intOrNull
            ?: usage["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull
            ?: 0
        return CallMetrics(promptTokens = prompt, completionTokens = completion, cachedTokens = cached)
    }

    /** 一次对话补全（非流式），返回文本 + 计量。 */
    suspend fun chat(messages: List<ChatMessage>): Result<CallResult> = withContext(Dispatchers.IO) {
        runCatching {
            val started = System.currentTimeMillis()
            val req = auth(Request.Builder().url(endpoint("chat/completions")))
                .header("Content-Type", "application/json")
                .post(payload(messages, stream = false).toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code}：${body.take(300)}")
                val root = json.parseToJsonElement(body).jsonObject
                val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
                val text = content?.takeIf { it.isNotBlank() } ?: error("接口返回了空内容：${body.take(300)}")
                val latency = System.currentTimeMillis() - started
                val metrics = parseUsage(root["usage"]?.jsonObject).copy(latencyMs = latency, ttftMs = latency)
                onCall?.invoke(metrics)
                CallResult(text, metrics)
            }
        }
    }

    /** 流式补全：每收到一段就回调 onDelta（参数是已生成的总字数），最后返回全文 + 计量。
     *  接口不支持流式时会抛错，调用方可以回退到 chat()。
     *  接口没回 usage 时，输出 token 按字数估算并标注 estimated。 */
    suspend fun chatStream(
        messages: List<ChatMessage>,
        onDelta: (Int) -> Unit,
        onReasoning: (String) -> Unit = {}
    ): Result<CallResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val started = System.currentTimeMillis()
                var firstDeltaAt = 0L
                val req = auth(Request.Builder().url(endpoint("chat/completions")))
                    .header("Content-Type", "application/json")
                    .post(payload(messages, stream = true).toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}：${resp.body?.string()?.take(300)}")
                    }
                    val source = resp.body?.source() ?: error("空响应")
                    val sb = StringBuilder()
                    val reasoningText = StringBuilder()
                    var lastReasoningReport = 0
                    var lastReport = 0
                    var usage: JsonObject? = null
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                        chunk["usage"]?.let { if (it is JsonObject) usage = it }
                        // 思考内容：DeepSeek / 中转常用 reasoning_content，也有用 reasoning 的
                        val reasoningDelta = runCatching {
                            chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.let { d ->
                                    d["reasoning_content"]?.jsonPrimitive?.content
                                        ?: d["reasoning"]?.jsonPrimitive?.content
                                }
                        }.getOrNull()
                        if (!reasoningDelta.isNullOrEmpty()) {
                            reasoningText.append(reasoningDelta)
                            if (reasoningText.length - lastReasoningReport >= 200) {
                                lastReasoningReport = reasoningText.length
                                onReasoning(reasoningText.toString())
                            }
                        }
                        val delta = runCatching {
                            chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.content
                        }.getOrNull()
                        if (!delta.isNullOrEmpty()) {
                            if (firstDeltaAt == 0L) firstDeltaAt = System.currentTimeMillis()
                            sb.append(delta)
                            if (sb.length - lastReport >= 400) {
                                lastReport = sb.length
                                onDelta(sb.length)
                            }
                        }
                    }
                    val text = sb.toString()
                    if (text.isBlank()) {
                        error(
                            if (reasoningText.isNotEmpty()) {
                                "这个模型是思考型的，输出额度全用在思考上了" +
                                    "（思考 ${reasoningText.length} 字，正文 0 字）。" +
                                    "把它在「模型设置」里的输出上限调到 8192 以上，或者换个不带思考的模型"
                            } else {
                                "接口返回了空内容"
                            }
                        )
                    }
                    onDelta(sb.length)
                    val latency = System.currentTimeMillis() - started
                    var metrics = parseUsage(usage)
                    metrics = if (metrics.completionTokens > 0) {
                        metrics.copy(latencyMs = latency, ttftMs = if (firstDeltaAt > 0) firstDeltaAt - started else latency)
                    } else {
                        // 没拿到 usage：输出 token 按中文约 0.7 token/字估算
                        metrics.copy(
                            completionTokens = (sb.length * 0.7).toInt().coerceAtLeast(1),
                            latencyMs = latency,
                            ttftMs = if (firstDeltaAt > 0) firstDeltaAt - started else latency,
                            estimated = true
                        )
                    }
                    onCall?.invoke(metrics)
                    if (reasoningText.isNotEmpty()) onReasoning(reasoningText.toString())
                    CallResult(text, metrics, reasoningText.toString())
                }
            }
        }
}
