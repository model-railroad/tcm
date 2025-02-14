/*
 * Project: TCM
 * Copyright (C) 2025 alf.labs gmail com,
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

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject
import javax.inject.Singleton

/** Wifi lock management -- active with the main activity. */
@Singleton
class AppWifiLock @Inject constructor(
    private val logger: ILogger,
    private val wifiManager: WifiManager,
) {
    companion object {
        private val TAG: String = AppWifiLock::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
    }

    private var wifiLock: WifiManager.WifiLock? = null

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (wifiLock != null) return
        try {
            wifiLock = wifiManager.createWifiLock(
                // WIFI_MODE_FULL_LOW_LATENCY replaces WIFI_MODE_FULL on API 29+.
                if (Build.VERSION.SDK_INT >= 29)
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                else
                    WifiManager.WIFI_MODE_FULL,
                "tcl:appWifiLock")
            wifiLock?.acquire()
            logger.log(TAG, "Wifi Lock Acquire")
        } catch (e: Throwable) {
            Log.e(TAG, "Wifi Lock Acquire failed", e)
        }
    }

    fun release() {
        if (wifiLock == null) return
        try {
            logger.log(TAG, "Wifi Lock Release")
            wifiLock?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Wifi Lock Release failed", e)
        }
        wifiLock = null
    }

}
