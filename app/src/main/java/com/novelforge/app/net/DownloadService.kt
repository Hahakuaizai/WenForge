package com.novelforge.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.novelforge.app.MainActivity
import com.novelforge.app.R
import com.novelforge.app.data.ImportedChapter
import com.novelforge.app.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 下载进度（进程内共享，界面直接读） */
data class DownloadState(
    val bookId: String,
    val title: String,
    val done: Int,
    val total: Int,
    val running: Boolean,
    val message: String = ""
)

object DownloadHub {
    var state by mutableStateOf<DownloadState?>(null)
        internal set
}

/**
 * 整本下载放在前台服务里跑：
 * 退出界面、切到别的 App、息屏都继续，只有用户点「停止」或系统杀掉进程才停。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                job?.cancel()
                job = null
                DownloadHub.state = DownloadHub.state?.copy(running = false, message = "已停止")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return START_NOT_STICKY
                startForeground(NOTIF_ID, buildNotification("准备下载…", "", 0, 0))
                job?.cancel()
                job = scope.launch { runDownload(bookId) }
            }
        }
        return START_REDELIVER_INTENT
    }

    private suspend fun runDownload(bookId: String) {
        val store = Store.shared(this)
        val book = store.data.importedBooks.firstOrNull { it.id == bookId } ?: run {
            DownloadHub.state = DownloadState(bookId, "", 0, 0, false, "书不见了")
            finishService()
            return
        }
        val refs = book.pendingChapters
        if (refs.isEmpty()) {
            // 已经没有待下载的部分（早就下完了）
            DownloadHub.state = DownloadState(bookId, book.title, book.chapters.size, book.chapters.size, false, "")
            finishService()
            return
        }
        val total = refs.size
        var failures = 0
        DownloadHub.state = DownloadState(bookId, book.title, book.chapters.size, total, true)

        try {
            var i = book.chapters.size
            while (i < total) {
                val ref = refs[i]
                var text = runCatching { Novels.chapter(ref.url, this) }.getOrElse { "" }
                if (text.length < 80) {
                    delay(450)
                    val retry = runCatching { Novels.chapter(ref.url, this) }.getOrElse { "" }
                    if (retry.length > text.length) text = retry
                }
                if (text.isBlank()) {
                    failures++
                    text = "（本章未能下载）"
                }
                val title = ref.title.trim().let {
                    if (it.isBlank() || it.matches(Regex("""\d{1,5}""")) || it == "开始阅读") "第${i + 1}章" else it
                }
                book.chapters.add(ImportedChapter(i + 1, title, text))
                i++

                DownloadHub.state = DownloadState(bookId, book.title, i, total, true)
                if (book.chapters.size % 10 == 0) store.saveNow()
                if (i % 5 == 0) notify(buildNotification(book.title, "$i / $total", i, total))
                delay(120)
            }
            // 下完了，把地址表清掉，未下完列表里就不显示它了
            book.pendingChapters = emptyList()
            store.saveNow()
            DownloadHub.state = DownloadState(
                bookId, book.title, book.chapters.size, total, false,
                if (failures > 0) "下载完成，$failures 章失败" else "下载完成"
            )
            notify(buildNotification(book.title, "下载完成 ${book.chapters.size} 章", total, total))
        } catch (e: CancellationException) {
            // 停的时候保留进度和地址表，随时能接着下
            store.saveNow()
            DownloadHub.state = DownloadState(
                bookId, book.title, book.chapters.size, total, false, "已暂停，已下的留在书架"
            )
        } catch (e: Exception) {
            store.saveNow()
            DownloadHub.state = DownloadState(
                bookId, book.title, book.chapters.size, total, false, "中断：${e.message}（进度已保留）"
            )
        } finally {
            if (DownloadHub.state?.running != true) finishService()
        }
    }

    private fun finishService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        scope.cancel()
    }

    // —— 通知 ——

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW)
            ch.description = "整本下载的进度"
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String, done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (title.isBlank()) "下载中" else "《$title》")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
        if (total > 0) b.setProgress(total, done, false)
        return b.build()
    }

    private fun notify(n: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) }
    }

    companion object {
        private const val CHANNEL = "download"
        private const val NOTIF_ID = 4721
        const val ACTION_START = "com.novelforge.app.DOWNLOAD_START"
        const val ACTION_STOP = "com.novelforge.app.DOWNLOAD_STOP"
        private const val EXTRA_BOOK_ID = "book_id"

        /** 章节地址已经存在书里了，这里只要 bookId */
        fun start(ctx: Context, bookId: String) {
            val intent = Intent(ctx, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BOOK_ID, bookId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.startService(
                    Intent(ctx, DownloadService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
