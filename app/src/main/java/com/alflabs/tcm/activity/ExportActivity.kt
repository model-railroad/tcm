/*
 * Project: TCM
 * Copyright (C) 2024 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.alflabs.tcm.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.app.MainApp
import com.alflabs.tcm.dagger.ActivityScope
import com.alflabs.tcm.util.GlobalDebug
import javax.inject.Inject

@ActivityScope
class ExportActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = ExportActivity::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
    }

    @Inject internal lateinit var appPrefsValues: AppPrefsValues
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.d(TAG, "onCreate")

        // Do not access dagger objects before this call
        MainApp
            .getAppComponent(this)
            .exportActivityComponentFactory
            .create()
            .inject(this)

        setContentView(R.layout.export_activity)

        editText = findViewById(R.id.edit_text)
        editText.text.clear()
        editText.text.append(prefsToText())

        findViewById<Button>(R.id.btn_import).setOnClickListener { doImport() }
        findViewById<Button>(R.id.btn_export).setOnClickListener { doExport() }
    }

    private fun prefsToText(): String {
        val sb = StringBuilder("V = 1")
        sb.append('\n')

        val n = appPrefsValues.camerasCount()
        sb.append("N = $n")
        sb.append('\n')

        for(i in 1..n) {
            val label = appPrefsValues.camerasLabel(i)
            if (label.isNotEmpty()) {
                sb.append("L$i = $label")
                sb.append('\n')
            }
            val url = appPrefsValues.camerasUrl(i)
            if (url.isNotEmpty()) {
                sb.append("U$i = $url")
                sb.append('\n')
            }
            val transform = appPrefsValues.camerasTransform(i).toString().replace('\n', '|')
            if (transform.isNotEmpty()) {
                sb.append("T$i = $transform")
                sb.append('\n')
            }
        }

        return sb.toString()
    }

    private fun doImport() {
        // Parse key-values from text field and update preferences.
        // TBD
    }

    private fun doExport() {
        // Using doc from: https://developer.android.com/training/sharing/send

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, "TrackCamMonitor Camera Configuration")
            putExtra(Intent.EXTRA_TEXT, prefsToText())
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }
}
