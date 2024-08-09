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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.alflabs.tcm.BuildConfig
import com.alflabs.tcm.activity.MainActivity
import com.alflabs.tcm.util.GlobalDebug

class WakeWifiLockHandler(private val activity: MainActivity) {

    companion object {
        private val TAG: String = WakeWifiLockHandler::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val MAX_API_WIFI_SSID = 28 // Android 9/P

        const val ACTIVITY_REQUEST_FOR_WIFI_PERMISSION = 100;
    }

    private var powerManager: PowerManager? = null
    private var wifiManager: WifiManager? = null
    private var wakeLock: WakeLock? = null
    private var wifiLock: WifiLock? = null

    fun onCreate() {
        powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager?
        wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    }

    @SuppressLint("WakelockTimeout")
    fun lockWake() {
        val pm = powerManager ?: return
        if (wakeLock != null) return
        try {
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TrackCamMonitor:WakeLock"
            )
            wakeLock?.acquire()
            if (DEBUG) Log.d(TAG, "Wake Lock Acquire")
        } catch (e: Throwable) {
            Log.e(TAG, "Wake Lock Acquire failed", e)
        }
    }

    fun releaseWake() {
        try {
            if (DEBUG) Log.d(TAG, "Wake Lock Release")
            wakeLock?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Wake Lock Release failed", e)
        }
        wakeLock = null
    }

    fun lockWifi() {
        val wm = wifiManager ?: return
        if (wifiLock != null) return
        try {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Track Cam Monitor")
            wifiLock?.acquire()
            if (DEBUG) Log.d(TAG, "Wifi Lock Acquire")
        } catch (e: Throwable) {
            Log.e(TAG, "Wifi Lock Acquire failed", e)
        }
    }

    fun releaseWifi() {
        try {
            if (DEBUG) Log.d(TAG, "Wifi Lock Release")
            wifiLock?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Wifi Lock Release failed", e)
        }
        wifiLock = null
    }

    fun enableSelectedWifiNetwork() {
        // Enabling or Reconfiguring Wifi is not supported after API 29 (Q)
        if (Build.VERSION.SDK_INT > MAX_API_WIFI_SSID) return
        val wm = wifiManager ?: return
        val prefs = AppPrefsValues(activity)
        val ssid: String = prefs.systemWifiSSID().trim()
        if (DEBUG) Log.d(TAG, "enableSelectedWifiNetwork, checking for SSID: $ssid")

        if (ssid.isEmpty()) return

        val quoted_ssid = "\"$ssid\""

        // This is no longer supported after Android Q / 10 / 29
        if (!wm.isWifiEnabled) {
            if (DEBUG) Log.d(TAG, "Wifi, Not enabled. Try to enable.")
            wm.setWifiEnabled(true)
        }

        // Check current state.
        val wifiInfo: WifiInfo? = wm.getConnectionInfo()
        if (wifiInfo != null &&
            (ssid == wifiInfo.ssid || quoted_ssid == wifiInfo.ssid)
        ) {
            // Already connected.
            if (DEBUG) Log.d(TAG, "Wifi, Current info: $wifiInfo")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            if (DEBUG) Log.d(TAG, "Wifi, Permission ACCESS_FINE_LOCATION not granted yet")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                ACTIVITY_REQUEST_FOR_WIFI_PERMISSION
            )
            return
        }

        for (configuration in wm.getConfiguredNetworks()) {
            if (ssid == configuration.SSID || quoted_ssid == configuration.SSID) {
                if (DEBUG) Log.d(TAG, "Wifi, Enable SSID: " + configuration.SSID)
                wm.enableNetwork(configuration.networkId, true /*attempConnect*/)
                break
            }
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (DEBUG) Log.d(TAG, "onRequestPermissionsResult: " +
                "requestCode $requestCode, permissions $permissions, grantResults $grantResults")
        if (requestCode == ACTIVITY_REQUEST_FOR_WIFI_PERMISSION) {
            enableSelectedWifiNetwork()
        }
    }
}
