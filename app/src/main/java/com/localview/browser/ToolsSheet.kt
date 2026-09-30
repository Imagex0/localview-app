package com.localview.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Tools hub: every dev tool in one sheet with live ON/OFF state.
 * Rows: 0 F12, 1 maximize/restore, 2 desktop, 3 hard reload, 4 clear, 5 storage.
 */
class ToolsSheet(
    private val devtoolsOn: Boolean,
    private val maximized: Boolean,
    private val desktopOn: Boolean,
    private val onPick: (Int) -> Unit,
) : BottomSheetDialogFragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.sheet_tools, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        v.findViewById<TextView>(R.id.toolDevtoolsLabel).text =
            if (devtoolsOn) "F12 DEVTOOLS [ON]" else "F12 DEVTOOLS [OFF]"
        v.findViewById<TextView>(R.id.toolMaximizeLabel).text =
            if (maximized) "RESTORE BORDERS" else "MAXIMIZE PAGE"
        v.findViewById<TextView>(R.id.toolDesktopLabel).text =
            if (desktopOn) "DESKTOP VIEW [ON]" else "DESKTOP VIEW [OFF]"
        val rows = listOf(
            R.id.toolDevtools, R.id.toolMaximize, R.id.toolDesktop,
            R.id.toolReload, R.id.toolClear, R.id.toolStorage,
        )
        rows.forEachIndexed { idx, id ->
            v.findViewById<View>(id).setOnClickListener { dismiss(); onPick(idx) }
        }
    }
}
