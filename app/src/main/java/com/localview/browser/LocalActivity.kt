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
    private var projects: List<ProjectsRepo.Project> = emptyList()
    private var live: Map<Int, Boolean> = emptyMap()

    private val tabs = ArrayDeque<Int>()
    private var active: Int = -1
    private val history = ArrayDeque<String>(20)

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

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        statLine = findViewById(R.id.statLine)

        findViewById<RecyclerView>(R.id.projectList).layoutManager = LinearLayoutManager(this)
        adapter = ProjectAdapter(
            onOpen = { openUrl(it.url, it.port) },
            onLong = { p -> lifecycleScope.launch { repo.remove(p.port); refresh() }; true },
        )
        findViewById<RecyclerView>(R.id.projectList).adapter = adapter

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
        findViewById<Button>(R.id.btnDevtools).setOnClickListener { devtools() }

        web.onConsole = { line -> statLine.text = "[LOG] ${line.take(120)}" }
        web.onProgress = { p -> if (p == 100) statLine.text = "[OK] loaded" }

        handleIntent()
        lifecycleScope.launch { refresh() }
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

    private suspend fun refresh() {
        projects = repo.load()
        live = projects.associate { it.port to Probe.check(it.port).live }
        adapter.submit(projects, live)
        findViewById<TextView>(R.id.projCount).text = projects.size.toString()
    }

    private fun go() {
        val t = LocalPort.parse(urlBar.text.toString().ifBlank { "5173" })
        openUrl(t.url, t.port)
    }

    private fun openUrl(url: String, port: Int?) {
        if (history.size >= 20) history.removeFirst()
        history.addLast(url)
        port?.let {
            if (!tabs.contains(it)) {
                if (tabs.size >= 5) tabs.removeFirst()
                tabs.addLast(it)
            }
            active = it
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
        val items = arrayOf("Hard reload", "Clear site data for this port", "Pin to Home (shortcut)", "Dashboard")
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> { web.hardReload(); toast("[SYS] hard reload") }
                    1 -> { web.clearSiteData(active); toast("[SYS] site data cleared") }
                    2 -> { toast("[SYS] pin to Home coming via shortcut") }
                    3 -> showDashboard()
                }
            }.show()
    }

    private fun devtools() {
        if (web.visibility != View.VISIBLE) { toast("[SYS] open a project first"); return }
        DevToolsSheet(web, active).show(supportFragmentManager, "devtools")
    }

    private fun addDialog() {
        val v = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val name = EditText(this).apply { hint = getString(R.string.add_name_hint) }
        val port = EditText(this).apply { hint = getString(R.string.add_port_hint); inputType = android.text.InputType.TYPE_CLASS_TEXT }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(name); addView(port); setPadding(40, 20, 40, 20) }
        AlertDialog.Builder(this).setTitle(getString(R.string.add_title)).setView(box)
            .setPositiveButton("Save") { _, _ ->
                lifecycleScope.launch { repo.add(name.text.toString(), port.text.toString()); refresh() }
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
            val name: TextView = v.findViewById(R.id.pName)
            val port: TextView = v.findViewById(R.id.pUrl)
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
