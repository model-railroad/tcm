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

import android.content.Context
import android.util.Log
import com.alflabs.tcm.activity.MainActivity
import com.alflabs.tcm.app.MonitorMixin.Companion.MAX_CAMERAS
import com.alflabs.tcm.dagger.AppContext
import com.alflabs.tcm.record.GrabbersManagerThread
import com.alflabs.tcm.util.AVUtils
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMonitor @Inject constructor(
    private var logger: ILogger,
    private var analytics: Analytics,
    private var appPrefsValues: AppPrefsValues,
    @AppContext internal var appContext: Context
) {
    companion object {
        private val TAG: String = AppMonitor::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
    }


    private var grabbersManager : GrabbersManagerThread? = null
    private var batteryMonitorThread : BatteryMonitorThread? = null
    private var startStreamingRunnable : Runnable? = null
    private var stopStreamingRunnable : Runnable? = null
    var activityResumed = false
        private set

    /**
     * The Application object has been created.
     *
     * Note that there is no way to know when the app "stops" as the
     * process is mostly killed directly.
     */
    fun onAppCreate() {
        if (DEBUG) Log.d(TAG, "onAppCreate")
        AVUtils.instance.setFFmpegLogCallback(logger)

        analytics.setAnalyticsId(appPrefsValues)
        analytics.start()
    }

    /**
     * The main activity has been started and/or resumed.
     */
    fun onActivityResume(activity: MainActivity) {
        activityResumed = true

        // The Analytics ID could have changed (e.g. if the Pref activity has been used in between).
        analytics.setAnalyticsId(appPrefsValues)

        if (appPrefsValues.systemDisconnectOnBattery() && batteryMonitorThread == null) {
            batteryMonitorThread = BatteryMonitorThread(
                logger,
                analytics,
                appContext) {
                    isPlugged -> activity.runOnUiThread {
                        when(isPlugged) {
                            true -> startStreamingRunnable?.run()
                            false -> stopStreamingRunnable?.run()
                        }
                    }
                }

            batteryMonitorThread?.start()
        }

        val camerasCount = appPrefsValues.camerasCount()
        for (index in 1..MAX_CAMERAS) {
            activity.videoViewHolders[index - 1].setVisible(index <= camerasCount)
        }

        startStreamingRunnable = Runnable {
            if (DEBUG) Log.d(TAG, "onStartStreaming")
            grabbersManager?.stopSync()

            val camUrls = buildMap<Int, String> {
                for (index in 1..camerasCount) {
                    put(index, appPrefsValues.camerasUrl(index))
                }
            }


            val debugDisplay = appPrefsValues.systemDebugDisplay2()

            grabbersManager = GrabbersManagerThread(
                logger,
                analytics,
                debugDisplay,
                camUrls,
                activity.videoViewHolders)

            grabbersManager?.start()

        }

        stopStreamingRunnable = Runnable {
            if (DEBUG) Log.d(TAG, "onStopStreaming")

            grabbersManager?.requestStopAsync()
            grabbersManager = null

            for (index in 1..camerasCount) {
                activity.videoViewHolders[index - 1].onStop()
            }

            // This is a one-shot operation.
            stopStreamingRunnable = null
        }

        // Start when battery is connected or immediately if not monitoring.
        if (batteryMonitorThread != null) {
            batteryMonitorThread?.requestInitialState()
        } else {
            startStreamingRunnable?.run()
        }
    }

    /**
     * The main activity is being paused.
     *
     * At this point, we should relinquish anything that has a reference to the activity
     * or its context to avoid memory leaks. We can also assume the app may be killed at
     * any moment, or goes back to [onActivityResume].
     */
    fun onActivityPause() {
        // One pause, we tear down existing process threads. However we only request them
        // to stop and then clear references (to reduce memory leaks) but we do not *wait*
        // for them.
        batteryMonitorThread?.requestStopAsync()
        batteryMonitorThread = null

        stopStreamingRunnable?.run()
        activityResumed = false
    }

}
