package com.leaking.rollupvastsurges.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import com.google.gson.Gson
import java.io.File

/**
 * 卷巨澜 WebView 卡牌 —— 渲染 .lkcard 中的 HTML 页面
 *
 * 不继承 BaseLauncherHolder，通过 Xposed 反射在运行时绑定。
 */
class LkWebViewCard(activity: Activity, private val metaInfo: Any) {
    init {
        // metaInfo 已存为属性
        metaInfo.toString()
    }


    private val TAG = "LkWebViewCard"
    private val gson = Gson()

    private var webView: WebView? = null
    private var sandboxFS: SandboxFS? = null

    var mRootView: ViewGroup? = null
        private set

    /**
     * 构建 UI
     */
    fun initView(context: Context, rootView: ViewGroup) {
        // 从 metaInfo.mExtra 读取卡牌目录
        val extra = try {
            val f = metaInfo.javaClass.getDeclaredField("mExtra").also { it.isAccessible = true }
            f.get(metaInfo) as? String
        } catch (_: Exception) { null }

        if (extra == null) {
            showError(rootView, "\u672a\u6307\u5b9a\u5361\u724c\u6570\u636e"); return
        }

        val cardDir = try {
            val map = gson.fromJson(extra, Map::class.java)
            val path = map?.get("lkcard_dir") as? String ?: run {
                showError(rootView, "\u7f3a\u5c11 lkcard_dir"); return
            }
            File(path)
        } catch (_: Exception) {
            showError(rootView, "\u5361\u724c\u6570\u636e\u89e3\u6790\u5931\u8d25"); return
        }

        if (!cardDir.exists()) {
            showError(rootView, "\u5361\u724c\u76ee\u5f55\u4e0d\u5b58\u5728"); return
        }

        // 读取 card.json
        val lkMeta = try {
            val mf = File(cardDir, "card.json")
            if (mf.exists()) gson.fromJson(mf.readText(), Map::class.java) else null
        } catch (_: Exception) { null }

        val webPage: String
        val webBg: String
        if (lkMeta != null) {
            val wv = lkMeta["webview"] as? Map<*, *>
            webPage = (wv?.get("page") as? String) ?: "page/index.html"
            webBg = (wv?.get("background") as? String) ?: "#ffffff"
        } else {
            webPage = "page/index.html"
            webBg = "#ffffff"
        }

        val pageFile = File(cardDir, webPage)
        if (!pageFile.exists()) {
            showError(rootView, "\u9875\u9762\u4e0d\u5b58\u5728: $webPage"); return
        }

        // ── 构建 WebView ──
        val wv = WebView(context)
        this.webView = wv

        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.domStorageEnabled = false
        // databaseEnabled 在 API 30 起废弃; 注解单独一行修饰整个赋值语句
        @Suppress("DEPRECATION")
        settings.databaseEnabled = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // ── 资源拦截 + 沙箱文件系统 ──
        val baseDir = cardDir.canonicalPath
        sandboxFS = SandboxFS(baseDir)
        wv.addJavascriptInterface(sandboxFS!!, "SandboxFS")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript("""
                    (function(){
                        if (typeof XMLHttpRequest !== 'undefined' && !window.__xhrBanned) {
                            window.__xhrBanned = true;
                            var desc = { get: function(){ throw new Error('XMLHttpRequest is banned in lkcard. Use fetch() instead.'); },
                                         set: function(){},
                                         configurable: false };
                            Object.defineProperty(window, 'XMLHttpRequest', desc);
                            Object.defineProperty(window, 'ActiveXObject', desc);
                        }
                    })();
                """.trimIndent(), null)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                // file:// → 仅放行卡牌目录内的路径
                if (url.startsWith("file://")) {
                    try {
                        val filePath = java.net.URLDecoder.decode(url.substring(7), "UTF-8")
                        if (filePath.startsWith(baseDir)) {
                            val f = java.io.File(filePath)
                            if (f.exists()) {
                                val mime = java.net.URLConnection.guessContentTypeFromName(f.name)
                                    ?: "application/octet-stream"
                                return WebResourceResponse(mime, null,
                                    java.io.FileInputStream(f))
                            }
                        }
                    } catch (_: Exception) {
                        return WebResourceResponse(null, null, null)
                    }
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return null
                }
                return WebResourceResponse(null, null, null)
            }
        }

        // ── 加载页面 ──
        wv.setBackgroundColor(Color.parseColor(webBg))
        wv.loadUrl("file://" + pageFile.absolutePath)
        rootView.addView(wv)
        mRootView = rootView
    }

    fun onDestroy() {
        webView?.destroy()
        webView = null
    }

    private fun showError(rootView: ViewGroup, msg: String) {
        rootView.addView(TextView(rootView.context).apply {
            text = "\u274c $msg"
            textSize = 16f
            setTextColor(Color.RED)
            setPadding(24, 24, 24, 24)
        })
    }
}

/**
 * 沙箱文件系统 API，通过 @JavascriptInterface 暴露给 JS，
 * 所有路径操作都经过 canocalPath 校验。
 */
class SandboxFS(private val basePath: String) {

    @JavascriptInterface
    fun read(path: String): String? {
        val f = resolve(path) ?: return null
        return try { f.readText() } catch (_: Exception) { null }
    }

    @JavascriptInterface
    fun write(path: String, content: String): Boolean {
        val f = resolve(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content)
            true
        } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun delete(path: String): Boolean {
        val f = resolve(path) ?: return false
        return try { f.delete() } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun list(path: String): String {
        val f = resolve(path) ?: return "[]"
        return try {
            val names = f.listFiles()?.map { it.name } ?: emptyList()
            Gson().toJson(names)
        } catch (_: Exception) { "[]" }
    }

    @JavascriptInterface
    fun readBinary(path: String): String {
        val f = resolve(path) ?: return ""
        return try {
            Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    @JavascriptInterface
    fun writeBinary(path: String, base64: String): Boolean {
        val f = resolve(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
            true
        } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun exists(path: String): Boolean {
        val f = resolve(path) ?: return false
        return f.exists()
    }

    @JavascriptInterface
    fun size(path: String): Long {
        val f = resolve(path) ?: return -1
        return try { f.length() } catch (_: Exception) { -1 }
    }

    private fun resolve(relPath: String): File? {
        if (relPath.startsWith("/")) return null
        if (relPath.contains("..")) return null
        return try {
            val f = File(basePath, relPath).canonicalFile
            if (f.absolutePath.startsWith(basePath)) f else null
        } catch (_: Exception) { null }
    }
}
