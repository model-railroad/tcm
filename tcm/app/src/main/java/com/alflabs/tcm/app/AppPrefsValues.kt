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
    private fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    /** Sets or removes (null) a key string.  */
    private fun setString(key: String, value: String?) {
        synchronized(editLock()) {
            endEdit(startEdit().putString(key, value))
        }
    }

    /** Retrieve int for key or default.  */
    private fun getInt(key: String, defaultValue: Int): Int {
        try {
            // This may throw ClassCastException if camera count is not an Int preference.
            return prefs.getInt(key, defaultValue)
        } catch (ignore: Exception) { }
        return prefs.getString(key, defaultValue.toString())?.toIntOrNull()
            ?: defaultValue
    }

    /** Retrieve float for key or default.  */
    private fun getFloat(key: String, defaultValue: Float): Float {
        try {
            // This may throw ClassCastException if camera count is not an Int preference.
            return prefs.getFloat(key, defaultValue)
        } catch (ignore: Exception) { }
        return prefs.getString(key, defaultValue.toString())?.toFloatOrNull()
            ?: defaultValue
    }

    /** Retrieve float X;Y tuple for key or default.  */
    private fun getTuple(key: String, defaultValue: XYTuple): XYTuple {
        val s = getString(key)
        if (s == null) return defaultValue
        try {
            val splits = s.split(';')
            if (splits.size == 2) {
                return XYTuple(
                    splits[0].toFloatOrNull() ?: defaultValue.x,
                    splits[1].toFloatOrNull() ?: defaultValue.y,
                    )
            }
        } catch (ignore: Exception) { }
        return defaultValue
    }

    data class XYTuple(val x: Float, val y: Float)

    fun systemDebugDisplay() : Boolean = prefs.getBoolean(PREF_SYSTEM__DEBUG_DISPLAY, false)

    fun systemStartOnBoot() : Boolean = prefs.getBoolean(PREF_SYSTEM__START_ON_BOOT, false)

    fun systemHideNavBar() : Boolean = prefs.getBoolean(PREF_SYSTEM__HIDE_NAV_BAR, false)

    fun systemDisconnectOnBattery() : Boolean = prefs.getBoolean(PREF_SYSTEM__ONLY_ON_AC_POWER, false)

    fun systemWifiSSID() : String = prefs.getString(PREF_SYSTEM__WIFI_SSID, "") ?: ""

    fun camerasCount() = getInt(PREF_CAMERAS__COUNT, MonitorMixin.MAX_CAMERAS)

    fun camerasUrl(index: Int) : String =
        when (index) {
            1 -> prefs.getString(PREF_CAMERAS__URL_1, "") ?: ""
            2 -> prefs.getString(PREF_CAMERAS__URL_2, "") ?: ""
            else -> throw IndexOutOfBoundsException("Camera URL Index $index out of bounds")
        }

    fun camerasLabel(index: Int) : String =
        when (index) {
            1 -> prefs.getString(PREF_CAMERAS__LABEL_1, "") ?: ""
            2 -> prefs.getString(PREF_CAMERAS__LABEL_2, "") ?: ""
            else -> throw IndexOutOfBoundsException("Camera LABEL Index $index out of bounds")
        }

    fun camerasRotation(index: Int) : Int =
        when (index) {
            1 -> getInt(PREF_CAMERAS__ROTATION_1, 0)
            2 -> getInt(PREF_CAMERAS__ROTATION_2, 0)
            else -> throw IndexOutOfBoundsException("Camera Rotation Index $index out of bounds")
        }

    fun camerasZoom(index: Int) : Float =
        when (index) {
            1 -> getFloat(PREF_CAMERAS__ZOOM_1, 1.0f)
            2 -> getFloat(PREF_CAMERAS__ZOOM_2, 1.0f)
            else -> throw IndexOutOfBoundsException("Camera Zoom Index $index out of bounds")
        }

    fun camerasOffset(index: Int) : XYTuple =
        when (index) {
            1 -> getTuple(PREF_CAMERAS__OFFSET_1, XYTuple(0f, 0f))
            2 -> getTuple(PREF_CAMERAS__OFFSET_2, XYTuple(0f, 0f))
            else -> throw IndexOutOfBoundsException("Camera Offset Index $index out of bounds")
        }

    companion object {
        const val PREF_SYSTEM__DEBUG_DISPLAY = "pref_system__debug_display"
        const val PREF_SYSTEM__HOME = "pref_system__home"
        const val PREF_SYSTEM__START_ON_BOOT = "pref_system__start_on_boot"
        const val PREF_SYSTEM__HIDE_NAV_BAR = "pref_system__hide_nav_bar"
        const val PREF_SYSTEM__WIFI_SSID = "pref_system__wifi_ssid"
        const val PREF_SYSTEM__ONLY_ON_AC_POWER = "pref_system__only_on_ac_power"
        const val PREF_CAMERAS__COUNT = "pref_cameras__count"
        const val PREF_CAMERAS__URL_1 = "pref_cameras__url_1"
        const val PREF_CAMERAS__URL_2 = "pref_cameras__url_2"
        const val PREF_CAMERAS__LABEL_1 = "pref_cameras__label_1"
        const val PREF_CAMERAS__LABEL_2 = "pref_cameras__label_2"
        const val PREF_CAMERAS__ROTATION_1 = "pref_cameras__rotation_1"
        const val PREF_CAMERAS__ROTATION_2 = "pref_cameras__rotation_2"
        const val PREF_CAMERAS__ZOOM_1 = "pref_cameras__zoom_1"
        const val PREF_CAMERAS__ZOOM_2 = "pref_cameras__zoom_2"
        const val PREF_CAMERAS__OFFSET_1 = "pref_cameras__offset_1"
        const val PREF_CAMERAS__OFFSET_2 = "pref_cameras__offset_2"
    }
}
