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

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConnectivityManager Network Request to enforce that the proper Wifi network is active.
 *
 * TBD -- This is WIP / experimental, and not functional yet.
 */
@Singleton
class AppNetworkRequest @Inject constructor(
    private val logger: ILogger,
    private val connectivityManager: ConnectivityManager,
) {
    companion object {
        private val TAG: String = AppNetworkRequest::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val MIN_USAGE_API = 29  // Android 10, Q
    }

    private var callback: NetworkCallback? = null
    private var ssid: String = ""

    fun acquire() {
        if (Build.VERSION.SDK_INT >= MIN_USAGE_API) {
            acquireApi29()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun acquireApi29() {
        if (callback != null) return
        // TBD read from prefs...
        // Note: this is currently NOT working and is a no-op. Experimentation in progress.
        val prefsSSID = ""
        if (prefsSSID.isEmpty()) return
        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(prefsSSID)
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                //.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val newCallback = createCallback()
            connectivityManager.requestNetwork(request, createCallback())
            callback = newCallback
            ssid = prefsSSID
            logger.log(TAG, "Network Requested: $request")
        } catch (e: Throwable) {
            Log.e(TAG, "Network Requested failed", e)
        }
    }

    fun release() {
        if (callback == null) return
        try {
            logger.log(TAG, "Network Requested Release")
            connectivityManager.unregisterNetworkCallback(callback!!)
        } catch (e: Throwable) {
            Log.e(TAG, "Network Requested failed", e)
        }
        ssid = ""
        callback = null
    }

    fun createCallback(): NetworkCallback {
        return object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                logger.log(TAG, "onAvailable: $network")
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                super.onLosing(network, maxMsToLive)
                logger.log(TAG, "onLosing: $network")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                logger.log(TAG, "onLost: $network")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                logger.log(TAG, "onUnavailable")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                logger.log(TAG, "onCapsChanged: $network -> $networkCapabilities")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                logger.log(TAG, "onLinkPropertiesChanged: $network -> $linkProperties")
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                super.onBlockedStatusChanged(network, blocked)
                logger.log(TAG, "onBlockedStatusChanged: $network -> $blocked")
            }
        }
    }

}
