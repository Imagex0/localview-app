package com.localview.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Single tuned WebView. Pattern lifted from Solipsism's WebViewFactory,
 * minus adblock / scanner / userscript hooks. Dev flags forced ON so
 * Vite/Next HMR, DOM storage and ServiceWorkers always work on loopback.
 */
@SuppressLint("SetJavaScriptEnabled")
class LocalWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    val consoleLines = ArrayDeque<String>(200)
    var onConsole: ((String) -> Unit)? = null
    var onProgress: ((Int) -> Unit)? = null
    private var mobileUa = ""

    init {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = false
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }
        mobileUa = settings.userAgentString
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Keep loopback + https in-app; hand the rest to the system.
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                return try {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                } catch (_: Exception) {
                    true
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val line = "[${m.messageLevel()}] ${m.message()} (${m.sourceId()}:${m.lineNumber()})"
                if (consoleLines.size >= 200) consoleLines.removeFirst()
                consoleLines.addLast(line)
                onConsole?.invoke(line)
                return true
            }

            override fun onProgressChanged(view: WebView, progress: Int) {
                onProgress?.invoke(progress)
            }
        }
    }

    /** Desktop view: spoof a Linux x86_64 Chrome token set, reload to apply. */
    var desktopOn = false
        private set

    fun setDesktopMode(on: Boolean) {
        if (on == desktopOn && settings.userAgentString.isNotEmpty()) return
        desktopOn = on
        settings.userAgentString = if (on) {
            mobileUa.replace("; wv", "")
                .replace(Regex("Linux; Android [\\d.]+"), "X11; Linux x86_64")
                .replace("Mobile", "")
        } else {
            mobileUa
        }
        if (url != null) reload()
    }

    /** Long-press reload semantics: bypass cache, like the mockup. */
    fun hardReload() {
        clearCache(false)
        reload()
    }

    /** One-tap reset for the active port. */
    fun clearSiteData(port: Int) {
        clearCache(true)
        clearHistory()
        android.webkit.WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance()?.let { cm ->
            cm.removeSessionCookies(null)
            // Scoped flush only; loopback cookies die with the session removal above.
            cm.flush()
        }
        loadUrl("http://localhost:$port/", emptyMap())
    }

    fun pageHtml(cb: (String) -> Unit) {
        evaluateJavascript("(document.documentElement||{}).outerHTML||''") { raw ->
            cb(raw?.unquoteJsString()?.take(60_000) ?: "")
        }
    }

    private fun String.unquoteJsString(): String {
        var s = this
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
