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
    private lateinit var statLine: TextView
    private lateinit var statMs: TextView
    private lateinit var portPill: TextView
    private lateinit var tabCount: TextView
    private lateinit var adapter: ProjectAdapter

    private var dtOpen = false
    private var dtTab = "html"
    private var lastHtml = ""

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
        statLine = findViewById(R.id.statLine)
        statMs = findViewById(R.id.statMs)
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
        findViewById<Button>(R.id.btnAdd).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnAddTop).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnDevtools).setOnClickListener { devtools() }
        findViewById<Button>(R.id.dtHtml).setOnClickListener { selectDt("html") }
        findViewById<Button>(R.id.dtJs).setOnClickListener { selectDt("js") }
        findViewById<Button>(R.id.dtLog).setOnClickListener { selectDt("log") }
        findViewById<Button>(R.id.dtCopy).setOnClickListener { copyDt() }
        findViewById<Button>(R.id.dtCopy).setOnClickListener { copyDt() }
        findViewById<Button>(R.id.dtClose).setOnClickListener { toggleDt() }

        web.onConsole = { line -> statLine.text = "[LOG] ${line.take(120)}" }
        web.onProgress = { p -> if (p == 100) statLine.text = "[OK] loaded" }

        handleIntent()
        restoreSession()
        lifecycleScope.launch { probeAll() }
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
        renderPorts()
        val a = store.activePort.takeIf { saved.contains(it) } ?: saved.last()
        openUrl(store.lastUrl(a) ?: "http://localhost:$a/", a)
    }

    /** Projects come from disk synchronously; only liveness probes suspend. */
    private fun reloadProjects() {
        projects = repo.load()
        adapter.submit(projects, live)
        findViewById<TextView>(R.id.projCount).text = projects.size.toString()
        renderPorts()
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        // F12 on hardware keyboards toggles DevTools, like the mockup.
        if (code == KeyEvent.KEYCODE_F12) { devtools(); return true }
        return super.onKeyDown(code, event)
    }

    override fun onBackPressed() {
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
        renderPorts()
        if (dtOpen) renderDt()
        lifecycleScope.launch {
            val p = port ?: return@launch
            val r = Probe.check(p)
            statLine.text = if (r.live) "[OK] :$p live — 200" else "[OFF] :$p offline"
            statLine.setTextColor(
                getColor(if (r.live) R.color.lv_acc else R.color.lv_amber),
            )
            statMs.text = if (r.live) "${r.ms}ms / HMR WS OK" else "retry / start server"
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

    private fun chip(text: String, on: Boolean): Button =
        Button(this, null, 0, R.style.Widget_LocalView_Chip).apply {
            this.text = text
            minWidth = 0
            setBackgroundResource(if (on) R.drawable.lv_chip_on else R.drawable.lv_chip)
            setTextColor(getColor(if (on) R.color.lv_acc else R.color.lv_mut))
        }

    private fun renderTabs() {
        val strip = findViewById<LinearLayout>(R.id.tabStrip)
        strip.removeAllViews()
        tabCount.text = tabs.size.toString()
        tabs.forEach { p ->
            val b = chip(":$p", p == active).apply {
                setOnClickListener { openUrl("http://localhost:$p/", p) }
                setOnLongClickListener { tabs.remove(p); if (active == p) active = tabs.lastOrNull() ?: -1; renderTabs(); true }
            }
            strip.addView(b)
            (b.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = dp(6)
        }
    }

    private fun renderPorts() {
        val strip = findViewById<LinearLayout>(R.id.portStrip)
        strip.removeAllViews()
        (projects.map { it.port } + listOf(3000, 5173, 8000, 8080, 9000)).distinct().forEach { p ->
            val b = chip(":$p", p == active).apply {
                setOnClickListener { openUrl("http://localhost:$p/", p) }
            }
            strip.addView(b)
            (b.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = dp(6)
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

    private fun dtLogText(): String =
        web.consoleLines.takeLast(30).joinToString("\n").ifBlank { "[SYS] no console output yet" }

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

    private fun toast(m: String) { statLine.text = m }

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
