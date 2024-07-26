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
