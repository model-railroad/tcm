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

    fun systemStartOnBoot() : Boolean = prefs.getBoolean(PREF_SYSTEM__START_ON_BOOT, true)

    fun camerasUrl(index: Int) : String =
        when (index) {
            1 -> camerasUrl1()
            2 -> camerasUrl2()
            else -> throw IndexOutOfBoundsException("Cameral URL Index $index out of bounds")
        }

    fun camerasUrl1() : String = prefs.getString(PREF_CAMERAS__URL_1, "") ?: ""

    fun camerasUrl2() : String = prefs.getString(PREF_CAMERAS__URL_2, "") ?: ""

    companion object {
        const val PREF_SYSTEM__START_ON_BOOT: String = "pref_system__start_on_boot"
        const val PREF_CAMERAS__URL_1: String = "url_camera_1"
        const val PREF_CAMERAS__URL_2: String = "url_camera_2"
    }
}
