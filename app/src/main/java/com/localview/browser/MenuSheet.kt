package com.localview.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Mockup sheet: grab handle, Menu head, icon rows. Reports row index. */
class MenuSheet(private val onPick: (Int) -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.sheet_menu, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val rows = listOf(R.id.rowReload, R.id.rowClear, R.id.rowStorage, R.id.rowPin, R.id.rowDash)
        rows.forEachIndexed { idx, id ->
            v.findViewById<View>(id).setOnClickListener { dismiss(); onPick(idx) }
        }
    }
}
