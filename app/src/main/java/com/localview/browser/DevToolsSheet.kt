package com.localview.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** F12 panel: HTML source / JS console / LOG for the active :PORT. */
class DevToolsSheet(
    private val web: LocalWebView,
    private val port: Int,
    initialTab: String = "html",
    private val onTab: ((String) -> Unit)? = null,
) : BottomSheetDialogFragment() {

    private lateinit var code: TextView
    private lateinit var portView: TextView
    private var tab = initialTab

    /** Plain-text source per tab — what COPY puts on the clipboard. */
    private var lastHtml = ""
    private val jsText: String
        get() = "// console — localhost:$port\n" +
            "fetch(`http://localhost:$port/api/health`)\n" +
            "  .then(r => r.json()).then(console.log)"
    private val logText: String
        get() = web.consoleLines.takeLast(30).joinToString("\n").ifBlank { "[SYS] no console output yet" }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.sheet_devtools, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        code = v.findViewById(R.id.dtCode)
        portView = v.findViewById(R.id.dtPort)
        portView.text = ":$port"
        val h: Button = v.findViewById(R.id.dtHtml)
        val j: Button = v.findViewById(R.id.dtJs)
        val l: Button = v.findViewById(R.id.dtLog)
        h.setOnClickListener { select("html") }
        j.setOnClickListener { select("js") }
        l.setOnClickListener { select("log") }
        v.findViewById<Button>(R.id.dtCopy).setOnClickListener { copyCurrent() }
        render()
    }

    private fun select(t: String) {
        tab = t
        onTab?.invoke(t)
        render()
    }

    private fun render() {
        when (tab) {
            "html" -> web.pageHtml {
                lastHtml = it
                code.text = "<!-- http://localhost:$port/ -->\n$it"
            }
            "js" -> code.text = jsText
            else -> code.text = logText
        }
    }

    /** Copies the visible snippet (HTML / JS / LOG) to the clipboard. */
    private fun copyCurrent() {
        val text = when (tab) {
            "html" -> "<!-- http://localhost:$port/ -->\n$lastHtml"
            "js" -> jsText
            else -> logText
        }.ifBlank { return }
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("localview:$port:$tab", text))
        android.widget.Toast.makeText(requireContext(), "[SYS] copied $tab :$port", android.widget.Toast.LENGTH_SHORT).show()
    }
}
