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
    }

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
            grabber?.start()
        }

        private fun discardGrabber() {
            render?.isValid = false
            render = null
            grabber?.let {
                grabber?.stopRequested()
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
                viewHolder.setStatus("${SPIN[spin]} $countNewRender\n$currDelayMS < $maxDelayMS ms")
                spin = (spin + 1) % 4
            }
        }

        fun pingAlive() {
            // The GrabberThread is alive even though it's not rendering.
            // This happens when the grabber is waiting for a connection to establish.
            pingActiveTS = SystemClock.elapsedRealtime()
        }

        fun loop() {
            val nowTS = SystemClock.elapsedRealtime()
            val latestPingTS = max(pingActiveTS, pingRenderTS)
            if (latestPingTS > 0) {
                val delayMS = nowTS - latestPingTS
                currDelayMS = delayMS
                maxDelayMS = max(maxDelayMS, nowTS - latestPingTS)
                if (delayMS > 4000) {
                    discardGrabber()
                    startNewGrabber()
                }
            }
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
        super.start()
    }

    override fun stop() {
        if (DEBUG) Log.d(TAG, "stop")
        super.stop()
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

            while (!mQuit) {
                cams.forEach { it.loop() }

                try {
                    Thread.sleep(1000)
                } catch (_ : Exception) {}
            }
        } finally {
            cams.forEach { it.stopBlocking() }
        }
    }

}
