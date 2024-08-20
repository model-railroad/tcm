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
package com.alflabs.tcm.util

import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FFmpegLogCallback
import kotlin.reflect.full.declaredFunctions

class AVUtils {

    companion object {
        private val TAG: String = AVUtils::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
        private val DEBUG_FFMPEG = false

        // From https://www.ffmpeg.org/doxygen/4.0/group__lavu__log__constants.html
        const val AV_LOG_TRACE = 56

        // https://ffmpeg.org/doxygen/trunk/pixfmt_8h_source.html
        const val AV_PIX_FMT_RGB24 = 75 - 73
        const val AV_PIX_FMT_BGR24 = 76 - 73
        const val AV_PIX_FMT_ARGB =  99 - 73    // Matches Android Bitmap Config ARGB_8888
        const val AV_PIX_FMT_RGBA = 100 - 73
        const val AV_PIX_FMT_ABGR = 101 - 73
        const val AV_PIX_FMT_BGRA = 102 - 73

        val instance = AVUtils()
    }

    val isJavaCV_1_5_9 : Boolean by lazy {
        // I'm not aware of an API call returning a version number in JavaCV.
        // We can infer we're using Java CV 1.5.9 or above if the method setDisplayRotation()
        // exists in FFmpegFrameRecorder.

        FFmpegFrameRecorder::class.declaredFunctions.any {
            it.name == "setDisplayRotation"
        }
    }

    fun setFFmpegLogCallback(logger: ILogger) {
        if (DEBUG_FFMPEG) {
            try {
                // FFmpegLogCallback.setLevel(AV_LOG_TRACE)    // Warning: very verbose in logcat

                // call FFmpegLogCallback.set()
                FFmpegLogCallback::class.declaredFunctions.firstOrNull { it.name == "set" }?.call()
            } catch (t: Throwable) {
                logger.log("ERROR: $t")
            }
        }
    }

}
