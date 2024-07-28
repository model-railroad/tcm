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
import com.alflabs.tcm.util.BasePrefsValues

class AppPrefsValues(context: Context) : BasePrefsValues(context) {

    /** Retrieve string for key or null.  */
    fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    /** Sets or removes (null) a key string.  */
    fun setString(key: String, value: String?) {
        synchronized(editLock()) {
            endEdit(startEdit().putString(key, value))
        }
    }

    fun systemStartOnBoot() : Boolean = prefs.getBoolean(PREF_SYSTEM__START_ON_BOOT, false)

    fun systemHideNavBar() : Boolean = prefs.getBoolean(PREF_SYSTEM__HIDE_NAV_BAR, false)

    fun systemWifiSSID() : String = prefs.getString(PREF_SYSTEM__WIFI_SSID, "") ?: ""

    fun camerasUrl(index: Int) : String =
        when (index) {
            1 -> camerasUrl1()
            2 -> camerasUrl2()
            else -> throw IndexOutOfBoundsException("Camera URL Index $index out of bounds")
        }

    fun camerasUrl1() : String = prefs.getString(PREF_CAMERAS__URL_1, "") ?: ""

    fun camerasUrl2() : String = prefs.getString(PREF_CAMERAS__URL_2, "") ?: ""

    fun camerasCount() : Int {
        try {
            // This may throw ClassCastException if camera count is not an Int preference.
            return prefs.getInt(PREF_CAMERAS__COUNT, MonitorMixin.MAX_CAMERAS)
        } catch (ignore: Exception) { }
        try {
            // This will throw if String is not a valid Int representation
            return prefs.getString(PREF_CAMERAS__COUNT, MonitorMixin.MAX_CAMERAS.toString())?.toInt()
                ?: MonitorMixin.MAX_CAMERAS
        } catch (ignore: Exception) { }
        return MonitorMixin.MAX_CAMERAS
    }

    companion object {
        const val PREF_SYSTEM__START_ON_BOOT = "pref_system__start_on_boot"
        const val PREF_SYSTEM__HOME = "pref_system__home"
        const val PREF_SYSTEM__HIDE_NAV_BAR = "pref_system__hide_nav_bar"
        const val PREF_SYSTEM__WIFI_SSID = "pref_system__wifi_ssid"
        const val PREF_CAMERAS__COUNT = "pref_cameras__count"
        const val PREF_CAMERAS__URL_1 = "pref_cameras__url_1"
        const val PREF_CAMERAS__URL_2 = "pref_cameras__url_2"
    }
}
