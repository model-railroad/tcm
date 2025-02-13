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

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alflabs.tcm.activity.MainActivity
import com.alflabs.tcm.record.GrabbersManagerThread
import com.alflabs.tcm.util.AVUtils
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMonitor @Inject constructor(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val appWakeLock: AppWakeLock,
    private val appWifiLock: AppWifiLock,
    private val appPrefsValues: AppPrefsValues,
    private val batteryMonitorThread : BatteryMonitorThread,
) {
    companion object {
        private val TAG: String = AppMonitor::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val MAX_CAMERAS = 3

        const val START_AUTOMATICALLY = true        // for debugging purposes

    }

    private lateinit var appHandler : Handler
    private var grabbersManager : GrabbersManagerThread? = null
    private var startStreamingRunnable : Runnable? = null
    private var stopStreamingRunnable : Runnable? = null
    private var actOnBatteryStateChanged = false
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

        appHandler = Handler(Looper.getMainLooper()) { msg -> true }

        batteryMonitorThread.setOnBatteryStateChange { isPlugged ->
            // This executes on the BatteryMonitorThread
            if (actOnBatteryStateChanged) {
                when (isPlugged) {
                    true -> startStreamingRunnable?.let { appHandler.post(it) }
                    false -> stopStreamingRunnable?.let { appHandler.post(it) }
                }
            }
        }
        batteryMonitorThread.start()
    }

    /**
     * The main activity has been started and/or resumed.
     */
    fun onActivityResume(activity: MainActivity) {
        activityResumed = true

        // The Analytics ID could have changed (e.g. if the Pref activity has been used in between).
        analytics.setAnalyticsId(appPrefsValues)
        actOnBatteryStateChanged = appPrefsValues.systemDisconnectOnBattery()

        val camerasCount = appPrefsValues.camerasCount()
        for (index in 1..MAX_CAMERAS) {
            activity.videoViewHolders[index - 1].setVisible(index <= camerasCount)
        }

        startStreamingRunnable = Runnable {
            // This executes in the appHandler on the main app UI thread.
            logger.log(TAG, "onStartStreaming")
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
            appWakeLock.acquire()
            appWifiLock.acquire()
        }

        stopStreamingRunnable = Runnable {
            // This executes in the appHandler on the main app UI thread.
            logger.log(TAG, "onStopStreaming")

            appWifiLock.release()
            appWakeLock.release()
            grabbersManager?.requestStopAsync()
            grabbersManager = null

            for (index in 1..camerasCount) {
                activity.videoViewHolders[index - 1].onStop()
            }
        }

        // Start when battery is connected or immediately if not monitoring.
        if (actOnBatteryStateChanged) {
            batteryMonitorThread.requestInitialState()
        } else if (START_AUTOMATICALLY) {
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
        stopStreamingRunnable?.let { appHandler.post(it) }
        startStreamingRunnable = null
        stopStreamingRunnable = null
        activityResumed = false
    }

}
