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
    suspend fun download(
        serverUrl: String,
        entry: UpdateEntry,
        ctx: Context,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, entry.file.ifBlank { "update-${entry.versionName}.apk" })
        val url = entry.directUrl.ifBlank { base(serverUrl) + "/" + entry.file }
        val resp = http.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
        val body = resp.body ?: error("空响应")
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
        if (out.length() < 1024 * 100) error("下载的文件只有 ${out.length()} 字节，应该不是 APK")
        // 完整性校验：大小对不上或 SHA-256 对不上，多半是局域网传坏了，删掉重下
        if (entry.size > 0 && out.length() != entry.size) {
            val got = out.length()
            out.delete()
            error("下载不完整：大小对不上（$got / ${entry.size}），再试一次")
        }
        if (entry.sha256.isNotBlank()) {
            val actual = sha256Of(out)
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                out.delete()
                error("下载校验没过（SHA-256 对不上），可能是网络传坏了，再试一次")
            }
        }
        out
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

    private const val LATEST_URL =
        "https://api.github.com/repos/Hahakuaizai/WenForge/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    suspend fun fetchLatestDetailed(): GhResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            val req = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext GhResult.Failed("HTTP ${resp.code}")
                }
                resp.body?.string()
            }
        }.getOrElse { e ->
            return@withContext GhResult.Failed(e.message ?: e.javaClass.simpleName)
        } ?: return@withContext GhResult.Failed("响应为空")

        runCatching {
            val root = JSONObject(text)
            val versionName = root.optString("tag_name").removePrefix("v").removePrefix("V")
            if (versionName.isBlank()) return@withContext GhResult.NoRelease
            val assets = root.optJSONArray("assets") ?: return@withContext GhResult.NoRelease
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) { asset = a; break }
            }
            val apkAsset = asset ?: return@withContext GhResult.NoRelease
            val fileName = apkAsset.optString("name")
            // versionCode 编在附件名里（wenforge-v0.4.41-47.apk），读不出来就不用这条
            val code = Regex("""-(\d+)\.apk$""").find(fileName)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext GhResult.NoRelease
            val notes = root.optString("body")
                .lines()
                .map { it.trim().trimStart('#').trim().removePrefix("- ").removePrefix("* ").trim() }
                .filter { it.isNotBlank() }
                .take(12)
            GhResult.Ok(
                UpdateEntry(
                    versionName = versionName,
                    versionCode = code,
                    time = root.optString("published_at").take(16).replace('T', ' '),
                    notes = notes,
                    file = fileName,
                    size = apkAsset.optLong("size"),
                    sha256 = apkAsset.optString("digest").removePrefix("sha256:"),
                    directUrl = apkAsset.optString("browser_download_url")
                )
            )
        }.getOrElse { e ->
            GhResult.Failed("解析失败：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** 老接口：只关心「有没有可用的新版本」时用 */
    suspend fun fetchLatest(): UpdateEntry? =
        (fetchLatestDetailed() as? GhResult.Ok)?.entry
}
