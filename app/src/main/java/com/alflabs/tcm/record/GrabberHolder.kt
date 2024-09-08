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
import kotlin.math.max

/** Manages a single [GrabberThread] and holds all state needed to manage the thread. */
class GrabberHolder(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val debugDisplay: Boolean,
    private val camUrls: Map<Int, String>,
    private val index : Int,
    internal val viewHolder: VideoViewHolder,
    private val exGrabbers: MutableList<GrabberThread>,
) {

    companion object {
        private val TAG: String = GrabberHolder::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
        private const val SPIN = "◥◢◣◤"
    }

    private var grabber : GrabberThread? = null
    private var render : GrabberRender? = null
    private var pingRenderTS = 0L
    private var pingActiveTS = 0L
    private var maxDelayMS = 0L
    private var currDelayMS = 0L
    private var spin = 0
    private var countNewRender = 0

    fun start() {
        if (DEBUG) Log.d(TAG, "CamThread $index > start")
        viewHolder.onStart()
        startNewGrabber()
    }

    private fun startNewGrabber() {
        if (DEBUG) Log.d(TAG, "CamThread $index > newGrabber")
        countNewRender++
        render = GrabberRender(this)
        grabber = GrabberThread(
            logger,
            analytics,
            index,
            camUrls[index]!!,
            render!!,
        )
        grabber?.start("Grabber-$index-$countNewRender")
    }

    internal fun discardGrabber() {
        render?.setValid(false)
        render = null
        grabber?.let {
            it.stopRequested()
            exGrabbers.add(it)
            grabber = null
        }
    }

    fun stopBlocking() {
        if (DEBUG) Log.d(TAG, "CamThread $index > stopBlocking")
        grabber?.stopSync()
        grabber = null
        discardGrabber()
        viewHolder.onStop()
    }

    fun pingRender() {
        pingRenderTS = SystemClock.elapsedRealtime()

        if (debugDisplay) {
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
            if (delayMS > GrabbersManagerThread.GRAB_TIMEOUT_MS) {
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
