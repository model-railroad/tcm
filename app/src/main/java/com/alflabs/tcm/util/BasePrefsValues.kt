/*
 * Project: Lib Utils
 * Copyright (C) 2010 alf.labs gmail com,
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
package com.alflabs.tcm.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

open class BasePrefsValues {
    val prefs: SharedPreferences

    constructor(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    constructor(prefs: SharedPreferences) {
        this.prefs = prefs
    }

    fun editLock(): Any {
        return BasePrefsValues::class.java
    }

    /** Returns a shared pref editor. Must call endEdit() later.  */
    fun startEdit(): SharedPreferences.Editor {
        return prefs.edit()
    }

    /** Commits an open editor.  */
    fun endEdit(editor: SharedPreferences.Editor) {
        editor.apply()
    }
}
