package com.novelforge.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 有些站点把正文塞在 script 里用 JS 现写（document.writeln + base64），
 * 普通 HTTP 请求只能拿到空壳和广告。这里用一个隐藏的 WebView 真渲染一遍，
 * 再把 #txt / .content 的 innerText 取回来。
 *
 * 整个进程复用一个 WebView，逐章排队取，比每章新建快很多。
 */
object WebFetcher {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0 Mobile Safari/537.36"

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private val busy = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(ctx: Context): WebView {
        web?.let { return it }
        val w = WebView(ctx.applicationContext)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = UA
            blockNetworkImage = true
            loadsImagesAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        w.setBackgroundColor(0x00000000)
        web = w
        return w
    }

    private val extractJs = """
        (function(){
          try {
            var selectors = ['#txt', '.read_chapterDetail', '.content', '#content', '.showtxt', '.txt', 'article'];
            var best = '';
            for (var i = 0; i < selectors.length; i++) {
              var el = document.querySelector(selectors[i]);
              if (!el) continue;
              var t = (el.innerText || el.textContent || '').trim();
              if (t.length > best.length) best = t;
            }
            if (best.length < 200) {
              var b = (document.body && (document.body.innerText || '')) || '';
              if (b.length > best.length) best = b;
            }
            return best;
          } catch (e) { return ''; }
        })()
    """.trimIndent()

    /** 渲染页面并取正文文字。失败抛异常。 */
    suspend fun text(ctx: Context, url: String, referer: String? = null): String =
        withContext(Dispatchers.Main) {
            // 同一时刻只用一个 WebView
            var waited = 0
            while (busy.get() && waited < 30000) {
                kotlinx.coroutines.delay(120)
                waited += 120
            }
            busy.set(true)
            try {
                val w = ensureWeb(ctx)
                suspendCancellableCoroutine<String> { cont ->
                    val done = AtomicBoolean(false)
                    val timeout = Runnable {
                        if (done.compareAndSet(false, true)) {
                            w.stopLoading()
                            cont.resume("")
                        }
                    }
                    main.postDelayed(timeout, 25_000)

                    w.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            // 有些站 onPageFinished 之后才 writeln 完，等一拍
                            main.postDelayed({
                                if (done.get()) return@postDelayed
                                view.evaluateJavascript(extractJs) { raw ->
                                    if (!done.compareAndSet(false, true)) return@evaluateJavascript
                                    main.removeCallbacks(timeout)
                                    val text = runCatching {
                                        JSONArray(raw ?: "[]").optString(0, "")
                                    }.getOrDefault("")
                                    cont.resume(text)
                                }
                            }, 450)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            // 主文档失败才算失败，子资源失败忽略
                            if (request.isForMainFrame) {
                                if (done.compareAndSet(false, true)) {
                                    main.removeCallbacks(timeout)
                                    cont.resume("")
                                }
                            }
                        }
                    }

                    if (referer != null) {
                        w.loadUrl(url, mapOf("Referer" to referer))
                    } else {
                        w.loadUrl(url)
                    }

                    cont.invokeOnCancellation {
                        done.set(true)
                        w.stopLoading()
                    }
                }
            } finally {
                busy.set(false)
            }
        }

    fun destroy() {
        val w = web ?: return
        web = null
        runCatching {
            w.stopLoading()
            w.loadUrl("about:blank")
            w.destroy()
        }
    }
}
