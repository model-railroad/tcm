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
package com.alflabs.tcm.app

import android.util.Log
import com.alflabs.tcm.activity.MainActivity
import com.alflabs.tcm.record.GrabbersManagerThread
import com.alflabs.tcm.util.AVUtils
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug

/**
 * This is the main core "Monitor" of the TCM application.
 *
 * There is a single instance of this held by the MainActivity during its
 * lifetime. It handles all the monitoring lifecycle -- deciding when to connect,
 * and when to disconnect (due to power saving, or the activity finishing).
 *
 * This is an "activity mixin", tightly connected to the MainActivity lifecycle.
 */
class MonitorMixin(
    private val activity: MainActivity,
    private val analytics: Analytics,
    ) {

    companion object {
        private val TAG: String = MonitorMixin::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val MAX_CAMERAS = 3

        const val START_AUTOMATICALLY = true        // for debugging purposes

        // From https://www.ffmpeg.org/doxygen/4.0/group__lavu__log__constants.html
        const val AV_LOG_TRACE = 56
    }

    private var camerasCount : Int = 0
    private var grabbersManager : GrabbersManagerThread? = null
    private var batteryMonitorThread : BatteryMonitorThread? = null

    fun onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate")
    }

    // Invoked after onCreate or onRestart
    fun onStart() {
        if (DEBUG) Log.d(TAG, "onStart")

        AVUtils.instance.setFFmpegLogCallback(activity.getLogger())

        val prefs = AppPrefsValues(activity)
        camerasCount = prefs.camerasCount()

        if (prefs.systemDisconnectOnBattery() && batteryMonitorThread == null) {
            batteryMonitorThread = BatteryMonitorThread(
                activity.getLogger(),
                analytics,
                activity) {
                    isPlugged -> activity.runOnUiThread {
                    when(isPlugged) {
                        true -> onStartStreaming()
                        false -> onStopStreaming()
                    }
                }
            }
        }

        for (index in 1..MAX_CAMERAS) {
            activity.videoViewHolders[index - 1].setVisible(index <= camerasCount)
        }

    }

    // Invoked after onStart or onPause
    fun onResume() {
        if (DEBUG) Log.d(TAG, "onResume")
        if (batteryMonitorThread != null) {
            batteryMonitorThread?.start()
            batteryMonitorThread?.requestInitialState()
        } else if (START_AUTOMATICALLY) {
            onStartStreaming()
        }
    }

    // Next state is either onResume or onStop
    fun onPause() {
        if (DEBUG) Log.d(TAG, "onPause")
        batteryMonitorThread?.stopSync()
        onStopStreaming()
    }

    // Next state is either onCreate > Start, onRestart > Start, or onDestroy
    fun onStop() {
        if (DEBUG) Log.d(TAG, "onStop")
    }

    // The end of the activity
    fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
    }

    fun onStartStreaming() {
        if (DEBUG) Log.d(TAG, "onStartStreaming")
        grabbersManager?.stopSync()

        val prefs = AppPrefsValues(activity)

        val camUrls = buildMap<Int, String> {
            for (index in 1..camerasCount) {
                put(index, prefs.camerasUrl(index))
            }
        }


        var debugDisplay = prefs.systemDebugDisplay2()

        grabbersManager = GrabbersManagerThread(
            activity.getLogger(),
            analytics,
            debugDisplay,
            camUrls,
            activity.videoViewHolders)

        grabbersManager?.start()
    }

    fun onStopStreaming() {
        if (DEBUG) Log.d(TAG, "onStopStreaming")

        grabbersManager?.stopSync()
        grabbersManager = null

        val prefs = AppPrefsValues(activity)
        val camerasCount = prefs.camerasCount()
        for (index in 1..camerasCount) {
            activity.videoViewHolders[index - 1].onStop()
        }
    }
}
