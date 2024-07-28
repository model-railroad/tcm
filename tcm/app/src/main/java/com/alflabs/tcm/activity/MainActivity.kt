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
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.BuildConfig
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
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


        // ComponentActivity.enableEdgeToEdge makes the top system/status bar transparent.
        // Note: an alternative is to set it via style theme resources as such:
        // <item name="android:statusBarColor">@android:color/transparent</item>
        // However by doing it programmatically we can respect the app preferences.
        val prefs = AppPrefsValues(this)
        if (prefs.systemHideNavBar()) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.argb(0x40, 0x20, 0x20, 0x20))
            )
        }

        setContentView(R.layout.activity_main)

        // The following sample code _could_ be used if we wanted to offset all views to fit
        // below the transparent system/status bar. However we want the video there so that's
        // exactly what we do not want in this app.
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
            VideoViewHolder(
                1,
                findViewById(R.id.video_cam1),
                findViewById(R.id.status_cam1),
                findViewById(R.id.fps_cam1),
                ),
            VideoViewHolder(
                2,
                findViewById(R.id.video_cam2),
                findViewById(R.id.status_cam2),
                findViewById(R.id.fps_cam2),
                ),
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
        hideNavigationBar()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        monitorMixin.wakeWifiLockHandler.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    //----
    /** Enable immersion mode to hide the navigation bar  */
    private fun hideNavigationBar() {
        val prefs = AppPrefsValues(this)
        if (!prefs.systemHideNavBar()) return

        val root = window.decorView
        val visibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // Note: combine with API 21 style.xml to give the nav bar a translucent background
        // to allow the nav bar to show up on top of the layout without resizing it.
        // Combine with onApplyWindowInsets() above to let the layout cover the nav bar area.
        if (DEBUG) Log.d(TAG, "@@ Initial setSystemUiVisibility to $visibility")
        root.systemUiVisibility = visibility
        root.setOnSystemUiVisibilityChangeListener { newVisibility: Int ->
            if (DEBUG) Log.d(TAG, "@@ onSystemUiVisibilityChange: visibility=$newVisibility")
            if ((newVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                root.postDelayed({
                    if (DEBUG) Log.d(TAG, "@@ onSystemUiVisibilityChange: reset visibility to $visibility")
                    root.systemUiVisibility = visibility
                }, (3 * 1000).toLong() /*ms*/)
            }
        }
    }

}
