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

/** Renders the output of a [GrabberThread] into a [MainActivity]. */
class GrabberRender(private val grabberHolder: GrabberHolder) : IGrabberRenderer {
    @Volatile
    private var isValid = true

    fun setValid(valid: Boolean) {
        isValid = valid
    }

    override fun render(bmp: Bitmap) {
        if (isValid) {
            grabberHolder.pingRender()
            grabberHolder.viewHolder.render(bmp)
        }
    }

    override fun setStatus(status: String) {
        if (isValid) {
            grabberHolder.viewHolder.setStatus(status)
        }
    }

    override fun pingAlive() {
        if (isValid) {
            grabberHolder.pingAlive()
        }
    }
}
