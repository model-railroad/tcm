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
            grabber.timeout = 5*1000 // milliseconds
            logger.log(TAG, "start")
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
