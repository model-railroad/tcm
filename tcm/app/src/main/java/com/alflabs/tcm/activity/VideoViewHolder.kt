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
import android.widget.ImageView
import android.widget.TextView
import com.alflabs.tcm.R
import com.alflabs.tcm.util.FpsMeasurer

class VideoViewHolder(
    private val cameraIndex: Int,
    private val imageView: ImageView,
    private val statusView: TextView,
    private val fpsView: TextView) {

    private val fpsMeasurer = FpsMeasurer()

    fun render(bmp: Bitmap) {
        imageView.post {
            fpsMeasurer.ping()
            imageView.setImageBitmap(bmp)
            fpsView.text = fpsMeasurer.lastFps
        }
    }

    fun setStatus(status: String) {
        statusView.post {
            statusView.text = status
        }
    }

    fun onStart() {
        setStatus("Starting")
        fpsView.text = fpsView.context.getString(R.string.main__starting_cam, cameraIndex)
    }

    fun onStop() {
        fpsView.text = fpsView.context.getString(R.string.main__stopped_cam, cameraIndex)
    }
}
