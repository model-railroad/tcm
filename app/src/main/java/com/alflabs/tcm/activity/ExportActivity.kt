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

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppMonitor
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
        editText.setText(prefsToText())

        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                editText.setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
            }
        }

        findViewById<Button>(R.id.btn_load).setOnClickListener { confirmLoad() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { doShare() }
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

    private fun confirmLoad() {
        val builder = AlertDialog.Builder(this)
        builder
            .setTitle(R.string.export__load_dlg_title)
            .setMessage(R.string.export__load_dlg_msg)
            .setNegativeButton(R.string.export__load_dlg_cancel) {_, _ -> /* no-op */ }
            .setPositiveButton(R.string.export__load_dlg_ok) { _, _ -> doLoad() }
        builder.show()
    }

    private fun doLoad() {
        // Parse key-values from text field and update preferences.
        // Note that order of keys matters:
        // V=1
        // N=num cameras (only valid if V=1)
        // L|U|T + camera index = value (only valid if N=... was read before).

        val source = editText.text.toString()

        var numCam = 0
        val linePattern = "^([A-Z])([0-9]+)?[ \t]]*=(.*)$".toRegex()
        var versionAccepted = false
        source.lines().forEach { line ->
            linePattern.matchEntire(line)?.let { lineMatch ->
                val key = lineMatch.groupValues[1]
                val index = lineMatch.groupValues[2].toIntOrNull() ?: 0
                val value = lineMatch.groupValues[3].trim()

                when(key) {
                    "V" -> {
                        versionAccepted = value == "1"
                    }
                    "N" -> if (versionAccepted) {
                        val n = value.toIntOrNull() ?: 0
                        if (n >= 1 && n <= AppMonitor.MAX_CAMERAS) {
                            numCam = n
                            // WARNING: PREF_CAMERAS__COUNT is a *string* preference for legacy reasons.
                            appPrefsValues.setString(AppPrefsValues.PREF_CAMERAS__COUNT, n.toString())
                        }
                    }
                    "L" -> if (versionAccepted && index >= 1 && index <= numCam) {
                        appPrefsValues.setString(
                            AppPrefsValues.PREF_CAMERAS__LABEL[index]!!,
                            value)
                    }
                    "U" -> if (versionAccepted && index >= 1 && index <= numCam) {
                        appPrefsValues.setString(
                            AppPrefsValues.PREF_CAMERAS__URL[index]!!,
                            value)
                    }
                    "T" -> if (versionAccepted && index >= 1 && index <= numCam) {
                        appPrefsValues.setString(
                            AppPrefsValues.PREF_CAMERAS__TRANSFORM[index]!!,
                            value.replace('|', '\n'))
                    }
                }
            }
        }

        // TBD: Warn if the input was not matched.

        // Now navigate to the PrefsActivity to look at the result
        navToPrefs()
    }

    private fun navToPrefs() {
        val i = Intent(this, PrefsActivity::class.java)
        startActivity(i)
        finish()
    }

    private fun doShare() {
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
