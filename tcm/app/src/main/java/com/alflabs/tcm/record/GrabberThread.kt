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
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber


class GrabberThread(
    private val logger: ILogger,
    private val url: String,
    private val renderer : VideoViewHolder
): ThreadLoop() {

    companion object {
        private val TAG: String = GrabberThread::class.java.simpleName

        // https://ffmpeg.org/doxygen/trunk/pixfmt_8h_source.html
        const val AV_PIX_FMT_RGB24 = 75 - 73
        const val AV_PIX_FMT_BGR24 = 76 - 73
        const val AV_PIX_FMT_ARGB =  99 - 73    // Matches Android Bitmap Config ARGB_8888
        const val AV_PIX_FMT_RGBA = 100 - 73
        const val AV_PIX_FMT_ABGR = 101 - 73
        const val AV_PIX_FMT_BGRA = 102 - 73

        const val FFMPEG_TIMEOUT_MS = 1000 * 3      // 3 seconds
        const val FFMPEG_TIMEOUT_µS = "3000000"     // in microseconds
        const val PAUSE_BEFORE_RETRY_MS = 1000L * 5 // 5 seconds
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

            grabber = FFmpegFrameGrabber(url)
            grabber.format = "rtsp"
            // http://ffmpeg.org/ffmpeg-all.html#rtsp
            grabber.setOption("rtsp_transport", "tcp")  // "udp" or "tcp"
            grabber.setOption("rw_timeout" , FFMPEG_TIMEOUT_µS) // microseconds
            grabber.setOption("timeout" , FFMPEG_TIMEOUT_µS) // microseconds
            grabber.setOption("stimeout" , FFMPEG_TIMEOUT_µS) // microseconds
            // Match the Android Bitmap Config ARGB_8888, which speeds up the converter.
            grabber.pixelFormat = AV_PIX_FMT_ARGB
            grabber.timeout = FFMPEG_TIMEOUT_MS
            grabber.start()

            val pixelFormat = grabber.getPixelFormat()
            val frameRate = grabber.getFrameRate()
            logger.log(TAG, "Grabber started with video format " + pixelFormat
                    + ", framerate " + frameRate + " fps"
                    + ", size " + grabber.imageWidth + "x" + grabber.imageHeight
            )

            // Note that frame is reused for each frame recording
            var frame: Frame? = null

            var firstImage = true

            while (!mQuit) {
                frame = grabber.grabImage()
                if (frame === null) break;
                if (firstImage) {
                    renderer.setStatus("")
                    firstImage = false
                }
                // use frame
                val bmp = converter.convert(frame)
                renderer.render(bmp)
            }

            logger.log(TAG, "end while: quit ($mQuit) or frame ($frame)")
            grabber.flush()

        } catch (e: FrameGrabber.Exception) {
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
            converter.close()
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
