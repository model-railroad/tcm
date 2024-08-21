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

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.alflabs.tcm.activity.VideoViewHolder
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import kotlin.math.max

class GrabbersManagerThread(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val camUrls: Map<Int, String>,
    private val viewHolders: List<VideoViewHolder>,
): ThreadLoop() {

    companion object {
        private val TAG: String = GrabbersManagerThread::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
        private const val SPIN = "◥◢◣◤"

        private const val GRAB_REFRESH_MS = 1000L           // 1 second
        private const val GRAB_TIMEOUT_MS = 1000L * 8       // 8 seconds
        private const val STAT_REPORT_MS  = 1000L * 60 * 10 // 10 minutes in milliseconds
        private const val GRAB_CLEANUP_MS = STAT_REPORT_MS
    }

    private val exGrabbers = mutableListOf<GrabberThread>()
    private var maxExGrabbersCount = 0

    private inner class CamThread(
        private val index : Int,
        val viewHolder: VideoViewHolder,
    ) {
        var grabber : GrabberThread? = null
        var render : CamRender? = null
        var pingRenderTS = 0L
        var pingActiveTS = 0L
        var maxDelayMS = 0L
        var currDelayMS = 0L
        var spin = 0
        var countNewRender = 0

        fun start() {
            if (DEBUG) Log.d(TAG, "CamThread $index > start")
            viewHolder.onStart()
            startNewGrabber()
        }

        private fun startNewGrabber() {
            if (DEBUG) Log.d(TAG, "CamThread $index > newGrabber")
            countNewRender++
            render = CamRender(this)
            grabber = GrabberThread(
                logger,
                analytics,
                index,
                camUrls[index]!!,
                render!!,
            )
            grabber?.start("Grabber-$index-$countNewRender")
        }

        private fun discardGrabber() {
            render?.isValid = false
            render = null
            grabber?.let {
                exGrabbers.add(it)
                it.stopRequested()
                grabber = null
            }
        }

        fun stopBlocking() {
            if (DEBUG) Log.d(TAG, "CamThread $index > stopBlocking")
            grabber?.let {
                grabber?.stop()
            }
            grabber = null
            discardGrabber()
            viewHolder.onStop()
        }

        fun pingRender() {
            pingRenderTS = SystemClock.elapsedRealtime()

            if (DEBUG) {
                viewHolder.setStatus(
                    "${SPIN[spin]} $countNewRender - ex ${exGrabbers.size}\n" +
                        "$currDelayMS < $maxDelayMS ms")
                spin = (spin + 1) % 4
            }
        }

        fun pingAlive() {
            // The GrabberThread is alive even though it's not rendering.
            // This happens when the grabber is waiting for a connection to establish.
            pingActiveTS = SystemClock.elapsedRealtime()
        }

        fun loop() {
            grabber?.let {
                // Don't try to timeout a grabber that is trying to open its stream
                if (it.isWaitingForFirstImage) return
            }
            val nowTS = SystemClock.elapsedRealtime()
            val latestPingTS = max(pingActiveTS, pingRenderTS)
            if (latestPingTS > 0) {
                val delayMS = nowTS - latestPingTS
                currDelayMS = delayMS
                maxDelayMS = max(maxDelayMS, nowTS - latestPingTS)
                if (delayMS > GRAB_TIMEOUT_MS) {
                    discardGrabber()
                    startNewGrabber()
                }
            }
        }

        fun sendStat(analytics: Analytics) {
            analytics.sendEvent(
                category = "TCM_Cam",
                action = "MaxDelayMS",
                label = index.toString(),
                value = maxDelayMS.toString())
            maxDelayMS = 0
        }
    }

    private class CamRender(private val camThread: CamThread) : IGrabberRenderer {
        @Volatile
        var isValid = true

        override fun render(bmp: Bitmap) {
            if (isValid) {
                camThread.pingRender()
                camThread.viewHolder.render(bmp)
            }
        }

        override fun setStatus(status: String) {
            if (isValid) {
                camThread.viewHolder.setStatus(status)
            }
        }

        override fun pingAlive() {
            if (isValid) {
                camThread.pingAlive()
            }
        }
    }

    override fun start() {
        if (DEBUG) Log.d(TAG, "start")
        super.start("GrabberManager")
    }

    override fun stop() {
        if (DEBUG) Log.d(TAG, "stop")
        super.stop(0)
    }

    override fun runInThreadLoop() {
        if (DEBUG) Log.d(TAG, "runInThreadLoop")

        val cams = camUrls.map { entry ->
            CamThread(
                index = entry.key,
                viewHolder = viewHolders[entry.key - 1],
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
                        category = "TCM",
                        action = "MaxExGrabbers",
                        value = maxExGrabbersCount.toString())
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
                    Thread.sleep(GRAB_REFRESH_MS)
                } catch (_ : Exception) {}
            }

            exGrabbers.forEach { it.stop(joinTimeoutMillis = 1000L * 10 ) }

        } finally {
            cams.forEach { it.stopBlocking() }
        }
    }

}
