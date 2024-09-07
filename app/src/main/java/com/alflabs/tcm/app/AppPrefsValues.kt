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
import com.alflabs.tcm.activity.CamTransformValues
import com.alflabs.tcm.activity.CameraTransformPref
import com.alflabs.tcm.dagger.AppQualifier
import com.alflabs.tcm.util.BasePrefsValues
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrefsValues @Inject constructor(@AppQualifier context: Context) : BasePrefsValues(context) {

    /** Retrieve string for key or null.  */
    private fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    /** Sets or removes (null) a key string.  */
    fun setString(key: String, value: String?) {
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

    /** Retrieve camera transform for key or default.  */
    private fun getTransform(key: String): CamTransformValues {
        val s = getString(key)
        return CamTransformValues.parse(s ?: CameraTransformPref.STRING_DEFAULT)
    }

    fun systemDebugDisplay1() : Boolean = prefs.getBoolean(PREF_SYSTEM__DEBUG_DISPLAY_1, false)

    fun systemDebugDisplay2() : Boolean = prefs.getBoolean(PREF_SYSTEM__DEBUG_DISPLAY_2, false)

    fun systemStartOnBoot() : Boolean = prefs.getBoolean(PREF_SYSTEM__START_ON_BOOT, false)

    fun systemHideNavBar() : Boolean = prefs.getBoolean(PREF_SYSTEM__HIDE_NAV_BAR, false)

    fun systemDisconnectOnBattery() : Boolean = prefs.getBoolean(PREF_SYSTEM__ONLY_ON_AC_POWER, false)

    fun systemGA4ID() : String = prefs.getString(PREF_SYSTEM__GA4_ID, "") ?: ""

    fun camerasCount() = getInt(PREF_CAMERAS__COUNT, MonitorMixin.MAX_CAMERAS)

    fun camerasUrl(index: Int) : String =
        when (index) {
            1 -> prefs.getString(PREF_CAMERAS__URL_1, "") ?: ""
            2 -> prefs.getString(PREF_CAMERAS__URL_2, "") ?: ""
            3 -> prefs.getString(PREF_CAMERAS__URL_3, "") ?: ""
            else -> throw IndexOutOfBoundsException("Camera URL Index $index out of bounds")
        }

    fun camerasLabel(index: Int) : String =
        when (index) {
            1 -> prefs.getString(PREF_CAMERAS__LABEL_1, "") ?: ""
            2 -> prefs.getString(PREF_CAMERAS__LABEL_2, "") ?: ""
            3 -> prefs.getString(PREF_CAMERAS__LABEL_3, "") ?: ""
            else -> throw IndexOutOfBoundsException("Camera LABEL Index $index out of bounds")
        }

    fun camerasTransform(index: Int) : CamTransformValues =
        when (index) {
            1 -> getTransform(PREF_CAMERAS__TRANSFORM_1)
            2 -> getTransform(PREF_CAMERAS__TRANSFORM_2)
            3 -> getTransform(PREF_CAMERAS__TRANSFORM_3)
            else -> throw IndexOutOfBoundsException("Camera TRANSFORM Index $index out of bounds")
        }

    companion object {
        const val PREF_SYSTEM__DEBUG_DISPLAY_1 = "pref_system__debug_display1"
        const val PREF_SYSTEM__DEBUG_DISPLAY_2 = "pref_system__debug_display2"
        const val PREF_SYSTEM__HOME = "pref_system__home"
        const val PREF_SYSTEM__START_ON_BOOT = "pref_system__start_on_boot"
        const val PREF_SYSTEM__HIDE_NAV_BAR = "pref_system__hide_nav_bar"
        const val PREF_SYSTEM__ONLY_ON_AC_POWER = "pref_system__only_on_ac_power"
        const val PREF_SYSTEM__GA4_ID = "pref_system__ga4_id"
        const val PREF_CAMERAS__COUNT = "pref_cameras__count"
        const val PREF_CAMERAS__URL_1 = "pref_cameras__url_1"
        const val PREF_CAMERAS__URL_2 = "pref_cameras__url_2"
        const val PREF_CAMERAS__URL_3 = "pref_cameras__url_3"
        const val PREF_CAMERAS__LABEL_1 = "pref_cameras__label_1"
        const val PREF_CAMERAS__LABEL_2 = "pref_cameras__label_2"
        const val PREF_CAMERAS__LABEL_3 = "pref_cameras__label_3"
        const val PREF_CAMERAS__TRANSFORM_1 = "pref_cameras__transform_1"
        const val PREF_CAMERAS__TRANSFORM_2 = "pref_cameras__transform_2"
        const val PREF_CAMERAS__TRANSFORM_3 = "pref_cameras__transform_3"
    }
}
