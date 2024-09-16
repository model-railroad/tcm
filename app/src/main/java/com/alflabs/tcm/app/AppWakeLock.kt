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

import android.annotation.SuppressLint
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppWakeLock @Inject constructor(
    private val logger: ILogger,
    private val powerManager: PowerManager,
) {
    companion object {
        private val TAG: String = AppWakeLock::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val USE_KEEP_SCREEN_ON = false
    }

    private var mWakeLock: WakeLock? = null

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (mWakeLock != null) return
        try {
            mWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "tcm:appWakeLock")
            mWakeLock?.acquire()
            logger.log(TAG, "Wake Lock Acquire")
        } catch (e: Throwable) {
            Log.e(TAG, "Wake Lock Acquire failed", e)
        }
    }

    fun release() {
        if (mWakeLock == null) return
        try {
            logger.log(TAG, "Wake Lock Release")
            mWakeLock?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Wake Lock Release failed", e)
        }
        mWakeLock = null
    }

}
