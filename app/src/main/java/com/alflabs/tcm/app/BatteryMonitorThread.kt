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
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop


class BatteryMonitorThread(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val context: Context,
    private val onStateChange: (Boolean)->Unit,
): ThreadLoop() {

    companion object {
        private val TAG: String = BatteryMonitorThread::class.java.simpleName

        const val ON_POWER_PAUSE_MS   = 1000L * 5  // 5 seconds
        const val ON_BATTERY_PAUSE_MS = 1000L * 30  // 30 seconds -- TBD change to 1 min in prod
    }

    @Volatile
    private var isPlugged = false

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

    private fun sendOnStateChange(newState: Boolean) {
        if (!mQuit) {
            analytics.sendEvent(
                category = "TCM_Plugged",
                action = if (newState) "On" else "Off",
                value = "1"
            )

            onStateChange(newState)
        }
    }

    private fun isPlugged() : Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battStatus = context.registerReceiver(null, filter)
        // https://developer.android.com/reference/android/os/BatteryManager#EXTRA_PLUGGED is
        // 0 for battery or a bitmask for {AC, Dock, USB}. We don't care how the device is
        // charging as long as it is powered by something.
        val plugged = battStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        // logger.log(TAG, "@@ check isPlugged  = $plugged")  // DEBUG
        return plugged != 0
    }
}
