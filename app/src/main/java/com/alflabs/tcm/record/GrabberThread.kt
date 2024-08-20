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
import com.alflabs.tcm.util.AVUtils
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber

class GrabberThread(
    private val logger: ILogger,
    private val analytics: Analytics,
    private val camIndex: Int,
    private val url: String,
    private val renderer : IGrabberRenderer
): ThreadLoop() {

    companion object {
        private val TAG: String = GrabberThread::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val FFMPEG_TIMEOUT_S  = 2             // 2 seconds
        const val FFMPEG_TIMEOUT_MS = 1000 * FFMPEG_TIMEOUT_S
        const val FFMPEG_TIMEOUT_µS = (1000 * FFMPEG_TIMEOUT_MS).toString()
        const val PAUSE_BEFORE_RETRY_MS = 1000L * 5 // 5 seconds
    }

    fun stopRequested() {
        mQuit = true
    }

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

            // TBD all that setup below is highly dependend on the URL using RTSP.
            grabber = FFmpegFrameGrabber(url)
            grabber.format = "rtsp"
            // http://ffmpeg.org/ffmpeg-all.html#rtsp
            grabber.setOption("rtsp_transport", "tcp")  // "udp" or "tcp"
            grabber.setOption("rw_timeout", FFMPEG_TIMEOUT_µS) // microseconds
            grabber.setOption("stimeout"  , FFMPEG_TIMEOUT_µS) // microseconds
            if (AVUtils.instance.isJavaCV_1_5_9) {
                // "timeout" isn't supported in older versions of FFMPEG for RTSP (before
                // 6.1.1-1.5.10 although I don't exactly which version.)
                grabber.setOption("timeout", FFMPEG_TIMEOUT_µS) // microseconds
            }
            // Match the Android Bitmap Config ARGB_8888, which speeds up the converter.
            grabber.pixelFormat = AVUtils.AV_PIX_FMT_ARGB
            grabber.timeout = FFMPEG_TIMEOUT_MS
            grabber.start()

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

            while (!mQuit) {
                frame = grabber.grabImage()
                if (frame === null || mQuit) {
                    break
                }
                if (firstImage) {
                    renderer.setStatus("")
                    firstImage = false
                }
                // use frame
                val bmp = converter.convert(frame)
                renderer.render(bmp)
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
            renderer.pingAlive()

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
                renderer.pingAlive()
            } catch (ignore: Exception) {
            }
        }
    }

    override fun afterThreadLoop() {
        logger.log(TAG, "afterThreadLoop")
    }
}
