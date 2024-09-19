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
package com.alflabs.tcm.record

import android.os.SystemClock
import android.util.Log
import com.alflabs.tcm.activity.VideoViewHolder
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import kotlin.math.max

/** Live management of a [GrabberThread] pool. */
class GrabbersManagerThread(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val debugDisplay: Boolean,
    private val camUrls: Map<Int, String>,
    private val viewHolders: List<VideoViewHolder>,
): ThreadLoop() {

    companion object {
        private val TAG: String = GrabbersManagerThread::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        private  const val GRAB_REFRESH_MS = 1000L           // 1 second
        internal const val GRAB_TIMEOUT_MS = 1000L * 8       // 8 seconds
        private  const val STAT_REPORT_MS  = 1000L * 60 * 10 // 10 minutes in milliseconds
        private  const val GRAB_CLEANUP_MS = STAT_REPORT_MS
    }

    private val exGrabbers = mutableListOf<GrabberThread>()
    private var maxExGrabbersCount = 0

    override fun start() {
        if (DEBUG) Log.d(TAG, "start")
        super.start("GrabberManager")
    }

    override fun requestStopAsync() {
        if (DEBUG) Log.d(TAG, "requestStopAsync")
        super.requestStopAsync()
    }

    override fun stopSync(joinTimeoutMillis: Long) {
        if (DEBUG) Log.d(TAG, "stopSync")
        super.stopSync(joinTimeoutMillis)
    }

    override fun runInThreadLoop() {
        if (DEBUG) Log.d(TAG, "runInThreadLoop")

        val cams = camUrls.map { entry ->
            GrabberHolder(
                logger = logger,
                analytics = analytics,
                debugDisplay = debugDisplay,
                camUrls = camUrls,
                index = entry.key,
                viewHolder = viewHolders[entry.key - 1],
                exGrabbers = exGrabbers,
                )
        }

        try {
            cams.forEach { it.start() }

            var nowMS = SystemClock.elapsedRealtime()
            var sendStatsMS = nowMS + STAT_REPORT_MS
            var grabCleanupMS = nowMS + GRAB_CLEANUP_MS

            while (!mQuit) {
                cams.forEach { it.loop() }

                nowMS = SystemClock.elapsedRealtime()
                if (nowMS >= sendStatsMS) {
                    cams.forEach { it.sendStat(analytics) }

                    analytics.sendEvent(
                        category = "TCM_Hourly",
                        name = "MaxExGrabbers",
                        value = maxExGrabbersCount)
                    maxExGrabbersCount = 0

                    sendStatsMS = nowMS + STAT_REPORT_MS
                }
                if (nowMS >= grabCleanupMS) {
                    if (exGrabbers.isNotEmpty()) {
                        val size = exGrabbers.size
                        if (DEBUG) Log.d(TAG, "exGrabbers: $size")
                        maxExGrabbersCount = max(maxExGrabbersCount, size)
                        exGrabbers.removeIf { it.isLoopFinished }
                    }
                    grabCleanupMS = nowMS + GRAB_CLEANUP_MS
                }

                try {
                    if (!mQuit) {
                        Thread.sleep(GRAB_REFRESH_MS)
                    }
                } catch (e : Exception) {
                    if (DEBUG) Log.d(TAG, "ThreadLoop: $e")
                }
            }

            cams.forEach { it.discardGrabber() }

            exGrabbers.forEach { it.stopSync(joinTimeoutMillis = 1000L * 10 ) }

        } finally {
            cams.forEach { it.stopBlocking() }
        }
    }

}
