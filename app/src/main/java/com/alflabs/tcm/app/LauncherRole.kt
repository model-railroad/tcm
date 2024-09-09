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

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.alflabs.tcm.dagger.AppQualifier
import com.alflabs.tcm.util.GlobalDebug
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a RoleManager HOME.
 *
 * This is only available starting with API 29 (Android 10 / Q).
 */
@Singleton
class LauncherRole @Inject constructor(
    @AppQualifier private val context: Context
) {

    companion object {
        private val TAG: String = LauncherRole::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val MIN_USAGE_API = 29  // Android 10, Q
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun isRoleAvailable() : Boolean {
        if (Build.VERSION.SDK_INT < MIN_USAGE_API) return false

        val manager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager?
        if (manager == null) return false

        return manager.isRoleAvailable(RoleManager.ROLE_HOME)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun isRoleHeld() : Boolean {
        if (Build.VERSION.SDK_INT < MIN_USAGE_API) return false

        val manager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager?
        if (manager == null) return false

        return manager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onRolePrefChanged(enabled: Boolean) : Intent? {
        if (Build.VERSION.SDK_INT < MIN_USAGE_API) return null

        if (!enabled) {
            // Intent to display the Android Settings > Default Apps screen.
            // Only available starting with API 24 (N).
            // This lets the user reset the launcher to the device's default, since there
            // no API to do that using the RoleManager.
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        // Intent to select a Home Launcher with the RoleManager
        val manager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager?
        if (manager == null) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

}
