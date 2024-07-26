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
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber


class GrabberThread(
    private val logger: ILogger,
    private val url: String,
    private val draw : (Bitmap) -> Unit): ThreadLoop() {

    companion object {
        const val TAG = "GrabberThread"

        // https://ffmpeg.org/doxygen/trunk/pixfmt_8h_source.html
        const val AV_PIX_FMT_RGB24 = 75 - 73
        const val AV_PIX_FMT_BGR24 = 76 - 73
        const val AV_PIX_FMT_ARGB =  99 - 73    // Matches Android Bitmap Config ARGB_8888
        const val AV_PIX_FMT_RGBA = 100 - 73
        const val AV_PIX_FMT_ABGR = 101 - 73
        const val AV_PIX_FMT_BGRA = 102 - 73

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
            grabber = FFmpegFrameGrabber(url)
            grabber.format = "rtsp"
            // http://ffmpeg.org/ffmpeg-all.html#rtsp
            grabber.setOption("rtsp_transport", "tcp")  // "udp" or "tcp"
            grabber.setOption("rw_timeout" , "5000000") // microseconds
            grabber.setOption("timeout" , "5000000") // microseconds
            grabber.setOption("stimeout" , "5000000") // microseconds
            // Match the Android Bitmap Config ARGB_8888, which speeds up the converter.
            grabber.pixelFormat = AV_PIX_FMT_ARGB
            grabber.timeout = 5*1000 // milliseconds
            grabber.start()

            val pixelFormat = grabber.getPixelFormat()
            val frameRate = grabber.getFrameRate()
            logger.log(TAG, "Grabber started with video format " + pixelFormat
                    + ", framerate " + frameRate + " fps"
                    + ", size " + grabber.imageWidth + "x" + grabber.imageHeight
            )

            // Note that frame is reused for each frame recording
            var frame: Frame? = null

            while (!mQuit) {
                frame = grabber.grabImage()
                if (frame === null) break;
                // use frame
                val bmp = converter.convert(frame)
                draw(bmp)
            }

            logger.log(TAG, "end while: quit ($mQuit) or frame ($frame)")
            grabber.flush()

        } catch (e: FrameGrabber.Exception) {
            try {
                grabber?.release()
            } catch (ignore: FrameGrabber.Exception) {}
            logger.log(TAG, e.toString())
        } finally {
            try {
                grabber?.close() // implementation calls stop + release
            } catch (ignore: FrameGrabber.Exception) {}
            converter.close()
        }
    }

    override fun afterThreadLoop() {
        logger.log(TAG, "afterThreadLoop")
    }
}
