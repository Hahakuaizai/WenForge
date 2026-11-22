package com.novelforge.app.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** 一条更新记录（版本号 + 改动说明） */
data class UpdateEntry(
    val versionName: String,
    val versionCode: Int,
    val time: String,
    val notes: List<String>,
    val file: String,
    val size: Long,
    /** APK 的 SHA-256（新版电脑端脚本会带上；旧 manifest 没有就为空，跳过校验） */
    val sha256: String = "",
    /** 直达下载地址（GitHub Release 的附件链接）；局域网条目为空，走服务器地址拼接 */
    val directUrl: String = ""
)

object UpdateClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun base(url: String): String = url.trim().trimEnd('/')

    /** 拉 manifest.json */
    suspend fun fetch(serverUrl: String): List<UpdateEntry> = withContext(Dispatchers.IO) {
        val url = base(serverUrl) + "/manifest.json"
        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        val text = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("服务器返回 ${resp.code}，先确认电脑上的更新服务开着")
            resp.body?.string() ?: error("空响应")
        }
        val root = JSONObject(text)
        val arr = root.optJSONArray("history") ?: error("manifest 格式不对")
        val out = mutableListOf<UpdateEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val notes = mutableListOf<String>()
            o.optJSONArray("notes")?.let { n ->
                for (j in 0 until n.length()) notes.add(n.getString(j))
            }
            out.add(
                UpdateEntry(
                    versionName = o.optString("versionName"),
                    versionCode = o.optInt("versionCode"),
                    time = o.optString("time"),
                    notes = notes,
                    file = o.optString("file"),
                    size = o.optLong("size"),
                    sha256 = o.optString("sha256")
                )
            )
        }
        out
    }

    /** 下载 apk 到 cache/updates */
    /**
     * GitHub Release 的附件直链在国内经常连不上：
     * github.com 返回 302，跳转到 objects.githubusercontent.com，然后卡死超时。
     * 所以按顺序试几个加速镜像，哪个能下完整就用哪个。
     */
    private val ghMirrors = listOf(
        // 实测（2026-11）手机端速度：ghfast 约 4.6MB/s，gh-proxy 约 3.9MB/s，
        // ghproxy.net / mirror.ghproxy.com 已经连不上。快的排前面。
        "https://ghfast.top/",
        "https://gh-proxy.com/"
    )

    /** 把 GitHub 原始链接套上镜像前缀 */
    private fun viaMirror(mirror: String, url: String) = mirror.trimEnd('/') + "/" + url

    /** 下载一个 URL 到 out，返回是否成功（失败会删掉半截文件） */
    private suspend fun tryFetch(
        url: String,
        out: File,
        expectSize: Long,
        expectSha: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = http.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) return@runCatching false
            val body = resp.body ?: return@runCatching false
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } > 0) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (out.length() < 1024 * 100) return@runCatching false
            // 大小校验留容差：GitHub 网页上的「17.7 MB」是四舍五入过的，
            // 和真实字节数能差几十 KB，严格相等会把好包判成坏包，无限重试。
            if (expectSize > 0) {
                val tolerance = (expectSize * 0.02).toLong().coerceAtLeast(64L * 1024)
                if (kotlin.math.abs(out.length() - expectSize) > tolerance) {
                    return@runCatching false
                }
            }
            if (expectSha.isNotBlank() &&
                !sha256Of(out).equals(expectSha, ignoreCase = true)
            ) return@runCatching false
            true
        }.getOrElse {
            false
        }.also { ok ->
            if (!ok) out.delete()
        }
    }

    /** 快速失败客户端：只用来试「这个地址通不通」，慢就直接换下一个 */
    private val quickHttp = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * 先快速探一下这个地址通不通（3 秒连不上就算不通），
     * 通了再用正式客户端下。避免在国内直连 GitHub 时白白等 30 秒超时。
     */
    private fun reachable(url: String): Boolean = runCatching {
        quickHttp.newCall(Request.Builder().url(url).head().build()).execute().use { r ->
            r.isSuccessful || r.code in 300..399
        }
    }.getOrDefault(false)

    suspend fun download(
        serverUrl: String,
        entry: UpdateEntry,
        ctx: Context,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, entry.file.ifBlank { "update-${entry.versionName}.apk" })

        // 局域网 / 自建服务器：直接拼地址，不走镜像
        val isGithub = entry.directUrl.contains("github.com") ||
            entry.directUrl.contains("githubusercontent.com")
        val candidates = when {
            entry.directUrl.isBlank() ->
                listOf(base(serverUrl) + "/" + entry.file)
            !isGithub ->
                listOf(entry.directUrl)
            else ->
                // GitHub 附件：镜像优先，直连放最后。
                // 原因：国内直连 github.com 会返回 302 然后卡在
                // objects.githubusercontent.com 上直到超时，白白浪费 30 秒。
                ghMirrors.map { viaMirror(it, entry.directUrl) } + entry.directUrl
        }

        // 先挑一个通的（探测很快，3 秒超时），探测全不通就按原顺序硬试
        val probe = candidates.firstOrNull { reachable(it) }
        val ordered = if (probe != null) listOf(probe) + candidates.filter { it != probe }
                      else candidates

        for (url in ordered) {
            val ok = tryFetch(url, out, entry.size, entry.sha256, onProgress)
            if (ok) return@withContext out
        }
        error("下载失败：${candidates.size} 个地址都没下成功，检查下网络")
    }

    private fun sha256Of(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } > 0) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 交给系统安装 */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** 能否安装未知来源（Android 8+） */
    fun canInstall(ctx: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 26)
            ctx.packageManager.canRequestPackageInstalls()
        else true

    fun askInstallPermission(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    /** 本机版本号 */
    fun installedVersion(ctx: Context): Pair<String, Int> {
        val pm = ctx.packageManager
        val info = pm.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else info.versionCode
        return info.versionName.orEmpty() to code
    }

    /** 探测共用一个短超时客户端（旧写法给 254 个地址各建一个 OkHttpClient，太浪费） */
    private val probeHttp = OkHttpClient.Builder()
        .connectTimeout(260, TimeUnit.MILLISECONDS)
        .readTimeout(400, TimeUnit.MILLISECONDS)
        .build()

    /** 扫局域网找 8787 端口上的更新服务 */
    suspend fun scanLan(port: Int = 8787): String? = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext null
        coroutineScope {
            val jobs = (1..254).map { host ->
                async {
                    val candidate = "http://$prefix.$host:$port"
                    if (isUpdateServer(candidate)) candidate else null
                }
            }
            jobs.awaitAll().firstOrNull { it != null }
        }
    }

    /** 是不是文枢的更新服务：先认 /ping 的身份，对不上再看 manifest（兼容旧版电脑脚本） */
    private fun isUpdateServer(base: String): Boolean {
        val ping = runCatching {
            probeHttp.newCall(Request.Builder().url("$base/ping").build()).execute().use { resp ->
                resp.isSuccessful && (resp.body?.string()?.contains("wenforge") == true)
            }
        }.getOrDefault(false)
        if (ping) return true
        return runCatching {
            probeHttp.newCall(Request.Builder().url("$base/manifest.json").build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun localPrefix(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<java.net.Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
                ?.split('.')
                ?.take(3)
                ?.joinToString(".")
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * GitHub Releases 更新渠道：查 Hahakuaizai/WenForge 的最新 Release，
 * 带 APK 附件就当成一个可直接下载安装的更新条目（不用打开 GitHub）。
 * 发版约定（scripts/release-github.ps1 保证）：附件名 wenforge-v<版本>-<versionCode>.apk，
 * Release 正文就是更新说明，附件 digest 带 SHA-256。
 */
object GithubUpdate {

    private const val REPO = "Hahakuaizai/WenForge"
    private const val UA = "Mozilla/5.0 (Linux; Android 14) WenForge"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // 关键：不跟随重定向。要靠 302 的 Location 头读出最新 tag，
        // 跟着跳过去就只剩一个 200 的 HTML 页面了。
        .followRedirects(false)
        .build()

    /** 查最新 Release；没有、没 APK、版本号读不出来都返回 null（当无更新处理） */
    /**
     * 查 GitHub 上最新的 Release。
     *
     * 三种结果要分开，界面才能说清楚是「网到不了 GitHub」还是「还没发过带 APK 的版本」：
     *  · ok        —— 查到了可用的版本
     *  · noRelease —— 请求成功，但没有可用的版本（没发布过 / 没有 apk 附件 / 附件名读不出 versionCode）
     *  · failed    —— 请求本身失败（网络到不了 GitHub、超时、403 限流）
     */
    sealed class GhResult {
        data class Ok(val entry: UpdateEntry) : GhResult()
        data object NoRelease : GhResult()
        data class Failed(val reason: String) : GhResult()
    }

    /**
     * 查最新 Release。
     *
     * 为什么不走 api.github.com：
     * 未认证的 GitHub API 每小时只有 60 次配额，用完了直接 403（rate limit），
     * 表现就是「检查更新失败」，而且一卡就是一小时。实测手机端就撞上过 remaining=0。
     *
     * 改用网页（不需要配额，也没有 UA 校验门槛）：
     *  1) GET /releases/latest  → 302，Location 里带 /releases/tag/v0.4.57
     *  2) GET /releases/expanded_assets/<tag> → HTML 片段，里面有 APK 文件名
     * 版本号从 tag 来，versionCode 从附件名（wenforge-v0.4.57-63.apk）里读。
     * 拿不到 SHA-256 就跳过校验，靠「文件大小 + 能装」兜底。
     */
    suspend fun fetchLatestDetailed(): GhResult = withContext(Dispatchers.IO) {
        // 1) 找最新 tag
        val tag = runCatching {
            val req = Request.Builder()
                .url("https://github.com/$REPO/releases/latest")
                .header("User-Agent", UA)
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code in 300..399) {
                    // Location: https://github.com/xxx/releases/tag/v0.4.57
                    (resp.header("Location") ?: "").substringAfterLast("/tag/", "")
                } else if (resp.code == 200) {
                    // 万一跟随了重定向，就从最终 URL 里抠 tag
                    resp.request.url.toString().substringAfterLast("/tag/", "")
                } else if (resp.code == 404) {
                    return@withContext GhResult.NoRelease
                } else {
                    return@withContext GhResult.Failed("HTTP ${resp.code}")
                }
            }
        }.getOrElse { e ->
            return@withContext GhResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (tag.isBlank()) return@withContext GhResult.NoRelease

        // 2) 拿附件列表（HTML 片段）
        val html = runCatching {
            val req = Request.Builder()
                .url("https://github.com/$REPO/releases/expanded_assets/$tag")
                .header("User-Agent", UA)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext GhResult.Failed("HTTP ${resp.code}")
                }
                resp.body?.string().orEmpty()
            }
        }.getOrElse { e ->
            return@withContext GhResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (html.isBlank()) return@withContext GhResult.Failed("附件列表为空")

        // /releases/download/v0.4.57/wenforge-v0.4.57-63.apk
        val fileName = Regex("""/releases/download/[^"\s]*/([^"\s]+\.apk)""")
            .find(html)?.groupValues?.get(1)
            ?: return@withContext GhResult.NoRelease

        val versionName = tag.removePrefix("v").removePrefix("V")
        val code = Regex("""-(\d+)\.apk$""").find(fileName)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: return@withContext GhResult.NoRelease

        // 文件大小从 HTML 里的「17.7 MB」读，只用于校验下载完整性
        val size = Regex("""([\d.]+)\s*(KB|MB|Bytes)""").find(html)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: 0.0
            when (m.groupValues[2]) {
                "KB" -> (v * 1024).toLong()
                "MB" -> (v * 1024 * 1024).toLong()
                else -> v.toLong()
            }
        } ?: 0L

        GhResult.Ok(
            UpdateEntry(
                versionName = versionName,
                versionCode = code,
                time = "",
                notes = emptyList(),
                file = fileName,
                size = size,
                sha256 = "",
                directUrl = "https://github.com/$REPO/releases/download/$tag/$fileName"
            )
        )
    }

    /** 老接口：只关心「有没有可用的新版本」时用 */
    suspend fun fetchLatest(): UpdateEntry? =
        (fetchLatestDetailed() as? GhResult.Ok)?.entry
}
