package com.localview.browser

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * LocalView v0.1: dashboard (projects + probe dots) on top, single tuned
 * WebView below. Max 5 tabs, 24h history is intentionally NOT kept —
 * ring buffer of last 20 visited ports only.
 */
class LocalActivity : AppCompatActivity() {

    private lateinit var repo: ProjectsRepo
    private lateinit var store: LocalStore
    private var projects: List<ProjectsRepo.Project> = emptyList()
    private var live: Map<Int, Boolean> = emptyMap()

    private val tabs = ArrayDeque<Int>()
    private var active: Int = -1

    private lateinit var web: LocalWebView
    private lateinit var urlBar: EditText
    private lateinit var portPill: TextView
    private lateinit var tabCount: TextView
    private lateinit var adapter: ProjectAdapter

    private var dtOpen = false
    private var dtTab = "html"
    private var lastHtml = ""
    private var maximized = false

    companion object {
        /** Mockup accent variety, desaturated Matrix set. */
        private val ACCENTS = listOf("#4ED58A", "#56C2BB", "#A9C46A", "#E2A84E", "#D48473", "#8FA3D9")
        fun accentFor(port: Int): Int =
            Color.parseColor(ACCENTS[Math.abs(port) % ACCENTS.size])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mockup is dark-only: pin night mode before inflation so dialogs match.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local)
        repo = ProjectsRepo(this)
        store = LocalStore(this)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        portPill = findViewById(R.id.portPill)
        tabCount = findViewById(R.id.tabCount)
        dtTab = store.devtoolsTab

        findViewById<RecyclerView>(R.id.projectList).layoutManager = LinearLayoutManager(this)
        adapter = ProjectAdapter(
            onOpen = { openUrl(it.url, it.port) },
            onDel = { p -> repo.remove(p.port); reloadProjects(); lifecycleScope.launch { probeAll() } },
            onLong = { p -> repo.remove(p.port); reloadProjects(); true },
        )
        findViewById<RecyclerView>(R.id.projectList).adapter = adapter
        // Synchronous load: projects are visible on first frame, never await a store.
        reloadProjects()

        findViewById<Button>(R.id.btnGo).setOnClickListener { go() }
        urlBar.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        val dashQuick: EditText = findViewById(R.id.dashQuick)
        dashQuick.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) {
                val t = LocalPort.parse(dashQuick.text.toString().ifBlank { "5173" })
                openUrl(t.url, t.port); true
            } else false
        }
        findViewById<Button>(R.id.dashGo).setOnClickListener {
            val t = LocalPort.parse(dashQuick.text.toString().ifBlank { "5173" })
            openUrl(t.url, t.port)
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { if (web.visibility == View.VISIBLE && web.canGoBack()) web.goBack() }
        findViewById<View>(R.id.btnFwd).setOnClickListener { if (web.visibility == View.VISIBLE && web.canGoForward()) web.goForward() }
        findViewById<View>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<View>(R.id.btnReload).setOnLongClickListener { web.hardReload(); toast("[SYS] hard reload"); true }
        findViewById<View>(R.id.tabsBadge).setOnClickListener { showBrowser() }
        findViewById<View>(R.id.btnMenu).setOnClickListener { menu() }
        findViewById<View>(R.id.btnHome).setOnClickListener { showDashboard() }
        // Focus glow on the URL bar: border lights up, like :focus in the mockup.
        val urlBox: View = findViewById(R.id.urlBarBox)
        urlBar.setOnFocusChangeListener { _, has ->
            urlBox.setBackgroundResource(if (has) R.drawable.lv_urlbar_focus else R.drawable.lv_urlbar)
        }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnAddTop).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnDevtools).setOnClickListener { devtools() }
        findViewById<View>(R.id.btnMaximize).setOnClickListener { setMaximized(true) }
        findViewById<View>(R.id.btnMinimize).setOnClickListener { setMaximized(false) }
        findViewById<View>(R.id.btnDesktop).setOnClickListener { toggleDesktop() }
        findViewById<View>(R.id.btnTools).setOnClickListener { toolsHub() }
        web.setDesktopMode(store.desktopMode)
        paintDesktop()
        findViewById<Button>(R.id.dtHtml).setOnClickListener { selectDt("html") }
        findViewById<Button>(R.id.dtJs).setOnClickListener { selectDt("js") }
        findViewById<Button>(R.id.dtLog).setOnClickListener { selectDt("log") }
        findViewById<Button>(R.id.dtCopy).setOnClickListener { copyDt() }
        findViewById<Button>(R.id.dtClose).setOnClickListener { toggleDt() }
        findViewById<View>(R.id.btnNewTab).setOnClickListener { newTabDialog() }

        web.onConsole = { if (dtOpen && dtTab == "log") renderDt() }
        web.onProgress = { _ -> }

        handleIntent()
        restoreSession()
        lifecycleScope.launch { probeAll() }
    }

    /** Maximize: page only — chrome, tabs, devbar and panels all hide. */
    private fun setMaximized(on: Boolean) {
        maximized = on
        val chrome = if (on) View.GONE else View.VISIBLE
        findViewById<View>(R.id.chromeBox).visibility = chrome
        findViewById<View>(R.id.chromeDivider).visibility = chrome
        findViewById<View>(R.id.tabsRow).visibility = chrome
        findViewById<View>(R.id.devbar).visibility = chrome
        if (on) {
            findViewById<View>(R.id.dtPanel).visibility = View.GONE
            dtOpen = false
        }
        findViewById<View>(R.id.btnMinimize).visibility = if (on) View.VISIBLE else View.GONE
        val ctl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            ctl.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            ctl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Desktop view: spoof desktop UA, persist, reload — then PROVE it. */
    private fun toggleDesktop() {
        val on = !web.desktopOn
        web.setDesktopMode(on)
        store.desktopMode = on
        paintDesktop()
        // Verify against the live WebView UA, not our flag: no "Mobile" token = desktop.
        val ua = web.settings.userAgentString
        val proven = if (on) !ua.contains("Mobile") else ua.contains("Mobile")
        toast(
            if (proven) "[SYS] desktop [${if (on) "ON" else "OFF"}] — verified"
            else "[ERR] UA reject — still ${if (on) "mobile" else "desktop"}",
        )
    }

    /** New tab key: bare port/URL in, tab out. Never touches saved projects. */
    private fun newTabDialog() {
        val box = layoutInflater.inflate(R.layout.dialog_add, null)
        val name: EditText = box.findViewById(R.id.fName)
        val port: EditText = box.findViewById(R.id.fPort)
        name.hint = "Label (optional)"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("New tab").setView(box)
            .setPositiveButton("Open") { _, _ ->
                val label = name.text.toString().ifBlank { null }
                val t = LocalPort.parse(port.text.toString().ifBlank { "5173" })
                if (label != null && t.port != null) {
                    toast("[TIP] use + ADD to save “$label” permanently")
                }
                openUrl(t.url, t.port)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun paintDesktop() {
        val on = web.desktopOn
        findViewById<View>(R.id.btnDesktop).apply {
            (this as? androidx.appcompat.widget.AppCompatImageButton)?.setColorFilter(
                getColor(if (on) R.color.lv_acc else R.color.lv_mut),
            )
            background = getDrawable(if (on) R.drawable.lv_chip_on else R.drawable.lv_nav)
        }
    }

    /** Hamburger hub: every dev tool in one sheet with live state. */
    private fun toolsHub() {
        ToolsSheet(dtOpen, maximized, web.desktopOn) { w ->
            when (w) {
                0 -> devtools()
                1 -> setMaximized(!maximized)
                2 -> toggleDesktop()
                3 -> { web.hardReload(); toast("[SYS] hard reload") }
                4 -> { web.clearSiteData(active); toast("[SYS] site data cleared") }
                5 -> storageDialog()
            }
        }.show(supportFragmentManager, "tools")
    }

    override fun onPause() {
        super.onPause()
        persistSession()
    }

    /** Session survives process death: tabs + active port + last URL per port. */
    private fun persistSession() {
        store.tabs = tabs.toList()
        store.activePort = active
        val cur = web.url
        if (active > 0 && cur != null) store.putLastUrl(active, cur)
    }

    private fun restoreSession() {
        val saved = store.tabs.filter { it > 0 }
        if (saved.isEmpty()) return
        tabs.clear()
        tabs.addAll(saved)
        renderTabs()
        val a = store.activePort.takeIf { saved.contains(it) } ?: saved.last()
        openUrl(store.lastUrl(a) ?: "http://localhost:$a/", a)
    }

    /** Projects come from disk synchronously; only liveness probes suspend. */
    private fun reloadProjects() {
        projects = repo.load()
        adapter.submit(projects, live)
        findViewById<TextView>(R.id.projCount).text = projects.size.toString()
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        // F12 on hardware keyboards toggles DevTools, like the mockup.
        if (code == KeyEvent.KEYCODE_F12) { devtools(); return true }
        return super.onKeyDown(code, event)
    }

    override fun onBackPressed() {
        if (maximized) { setMaximized(false); return }
        if (web.visibility == View.VISIBLE && web.canGoBack()) web.goBack()
        else if (web.visibility == View.VISIBLE) showDashboard()
        else super.onBackPressed()
    }

    private fun handleIntent() {
        val d = intent?.data ?: return
        if (d.scheme == "http" || d.scheme == "https") openUrl(d.toString(), null)
    }

    private suspend fun probeAll() {
        live = projects.associate { it.port to Probe.check(it.port).live }
        adapter.submit(projects, live)
    }

    private fun refresh() {
        reloadProjects()
        lifecycleScope.launch { probeAll() }
    }

    private fun go() {
        val t = LocalPort.parse(urlBar.text.toString().ifBlank { "5173" })
        openUrl(t.url, t.port)
    }

    private fun openUrl(url: String, port: Int?) {
        store.pushHistory(url)
        port?.let {
            if (!tabs.contains(it)) {
                if (tabs.size >= 5) tabs.removeFirst()
                tabs.addLast(it)
            }
            active = it
            store.putLastUrl(it, url)
            persistSession()
        }
        urlBar.setText(url)
        portPill.text = port?.let { ":$it" } ?: ""
        showBrowser()
        web.loadUrl(url)
        renderTabs()
        if (dtOpen) renderDt()
        lifecycleScope.launch {
            val p = port ?: return@launch
            val r = Probe.check(p)
            if (!r.live) toast("[OFF] :$p offline — start server")
        }
    }

    private fun showBrowser() {
        findViewById<View>(R.id.dashboard).visibility = View.GONE
        findViewById<View>(R.id.chromeBox).visibility = View.VISIBLE
        findViewById<View>(R.id.chromeDivider).visibility = View.VISIBLE
        findViewById<View>(R.id.tabsRow).visibility = View.VISIBLE
        web.visibility = View.VISIBLE
        findViewById<View>(R.id.devbar).visibility = View.VISIBLE
    }

    private fun showDashboard() {
        web.visibility = View.GONE
        findViewById<View>(R.id.devbar).visibility = View.GONE
        findViewById<View>(R.id.dtPanel).visibility = View.GONE
        dtOpen = false
        findViewById<View>(R.id.chromeBox).visibility = View.GONE
        findViewById<View>(R.id.chromeDivider).visibility = View.GONE
        findViewById<View>(R.id.tabsRow).visibility = View.GONE
        findViewById<View>(R.id.dashboard).visibility = View.VISIBLE
        lifecycleScope.launch { refresh() }
    }

    /** Tab chip with a visible close key — no hidden gestures. */
    private fun chip(port: Int, on: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(if (on) R.drawable.lv_chip_on else R.drawable.lv_chip)
            setPadding(dp(9), dp(6), dp(4), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { openUrl("http://localhost:$port/", port) }
        }
        val label = TextView(this).apply {
            text = ":$port"
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            setTextColor(getColor(if (on) R.color.lv_acc else R.color.lv_mut))
        }
        val x = TextView(this).apply {
            text = "×"
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setPadding(dp(8), 0, dp(4), 0)
            setTextColor(getColor(R.color.lv_mut2))
            isClickable = true
            isFocusable = true
            setOnClickListener { closeTab(port) }
        }
        row.addView(label)
        row.addView(x)
        return row
    }

    private fun closeTab(port: Int) {
        tabs.remove(port)
        if (tabs.isEmpty()) {
            active = -1
            persistSession()
            showDashboard()
            return
        }
        if (active == port) {
            active = tabs.last()
            openUrl(store.lastUrl(active) ?: "http://localhost:$active/", active)
        } else {
            renderTabs()
            persistSession()
        }
    }

    private fun renderTabs() {
        val strip = findViewById<LinearLayout>(R.id.tabStrip)
        strip.removeAllViews()
        tabCount.text = tabs.size.toString()
        tabs.forEach { p ->
            val c = chip(p, p == active)
            strip.addView(c)
            (c.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = dp(6)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun menu() {
        MenuSheet { w ->
            when (w) {
                0 -> { web.hardReload(); toast("[SYS] hard reload") }
                1 -> { web.clearSiteData(active); toast("[SYS] site data cleared") }
                2 -> storageDialog()
                3 -> { toast("[SYS] pin to Home coming via shortcut") }
                4 -> showDashboard()
            }
        }.show(supportFragmentManager, "menu")
    }

    /** Visible on-device storage: what LocalView keeps + one-tap clearing. */
    private fun storageDialog() {
        val stats = "Projects: ${repo.count()}\n" +
            "History: ${store.history.size} / 20\n" +
            "Open tabs: ${tabs.size} / 5\n" +
            "Cache: ${store.cacheMb()}\n" +
            "Session: ${if (store.activePort > 0) "resumes :${store.activePort}" else "none"}"
        AlertDialog.Builder(this)
            .setTitle("Storage")
            .setMessage(stats)
            .setNeutralButton("Clear history") { _, _ -> store.clearHistory(); toast("[SYS] history cleared") }
            .setNegativeButton("Clear cache") { _, _ ->
                web.clearCache(true); store.clearCache(); toast("[SYS] cache cleared")
            }
            .setPositiveButton("Reset all") { _, _ ->
                store.resetAll(this)
                tabs.clear(); active = -1
                reloadProjects(); showDashboard(); toast("[SYS] storage reset")
            }
            .show()
    }

    private fun devtools() {
        if (web.visibility != View.VISIBLE) { toast("[SYS] open a project first"); return }
        toggleDt()
    }

    /** Inline F12 panel, bound to the active :PORT like the mockup dtpanel. */
    private fun toggleDt() {
        dtOpen = !dtOpen
        findViewById<View>(R.id.dtPanel).visibility = if (dtOpen) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnDevtools).text = if (dtOpen) "DEVTOOLS [ON]" else "DEVTOOLS [F12]"
        if (dtOpen) renderDt()
    }

    private fun selectDt(t: String) {
        dtTab = t
        store.devtoolsTab = t
        paintDtTabs()
        renderDt()
    }

    private fun paintDtTabs() {
        val map = mapOf("html" to R.id.dtHtml, "js" to R.id.dtJs, "log" to R.id.dtLog)
        map.forEach { (t, id) ->
            val b: Button = findViewById(id)
            val on = t == dtTab
            b.setBackgroundResource(if (on) R.drawable.lv_chip_on else R.drawable.lv_chip)
            b.setTextColor(getColor(if (on) R.color.lv_acc else R.color.lv_mut))
        }
    }

    private fun dtJsText(): String =
        "// console — localhost:$active\n" +
            "fetch(`http://localhost:$active/api/health`)\n" +
            "  .then(r => r.json()).then(console.log)"

    private fun dtLogText(): String {
        val ua = "UA: " + web.settings.userAgentString
        return (listOf(ua) + web.consoleLines.takeLast(29)).joinToString("\n").ifBlank { "[SYS] no console output yet" }
    }

    private fun renderDt() {
        findViewById<TextView>(R.id.dtPortLabel).text = ":$active"
        paintDtTabs()
        val code: TextView = findViewById(R.id.dtCode)
        when (dtTab) {
            "html" -> web.pageHtml {
                lastHtml = it
                code.text = "<!-- http://localhost:$active/ -->\n$it"
            }
            "js" -> code.text = dtJsText()
            else -> code.text = dtLogText()
        }
    }

    private fun copyDt() {
        val text = when (dtTab) {
            "html" -> "<!-- http://localhost:$active/ -->\n$lastHtml"
            "js" -> dtJsText()
            else -> dtLogText()
        }.ifBlank { return }
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("localview:$active:$dtTab", text))
        toast("[SYS] copied $dtTab :$active")
    }

    private fun addDialog() {
        val box = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val name: EditText = box.findViewById(R.id.fName)
        val port: EditText = box.findViewById(R.id.fPort)
        AlertDialog.Builder(this).setTitle(getString(R.string.add_title)).setView(box)
            .setPositiveButton("Save") { _, _ ->
                val (p, ok) = repo.add(name.text.toString(), port.text.toString())
                reloadProjects()
                lifecycleScope.launch { probeAll() }
                toast(if (ok) "[SYS] saved ${p.name} :${p.port} (${repo.count()})" else "[ERR] write failed — retry")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toast(m: String) {
        // System toast: custom views are ignored on Android 12+, plain text always shows.
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()
    }

    private class ProjectAdapter(
        val onOpen: (ProjectsRepo.Project) -> Unit,
        val onDel: (ProjectsRepo.Project) -> Unit,
        val onLong: (ProjectsRepo.Project) -> Boolean,
    ) : RecyclerView.Adapter<ProjectAdapter.H>() {
        private var items: List<ProjectsRepo.Project> = emptyList()
        private var live: Map<Int, Boolean> = emptyMap()

        fun submit(i: List<ProjectsRepo.Project>, l: Map<Int, Boolean>) {
            items = i; live = l; notifyDataSetChanged()
        }

        class H(v: View) : RecyclerView.ViewHolder(v) {
            val stripe: View = v.findViewById(R.id.stripe)
            val avatar: TextView = v.findViewById(R.id.avatar)
            val dot: View = v.findViewById(R.id.dot)
            val name: TextView = v.findViewById(R.id.pName)
            val port: TextView = v.findViewById(R.id.pUrl)
            val desc: TextView = v.findViewById(R.id.pDesc)
            val state: TextView = v.findViewById(R.id.pState)
            val open: Button = v.findViewById(R.id.pOpen)
            val del: Button = v.findViewById(R.id.pDel)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): H =
            H(LayoutInflater.from(p.context).inflate(R.layout.item_project, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: H, pos: Int) {
            val p = items[pos]
            val isLive = live[p.port] ?: true
            val acc = accentFor(p.port)
            val ctx = h.itemView.context
            h.stripe.setBackgroundColor(acc)
            h.avatar.text = p.name.firstOrNull()?.uppercase() ?: "L"
            h.avatar.setTextColor(acc)
            (h.avatar.background.mutate() as GradientDrawable).setStroke(
                (2 * ctx.resources.displayMetrics.density).toInt(), acc,
            )
            h.name.text = p.name.uppercase()
            h.port.text = ":${p.port}"
            h.port.setTextColor(acc)
            h.dot.setBackgroundColor(if (isLive) acc else ctx.getColor(R.color.lv_amber))
            h.desc.text = p.url.removePrefix("http://").removePrefix("https://").removeSuffix("/")
            h.state.text = if (isLive) "[LIVE]" else "[OFF]"
            h.state.setTextColor(
                ctx.getColor(if (isLive) R.color.lv_mut else R.color.lv_amber),
            )
            h.open.setOnClickListener { onOpen(p) }
            h.del.setOnClickListener { onDel(p) }
            h.itemView.setOnClickListener { onOpen(p) }
            h.itemView.setOnLongClickListener { onLong(p) }
        }
    }
}
