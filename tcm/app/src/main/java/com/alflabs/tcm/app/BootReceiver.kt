/*
 * Project: RTAC
 * Copyright (C) 2017 alf.labs gmail com,
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alflabs.tcm.BuildConfig
import com.alflabs.tcm.activity.MainActivity

/**
 * Implements a Boot Receiver to start the MainActivity.
 *
 * Use of the Boot Receiver to start an activity from Background does not work
 * starting with API 29 (Android 10, Q). We can only use on 28 and below (Android 9, P).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private val TAG: String = BootReceiver::class.java.simpleName
        private val DEBUG: Boolean = BuildConfig.DEBUG

        const val MAX_USAGE_API = 28  // Android 9, P
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "onReceive: intent=$intent")

        val action = intent.action

        if (Intent.ACTION_BOOT_COMPLETED == action) {
            val prefsValues = AppPrefsValues(context)
            val bootAction = prefsValues.systemStartOnBoot()
            if (DEBUG) Log.d(TAG, "onReceive: boot action=$bootAction")

            if (bootAction) {
                val i = Intent(context, MainActivity::class.java)
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }
    }
}
