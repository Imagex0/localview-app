package com.localview.browser

import android.content.Context
import org.json.JSONArray

/**
 * LocalView on-device storage: session (tabs + active port + last URL per
 * port), 20-entry history ring, DevTools tab, plus cache accounting.
 * Everything synchronous SharedPreferences — no hangs, no lost writes.
 */
class LocalStore(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("localview_state", Context.MODE_PRIVATE)

    // ---- session ----
    var tabs: List<Int>
        get() = prefs.getString(KEY_TABS, "").orEmpty()
            .split(",").mapNotNull { it.toIntOrNull() }.take(5)
        set(v) = prefs.edit().putString(KEY_TABS, v.take(5).joinToString(",")).apply()

    var activePort: Int
        get() = prefs.getInt(KEY_ACTIVE, -1)
        set(v) = prefs.edit().putInt(KEY_ACTIVE, v).apply()

    fun lastUrl(port: Int): String? = prefs.getString("$KEY_URL$port", null)

    fun putLastUrl(port: Int, url: String) =
        prefs.edit().putString("$KEY_URL$port", url).apply()

    // ---- history (persisted ring, max 20) ----
    var history: List<String>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_HIST, "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString(KEY_HIST, JSONArray(v.take(20)).toString()).apply()

    fun pushHistory(url: String) {
        history = (listOf(url) + history.filterNot { it == url }).take(20)
    }

    fun clearHistory() = prefs.edit().remove(KEY_HIST).apply()

    // ---- misc ----
    var devtoolsTab: String
        get() = prefs.getString(KEY_DTAB, "html") ?: "html"
        set(v) = prefs.edit().putString(KEY_DTAB, v).apply()

    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESK, false)
        set(v) = prefs.edit().putBoolean(KEY_DESK, v).apply()

    // ---- storage accounting ----
    fun cacheBytes(): Long = app.cacheDir.walkTopDown()
        .filter { it.isFile }.sumOf { it.length() }

    fun cacheMb(): String = "%.1f MB".format(cacheBytes() / 1048576.0)

    fun clearCache(): Boolean {
        var ok = true
        app.cacheDir.listFiles()?.forEach { ok = it.deleteRecursively() && ok }
        return ok
    }

    /** Nuclear option: wipes projects file + all state keys. WebView site data cleared separately. */
    fun resetAll(ctx: Context) {
        app.getSharedPreferences("localview_projects", Context.MODE_PRIVATE).edit().clear().apply()
        prefs.edit().clear().apply()
        android.webkit.WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance()?.removeAllCookies(null)
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        private const val KEY_TABS = "tabs"
        private const val KEY_ACTIVE = "active"
        private const val KEY_URL = "last_url_"
        private const val KEY_HIST = "history"
        private const val KEY_DTAB = "dtab"
        private const val KEY_DESK = "desktop"
    }
}
