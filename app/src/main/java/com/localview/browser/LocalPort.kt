package com.localview.browser

/** Pure logic: bare-port omnibox. Mirrors the mockup's normalizeLocalInput(). */
object LocalPort {

    data class Target(val url: String, val port: Int?)

    fun parse(raw: String): Target {
        val v = raw.trim()
        if (v.matches(Regex("^\\d{2,5}$"))) {
            return Target("http://localhost:$v/", v.toInt())
        }
        if (v.matches(Regex("^:\\d{2,5}$"))) {
            val p = v.drop(1).toInt()
            return Target("http://localhost:$p/", p)
        }
        if (v.matches(Regex("^(localhost|127\\.0\\.0\\.1):\\d+(/.*)?$"))) {
            val p = v.substringAfter(":").substringBefore("/").toIntOrNull()
            return Target("http://$v".removeSuffix("/") + "/", p)
        }
        if (v.startsWith("http://") || v.startsWith("https://")) {
            val p = Regex(":(\\d+)(/|$)").find(v)?.groupValues?.get(1)?.toIntOrNull()
            return Target(v, p)
        }
        // Fallback: treat as port if numeric-ish, else localhost path.
        val p = v.toIntOrNull()
        return if (p != null) Target("http://localhost:$p/", p)
        else Target("http://localhost/$v", null)
    }

    fun isLoopback(url: String): Boolean {
        val h = runCatching { java.net.URL(url).host.lowercase() }.getOrNull() ?: return false
        return h == "localhost" || h == "127.0.0.1" || h == "[::1]"
    }

    fun healthUrl(port: Int): String = "http://localhost:$port/"
}
