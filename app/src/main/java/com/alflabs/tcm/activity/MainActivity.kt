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

import android.content.Context
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
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.app.MainApp
import com.alflabs.tcm.app.MonitorMixin
import com.alflabs.tcm.dagger.ActivityScope
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject


@ActivityScope
class MainActivity : AppCompatActivity() {

    private lateinit var mComponent: IMainActivityComponent
    @Inject internal lateinit var appPrefsValues: AppPrefsValues

    private var debugDisplay = false
    private lateinit var logger: ILogger
    private lateinit var statusTxt: TextView
    private lateinit var monitorMixin: MonitorMixin
    private lateinit var analytics: Analytics
    lateinit var videoViewHolders: List<VideoViewHolder>
        private set

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG


        fun getMainActivityComponent(context: Context): IMainActivityComponent {
            context as MainActivity
            return context.getComponent()
        }

    }

    // ---

    protected fun createComponent(): IMainActivityComponent {
        if (DEBUG) Log.d(TAG, "createComponent")
        return MainApp
            .getAppComponent(this)
            .mainActivityComponentFactory
            .create(ActivityContextModule(this))
    }

    fun getComponent(): IMainActivityComponent {
        if (DEBUG) Log.d(TAG, "getComponent")
        if (!this::mComponent.isInitialized) {
            mComponent = createComponent()
        }
        return mComponent
    }

    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        // Do not access dagger objects before this call
        getComponent().inject(this)

        analytics = Analytics()
        setUncaughtExceptionHandler()
        monitorMixin = MonitorMixin(this, analytics)
        monitorMixin.onCreate()

        // ComponentActivity.enableEdgeToEdge makes the top system/status bar transparent.
        // Note: an alternative is to set it via style theme resources as such:
        // <item name="android:statusBarColor">@android:color/transparent</item>
        // However by doing it programmatically we can respect the app preferences.
        if (appPrefsValues.systemHideNavBar()) {
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


        statusTxt = findViewById(R.id.status_text)

        val deferView = window.decorView
        videoViewHolders = listOf(
            VideoViewHolder(
                1,
                findViewById(R.id.container_cam1),
                findViewById(R.id.video_cam1),
                findViewById(R.id.label_cam1),
                findViewById(R.id.status_cam1),
                findViewById(R.id.fps_cam1)
                ) { action -> deferView.post(action) },
            VideoViewHolder(
                2,
                findViewById(R.id.container_cam2),
                findViewById(R.id.video_cam2),
                findViewById(R.id.label_cam2),
                findViewById(R.id.status_cam2),
                findViewById(R.id.fps_cam2),
                ) { action -> deferView.post(action) },
            VideoViewHolder(
                3,
                findViewById(R.id.container_cam3),
                findViewById(R.id.video_cam3),
                findViewById(R.id.label_cam3),
                findViewById(R.id.status_cam3),
                findViewById(R.id.fps_cam3),
            ) { action -> deferView.post(action) },
        )

        if (DEBUG) {
            addStatus("\n@@ API Level: ${android.os.Build.VERSION.SDK_INT}")
            addStatus("\n@@ ABIs: ${android.os.Build.SUPPORTED_ABIS.toList()}")
        }
    }

    private fun setUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Note: this may run a thread like thread FinalizerWatchdogDaemon.
            // The thread here has nothing to do with where the object was being used.
            Log.e(TAG, "@@ Uncaught Exception in thread ${thread.name}", exception)
            runOnUiThread {
                analytics.sendEvent(
                    category = "TCM",
                    action = "Exception",
                    label = "${thread.name} ${exception.javaClass.simpleName}"
                )
            }
        }
    }

    // Invoked after onCreate or onRestart
    override fun onStart() {
        if (DEBUG) Log.d(TAG, "onStart")
        super.onStart()

        // Note: Any UI that can be changed by editing preferences should be set/reset in
        // onStart rather than onCreate. onStart is called when coming back from PrefsActivity.

        // For debugging
        // prefs.setString("pref_system__ga4_id", // 'ga id | client id | app secret'

        analytics.setAnalyticsId(appPrefsValues)
        debugDisplay = appPrefsValues.systemDebugDisplay1()

        videoViewHolders.forEach { it.loadPrefs(appPrefsValues) }

        statusTxt.visibility = if (debugDisplay) View.VISIBLE else View.GONE
        if (!debugDisplay) statusTxt.text = ""

        val startBtn = findViewById<Button>(R.id.start_btn)
        val stopBtn = findViewById<Button>(R.id.stop_btn)
        val prefsBtn = findViewById<ImageButton>(R.id.prefs_btn)
        startBtn.visibility = if (debugDisplay) View.VISIBLE else View.GONE
        stopBtn .visibility = if (debugDisplay) View.VISIBLE else View.GONE
        startBtn.setOnClickListener { onStartButton() }
        stopBtn .setOnClickListener { onStopButton() }
        prefsBtn.setOnClickListener { onPrefsButton() }

        analytics.start()
        analytics.sendEvent(category = "TCM", action = "Start")
        monitorMixin.onStart()
    }

    // Invoked after onStart or onPause
    override fun onResume() {
        if (DEBUG) Log.d(TAG, "onResume")
        super.onResume()
        hideNavigationBar()
        monitorMixin.onResume()
    }

    // Next state is either onResume or onStop
    override fun onPause() {
        if (DEBUG) Log.d(TAG, "onPause")
        super.onPause()
        analytics.sendEvent(category = "TCM", action = "Pause")
        monitorMixin.onPause()
    }

    // Next state is either onCreate > Start, onRestart > Start, or onDestroy
    override fun onStop() {
        if (DEBUG) Log.d(TAG, "onStop")
        super.onStop()
        monitorMixin.onStop()
        analytics.stop()
    }

    // The end of the activity
    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        super.onDestroy()
        monitorMixin.onDestroy()
    }

    private fun onPrefsButton() {
        if (DEBUG) Log.d(TAG, "onPrefsButton")
        val i = Intent(this, PrefsActivity::class.java)
        startActivity(i)
    }

    private fun onStartButton() {
        if (DEBUG) Log.d(TAG, "onStartButton")
        monitorMixin.onStartStreaming()
    }

    private fun onStopButton() {
        if (DEBUG) Log.d(TAG, "onStopButton")
        monitorMixin.onStopStreaming()
    }

    fun addStatus(s : String) {
        Log.d(TAG, "Status: $s")
        if (!debugDisplay) return
        statusTxt.text = "$s\n${statusTxt.text}"
    }

    fun getLogger() : ILogger {
        if (!this::logger.isInitialized) {
            logger = object : ILogger {
                override fun log(tag: String, msg: String) {
                    if (!debugDisplay) {
                        Log.d(TAG, "Status: $msg")
                        return
                    }
                    statusTxt.post {
                        addStatus("$tag : $msg")
                    }
                }
            }
        }

        return logger
    }

    //----
    /** Enable immersion mode to hide the navigation bar  */
    @Suppress("DEPRECATION")
    private fun hideNavigationBar() {
        if (!appPrefsValues.systemHideNavBar()) return
        if (DEBUG) Log.d(TAG, "hideNavigationBar")

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
