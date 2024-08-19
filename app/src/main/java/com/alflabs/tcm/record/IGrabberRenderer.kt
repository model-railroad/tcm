package com.alflabs.tcm.record

import android.graphics.Bitmap

interface IGrabberRenderer {
    fun render(bmp: Bitmap)
    fun setStatus(status: String)
    fun pingAlive()
}
