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
    val size: Long
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
                    size = o.optLong("size")
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
        val url = base(serverUrl) + "/" + entry.file
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
        out
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

    /** 扫局域网找 8787 端口上的更新服务 */
    suspend fun scanLan(port: Int = 8787): String? = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext null
        coroutineScope {
            val jobs = (1..254).map { host ->
                async {
                    val candidate = "http://$prefix.$host:$port"
                    val probe = OkHttpClient.Builder()
                        .connectTimeout(260, TimeUnit.MILLISECONDS)
                        .readTimeout(400, TimeUnit.MILLISECONDS)
                        .build()
                    val ok = runCatching {
                        probe.newCall(Request.Builder().url("$candidate/manifest.json").build())
                            .execute().use { it.isSuccessful }
                    }.getOrDefault(false)
                    if (ok) candidate else null
                }
            }
            jobs.awaitAll().firstOrNull { it != null }
        }
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
