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
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.alflabs.tcm.dagger.AppQualifier
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BatteryMonitorThread @Inject constructor(
    private val logger: ILogger,
    private val analytics: Analytics,
    @AppQualifier private val context: Context
): ThreadLoop() {

    companion object {
        private val TAG: String = BatteryMonitorThread::class.java.simpleName

        const val ON_POWER_PAUSE_MS   = 1000L * 5       // 5 seconds
        const val ON_BATTERY_PAUSE_MS = 1000L * 60      // 60 seconds

        const val HOURLY_REPORT_MS = 1000L * 60 * 60    // 1 hour
    }

    private val onStateChange = AtomicReference<OnBatteryStateChange>()
    private var isPlugged = false
    private var lastLevel = -1
    private var lastScale = -1
    private var nextHourlyReportTS = 0L

    override fun beforeThreadLoop() {
        isPlugged = isPlugged()
        logger.log(TAG, "beforeThreadLoop -- isPlugged $isPlugged")
    }

    override fun runInThreadLoop() {
        val newState = isPlugged()
        if (newState != isPlugged) {
            logger.log(TAG, "runInThreadLoop - isPlugged state changed $isPlugged --> $newState")
            isPlugged = newState
            sendOnStateChange(newState)
        }

        val nowMS = SystemClock.elapsedRealtime()
        if (nextHourlyReportTS < nowMS) {
            nextHourlyReportTS = nowMS + HOURLY_REPORT_MS

            if (lastScale > 0 && lastLevel >= 0) {
                val battPercent = (lastLevel.toFloat() * 100f / lastScale.toFloat()).toInt()
                logger.log(TAG, "Battery Percent: $battPercent%")
                analytics.sendEvent(
                    category = "TCM_BattPct",
                    action = if (isPlugged) "On" else "Off",
                    value = battPercent.toString()
                )
            }
        }

        try {
            Thread.sleep(if (isPlugged) ON_POWER_PAUSE_MS else ON_BATTERY_PAUSE_MS)
        } catch (ignore: Exception) {
            logger.log(TAG, "runInThreadLoop - interrupted")
            // This will be interrupted when thread wants to quit
        }
    }

    override fun afterThreadLoop() {
        logger.log(TAG, "afterThreadLoop")
    }

    fun requestInitialState() {
        isPlugged = isPlugged()
        sendOnStateChange(isPlugged)
    }

    fun setOnBatteryStateChange(callback: OnBatteryStateChange) {
        onStateChange.set(callback)
    }

    private fun sendOnStateChange(newState: Boolean) {
        if (!mQuit) {
            analytics.sendEvent(
                category = "TCM_Plugged",
                action = if (newState) "On" else "Off",
                value = "1"
            )

            val callback = onStateChange.get()
            callback?.invoke(newState)
        }
    }

    private fun isPlugged() : Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battStatus = context.registerReceiver(null, filter)
        // https://developer.android.com/reference/android/os/BatteryManager#EXTRA_PLUGGED is
        // 0 for battery or a bitmask for {AC, Dock, USB}. We don't care how the device is
        // charging as long as it is powered by something.
        val plugged = battStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        battStatus?.let {
            lastLevel = battStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            lastScale = battStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        }

        // logger.log(TAG, "@@ check isPlugged  = $plugged")  // DEBUG
        return plugged != 0
    }
}

typealias OnBatteryStateChange = (plugged: Boolean) -> Unit
