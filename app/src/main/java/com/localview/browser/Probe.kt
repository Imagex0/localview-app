package com.localview.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/** Fast loopback probe: HEAD /, 800ms budget. Console-grade, no dependencies. */
object Probe {

    data class Result(val port: Int, val live: Boolean, val ms: Long)

    suspend fun check(port: Int): Result = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val live = withTimeoutOrNull(800L) {
            runCatching {
                val c = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"
                c.connectTimeout = 700
                c.readTimeout = 700
                c.instanceFollowRedirects = false
                c.connect()
                c.responseCode in 100..599
            }.getOrDefault(false)
        } ?: false
        Result(port, live, System.currentTimeMillis() - start)
    }
}
