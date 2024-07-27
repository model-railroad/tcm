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
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.BuildConfig
import com.alflabs.tcm.R
import com.alflabs.tcm.app.MonitorMixin
import com.alflabs.tcm.util.ILogger


class MainActivity : AppCompatActivity() {

    private lateinit var statusTxt: TextView
    private lateinit var monitorMixin: MonitorMixin
    lateinit var videoViewHolders: List<VideoViewHolder>
        private set

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private val DEBUG: Boolean = BuildConfig.DEBUG
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitorMixin = MonitorMixin(this)
        monitorMixin.onCreate()

        // enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
        //     val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        //     v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        //     insets
        // }


        val startBtn = findViewById<Button>(R.id.start_btn)
        val stopBtn = findViewById<Button>(R.id.stop_btn)
        val prefsBtn = findViewById<ImageButton>(R.id.prefs_btn)
        statusTxt = findViewById(R.id.status_text)

        videoViewHolders = listOf(
            VideoViewHolder(1, findViewById(R.id.video_cam1), findViewById(R.id.fps_cam1)),
            VideoViewHolder(2, findViewById(R.id.video_cam2), findViewById(R.id.fps_cam2)),
        )

        startBtn.setOnClickListener { onStartButton() }
        stopBtn.setOnClickListener { onStopButton() }
        prefsBtn.setOnClickListener { onPrefsButton() }

        if (DEBUG) addStatus("\n@@ ABIs: ${android.os.Build.SUPPORTED_ABIS.toList()}")
    }

    // Invoked after onCreate or onRestart
    override fun onStart() {
        super.onStart()
        monitorMixin.onStart()
    }

    // Invoked after onStart or onPause
    override fun onResume() {
        super.onResume()
        monitorMixin.onResume()
    }

    // Next state is either onResume or onStop
    override fun onPause() {
        super.onPause()
        monitorMixin.onPause()
    }

    // Next state is either onCreate > Start, onRestart > Start, or onDestroy
    override fun onStop() {
        super.onStop()
        monitorMixin.onStop()
    }

    // The end of the activity
    override fun onDestroy() {
        super.onDestroy()
        monitorMixin.onDestroy()
    }

    private fun onPrefsButton() {
        val i = Intent(this, PrefsActivity::class.java)
        startActivity(i)
    }

    private fun onStartButton() {
        if (DEBUG) Log.d(TAG, "@@ on START button")
        monitorMixin.onStartStreaming()
    }

    private fun onStopButton() {
        if (DEBUG) Log.d(TAG, "@@ on STOP button")
        monitorMixin.onStopStreaming()
    }

    fun addStatus(s : String) {
        Log.d(TAG, "Status: $s")
        statusTxt.text = statusTxt.text.toString() + s + "\n"
    }

    fun getLogger() : ILogger {
        return object : ILogger {
            override fun log(msg: String) {
                statusTxt.post {
                    addStatus(msg)
                }

            }

            override fun log(tag: String, msg: String) {
                statusTxt.post {
                    addStatus("$tag : $msg")
                }
            }
        }
    }
}
