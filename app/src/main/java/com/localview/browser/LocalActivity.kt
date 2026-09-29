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
    private lateinit var adapter: ProjectAdapter

    companion object {
        /** Mockup accent variety, desaturated Matrix set. */
        private val ACCENTS = listOf("#4ED58A", "#56C2BB", "#A9C46A", "#E2A84E", "#D48473", "#8FA3D9")
        fun accentFor(port: Int): Int =
            Color.parseColor(ACCENTS[Math.abs(port) % ACCENTS.size])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local)
        repo = ProjectsRepo(this)
        store = LocalStore(this)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        statLine = findViewById(R.id.statLine)

        findViewById<RecyclerView>(R.id.projectList).layoutManager = LinearLayoutManager(this)
        adapter = ProjectAdapter(
            onOpen = { openUrl(it.url, it.port) },
            onLong = { p -> repo.remove(p.port); reloadProjects(); true },
        )
        findViewById<RecyclerView>(R.id.projectList).adapter = adapter
        // Synchronous load: projects are visible on first frame, never await a store.
        reloadProjects()

        findViewById<Button>(R.id.btnGo).setOnClickListener { go() }
        urlBar.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { if (web.visibility == View.VISIBLE && web.canGoBack()) web.goBack() }
        findViewById<Button>(R.id.btnFwd).setOnClickListener { if (web.visibility == View.VISIBLE && web.canGoForward()) web.goForward() }
        findViewById<Button>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<Button>(R.id.btnReload).setOnLongClickListener { web.hardReload(); toast("[SYS] hard reload"); true }
        findViewById<Button>(R.id.btnTabs).setOnClickListener { showBrowser() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { menu() }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnAddTop).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnDevtools).setOnClickListener { devtools() }

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
        showBrowser()
        web.loadUrl(url)
        renderTabs()
        renderPorts()
        lifecycleScope.launch {
            val p = port ?: return@launch
            val r = Probe.check(p)
            statLine.text = if (r.live) "[OK] :$p live — ${r.ms}ms" else "[OFF] :$p offline"
            statLine.setTextColor(
                getColor(if (r.live) R.color.lv_acc else R.color.lv_amber),
            )
        }
    }

    private fun showBrowser() {
        findViewById<View>(R.id.dashboard).visibility = View.GONE
        web.visibility = View.VISIBLE
        findViewById<View>(R.id.devbar).visibility = View.VISIBLE
    }

    private fun showDashboard() {
        web.visibility = View.GONE
        findViewById<View>(R.id.devbar).visibility = View.GONE
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
        findViewById<Button>(R.id.btnTabs).text = "▦ ${tabs.size}"
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
        val items = arrayOf("Hard reload", "Clear site data for this port", "Storage", "Pin to Home (shortcut)", "Dashboard")
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> { web.hardReload(); toast("[SYS] hard reload") }
                    1 -> { web.clearSiteData(active); toast("[SYS] site data cleared") }
                    2 -> storageDialog()
                    3 -> { toast("[SYS] pin to Home coming via shortcut") }
                    4 -> showDashboard()
                }
            }.show()
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
        DevToolsSheet(web, active, store.devtoolsTab) { store.devtoolsTab = it }
            .show(supportFragmentManager, "devtools")
    }

    private fun addDialog() {
        val v = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val name = EditText(this).apply { hint = getString(R.string.add_name_hint) }
        val port = EditText(this).apply { hint = getString(R.string.add_port_hint); inputType = android.text.InputType.TYPE_CLASS_TEXT }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(name); addView(port); setPadding(40, 20, 40, 20) }
        AlertDialog.Builder(this).setTitle(getString(R.string.add_title)).setView(box)
            .setPositiveButton("Save") { _, _ ->
                val p = repo.add(name.text.toString(), port.text.toString())
                reloadProjects()
                lifecycleScope.launch { probeAll() }
                toast("[SYS] saved :${p.port}")
            }
            .setNegativeButton("Cancel", null).show()
        v.toString()
    }

    private fun toast(m: String) { statLine.text = m }

    private class ProjectAdapter(
        val onOpen: (ProjectsRepo.Project) -> Unit,
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
            h.itemView.setOnClickListener { onOpen(p) }
            h.itemView.setOnLongClickListener { onLong(p) }
        }
    }
}
