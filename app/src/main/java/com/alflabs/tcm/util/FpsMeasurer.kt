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
package com.alflabs.tcm.util

import android.os.SystemClock
import java.util.Locale

class FpsMeasurer {

    private var lastPingMS: Long = 0
    private var smoothedDeltaMS: Long = 0
    var lastFps = ""
        private set

    fun ping() {
        val nowMS = SystemClock.elapsedRealtime()

        if (lastPingMS > 0) {
            val deltaMS = nowMS - lastPingMS
            if (smoothedDeltaMS == 0L) {
                smoothedDeltaMS = deltaMS
            } else {
                smoothedDeltaMS = (3 * smoothedDeltaMS + deltaMS) / 4
            }

            if (smoothedDeltaMS > 0) {
                val fps: Double = 1000.0 / smoothedDeltaMS.toDouble()
                lastFps = String.format(Locale.US, "%.1f fps", fps)
            }
        }

        lastPingMS = nowMS
    }

}
