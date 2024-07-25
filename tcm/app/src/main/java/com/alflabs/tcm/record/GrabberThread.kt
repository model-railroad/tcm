package com.alflabs.tcm.record

import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.ThreadLoop
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber


class GrabberThread(private val logger: ILogger, private val url: String): ThreadLoop() {

    companion object {
        const val TAG = "GrabberThread"
    }

    override fun beforeThreadLoop() {
        logger.log(TAG, "beforeThreadLoop")
//        FFmpegFrameGrabber.tryLoad()
    }

    override fun runInThreadLoop() {
        logger.log(TAG, "runInThreadLoop")
        var grabber : FFmpegFrameGrabber? = null

        try {
            logger.log(TAG, "Grabber for URL: $url")
            grabber = FFmpegFrameGrabber(url)
//            grabber.format = "mp4"
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
                logger.log(TAG, "Grabber: process frame $frame")
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
                grabber?.stop()
            } catch (ignore: FrameGrabber.Exception) {}
            try {
                grabber?.release()
                grabber = null
            } catch (ignore: FrameGrabber.Exception) {}
        }
    }

    override fun afterThreadLoop() {
        logger.log(TAG, "afterThreadLoop")
    }
}
