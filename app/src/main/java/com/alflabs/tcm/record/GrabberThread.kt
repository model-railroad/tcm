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
import com.alflabs.tcm.activity.VideoViewHolder
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import org.bytedeco.ffmpeg.avformat.*
import org.bytedeco.ffmpeg.global.avformat.*
import java.util.concurrent.atomic.AtomicInteger

class GrabberThread(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val camIndex: Int,
    private val url: String,
    private val renderer : VideoViewHolder
): ThreadLoop() {

    companion object {
        private val TAG: String = GrabberThread::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
        private const val SPIN = "◥◢◣◤"

        // https://ffmpeg.org/doxygen/trunk/pixfmt_8h_source.html
        const val AV_PIX_FMT_RGB24 = 75 - 73
        const val AV_PIX_FMT_BGR24 = 76 - 73
        const val AV_PIX_FMT_ARGB =  99 - 73    // Matches Android Bitmap Config ARGB_8888
        const val AV_PIX_FMT_RGBA = 100 - 73
        const val AV_PIX_FMT_ABGR = 101 - 73
        const val AV_PIX_FMT_BGRA = 102 - 73

        const val FFMPEG_TIMEOUT_S  = 2             // 2 seconds
        const val FFMPEG_TIMEOUT_MS = 1000 * FFMPEG_TIMEOUT_S
        const val FFMPEG_TIMEOUT_µS = (1000 * FFMPEG_TIMEOUT_MS).toString()
        const val PAUSE_BEFORE_RETRY_MS = 1000L * 5 // 5 seconds
    }

    private var countStart = 0
    private var countNull = 0

    override fun beforeThreadLoop() {
        logger.log(TAG, "beforeThreadLoop")
    }

    override fun runInThreadLoop() {
        logger.log(TAG, "runInThreadLoop")
        var grabber : FFmpegFrameGrabber? = null
        val converter = AndroidFrameConverter()

        try {
            logger.log(TAG, "Grabber for URL: $url")

            renderer.setStatus("Connecting")

            grabber = FFmpegFrameGrabber(url)
            grabber.format = "rtsp"
            // http://ffmpeg.org/ffmpeg-all.html#rtsp
            grabber.setOption("rtsp_transport", "tcp")  // "udp" or "tcp"
            grabber.setOption("rw_timeout", FFMPEG_TIMEOUT_µS) // microseconds
            grabber.setOption("stimeout"  , FFMPEG_TIMEOUT_µS) // microseconds
            // "timeout" isn't supported in older versions of FFMPEG for RTSP
             grabber.setOption("timeout"   , FFMPEG_TIMEOUT_µS) // microseconds
            // Match the Android Bitmap Config ARGB_8888, which speeds up the converter.
            grabber.pixelFormat = AV_PIX_FMT_ARGB
            grabber.timeout = FFMPEG_TIMEOUT_MS
            grabber.start()

            // Example from https://github.com/bytedeco/javacv/blob/master/samples/FFmpegStreamingTimeout.java
            val interruptFlag = AtomicBoolean(false)
            val interruptCount = AtomicInteger(0)
            val cp: AVIOInterruptCB.Callback_Pointer = object : AVIOInterruptCB.Callback_Pointer() {
                override fun call(pointer: org.bytedeco.javacpp.Pointer?): Int {
                    // 0 - continue, 1 - exit
                    interruptCount.incrementAndGet()
                    val interruptFlagInt = if (interruptFlag.get()) 1 else 0
                    logger.log(TAG, "@@ Callback, interrupt flag == $interruptFlagInt")
                    return interruptFlagInt
                }
            }
            val oc: AVFormatContext = grabber.formatContext
            avformat_alloc_context()
            val cb: AVIOInterruptCB = AVIOInterruptCB()
            cb.callback(cp)
            oc.interrupt_callback(cb)

            val pixelFormat = grabber.getPixelFormat()
            val frameRate = grabber.getFrameRate()
            logger.log(TAG, "Grabber started with video format " + pixelFormat
                    + ", framerate " + frameRate + " fps"
                    + ", size " + grabber.imageWidth + "x" + grabber.imageHeight
            )

            analytics.sendEvent(
                category = "TCM_Cam",
                action = "Start",
                label = camIndex.toString(),
                value = "1")

            // Note that frame is reused for each frame recording
            var frame: Frame? = null

            var firstImage = true

            countStart++
            var spin = 0
            val grabberDesiredMS = 1000L / 2 // 5 FPS target in ms
            var grabCalls = 0
            val statsSpanMS = 10*60*1000L
            var statsMinuteEndMS = SystemClock.elapsedRealtime() + statsSpanMS
            var statsMax = 0L
            var statsTotal = 0L
            var statsCount = 0
            var statsAvg = 0L

            while (!mQuit) {
                val grabberStartMS = SystemClock.elapsedRealtime()
                if (grabberStartMS > statsMinuteEndMS) {
                    statsMinuteEndMS = grabberStartMS + statsSpanMS
                    statsTotal = 0L
                    statsCount = 0
                    grabCalls = 0
                }
                frame = grabber.grabImage()
                grabCalls++
                if (frame === null) {
                    countNull++
                    break
                } else {
                    val grabberDeltaMS = SystemClock.elapsedRealtime() - grabberStartMS - grabberDesiredMS
                    if (grabberDeltaMS > 0) {
                        statsMax = max(statsMax, grabberDeltaMS)
                        statsTotal += grabberDeltaMS
                        statsCount++
                        statsAvg = statsTotal / statsCount
                    }
                }
                if (firstImage) {
                    renderer.setStatus("")
                    firstImage = false
                }
                if (DEBUG) {
                    renderer.setStatus("S: $countStart   N: $countNull  ${SPIN[spin]}\n" +
                            "${statsTotal/1000}s / #$statsCount = $statsAvg ms\nMax $statsMax ms\n" +
                            "${(100 * statsCount) / grabCalls} % -- int ${interruptCount.get()}")
                    spin = (spin + 1) % 4
                }
                // use frame
                val bmp = converter.convert(frame)
                renderer.render(bmp)
            }

            if (DEBUG) {
                renderer.setStatus("S: $countStart   N: $countNull  ${SPIN[spin]}\n" +
                        "${statsTotal/1000}s / #$statsCount = $statsAvg ms\n" +
                        "Max $statsMax ms\n" +
                        "${(100 * statsCount) / grabCalls} %")
            }

            logger.log(TAG, "end while: quit ($mQuit) or frame ($frame)")
            analytics.sendEvent(
                category = "TCM_Cam",
                action = if (mQuit) "Stop" else "Error",
                label = camIndex.toString(),
                value = "1")
            grabber.flush()

        } catch (e: FrameGrabber.Exception) {
            analytics.sendEvent(
                category = "TCM_Cam",
                action = "Error",
                label = camIndex.toString(),
                value = "1")
            renderer.setStatus("Disconnected")

            try {
                grabber?.close()
            } catch (ignore: FrameGrabber.Exception) {}
            logger.log(TAG, "Grabber Exception: $e")
            if (e.toString().contains("Could not open input")) {
                // Insert a delay before retrying unless asked to quit
                logger.log(TAG, "Pause $PAUSE_BEFORE_RETRY_MS ms before retry")
                pause(PAUSE_BEFORE_RETRY_MS)
            }
        } finally {
            try {
                grabber?.close() // implementation calls stop + release
            } catch (ignore: FrameGrabber.Exception) {}
            // AndroidFrameConverter.close() was only added in JavaCV 1.5.5
            if (converter is AutoCloseable) {
                converter.close()
            }
            logger.log(TAG, "runInThreadLoop - closed")

            renderer.setStatus("Disconnected")
        }
    }

    private fun pause(delayMS: Long) {
        val endMS = SystemClock.elapsedRealtime() + delayMS
        while (!mQuit && SystemClock.elapsedRealtime() < endMS) {
            try {
                Thread.sleep(10L)
            } catch (ignore: Exception) {
            }
        }
    }

    override fun afterThreadLoop() {
        logger.log(TAG, "afterThreadLoop")
    }
}
