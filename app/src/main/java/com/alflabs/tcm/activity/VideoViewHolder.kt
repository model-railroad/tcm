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
package com.alflabs.tcm.activity

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.util.FpsMeasurer
import com.alflabs.tcm.util.GlobalDebug

class VideoViewHolder(
    private val cameraIndex: Int,
    private val container: ViewGroup,
    private val imageView: ImageView,
    private val labelView: TextView,
    private val statusView: TextView,
    private val fpsView: TextView,
    private val runOnUiThread: IRunOnUiThread,
    ) {

    companion object {
        private val TAG: String = VideoViewHolder::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
    }

    fun interface IRunOnUiThread {
        fun run(action: () -> Unit)
    }

    private var transform: CamTransformValues? = null
    private var label = ""
    private val fpsMeasurer = FpsMeasurer()
    private var mat: Matrix? = null

    fun loadPrefs(prefs: AppPrefsValues) {
        transform = prefs.camerasTransform(cameraIndex)
        label = prefs.camerasLabel(cameraIndex)
    }

    fun render(bmp: Bitmap) {
        runOnUiThread.run {
            fpsMeasurer.ping()
            if (mat == null && transform != null) {
                mat = computeImageMatrix(transform!!, bmp.width, bmp.height, imageView.width, imageView.height)
                imageView.imageMatrix = mat
            }
            imageView.setImageBitmap(bmp)
            fpsView.text = fpsMeasurer.lastFps
        }
    }

    fun setStatus(status: String) {
        runOnUiThread.run {
            statusView.text = status
        }
    }

    fun setVisible(visible: Boolean) {
        runOnUiThread.run {
            container.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun onStart() {
        if (DEBUG) Log.d(TAG, "VideoViewHolder $cameraIndex -- START")
        setStatus("Starting")
        runOnUiThread.run {
            imageView.keepScreenOn = true
            fpsView.text = fpsView.context.getString(R.string.main__starting_cam, cameraIndex)

            labelView.text = label

            // Note: Any preferences should be used in onStart, which is called after PrefsActivity.
            imageView.scaleType = ImageView.ScaleType.MATRIX
            // Force recomputing matrix before displaying the first image
            // (we don't know the image size here before the first rendering; we do have that info
            // in GrabberThread but only after the stream connection started.)
            mat = null
        }
    }

    fun onStop() {
        if (DEBUG) Log.d(TAG, "VideoViewHolder $cameraIndex -- STOP")
        runOnUiThread.run {
            imageView.keepScreenOn = false
            fpsView.text = fpsView.context.getString(R.string.main__stopped_cam, cameraIndex)
        }
    }

    private fun computeImageMatrix(
        transform: CamTransformValues,
        bmpW: Int,
        bmpH: Int,
        viewW: Int,
        viewH: Int
    ): Matrix {
        if (DEBUG) Log.d(TAG, "VideoViewHolder $cameraIndex -- computeImageMatrix")
        val mat = Matrix()

        // Center image on view (0,0) to apply rotation & scale
        mat.setTranslate(
            bmpW * -0.5f,
            bmpH * -0.5f)

        mat.postRotate(transform.rotation.toFloat())

        mat.postScale(transform.scale, transform.scale)

        // Center image on middle of view + apply requested offset (in pixels)
        mat.postTranslate(
            viewW * 0.5f + transform.panX,
            viewH * 0.5f + transform.panY,
        )

        return mat
    }
}
