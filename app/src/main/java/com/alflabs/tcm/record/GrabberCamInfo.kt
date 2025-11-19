/*
 * Project: TCM
 * Copyright (C) 2025 alf.labs gmail com,
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

/**
 * Camera Connection Parameters
 *
 * @param url The URL for the camera stream
 * @param params FFMPEG parameters in format "name=value;name=value"
 */
class GrabberCamInfo(
    val url: String,
    params: String,
) {
    private val paramsMap: Map<String, String> = params
        .split(";")
        .filter { it.contains("=") }
        .associate {
            val (left, right) = it.split("=")
            left.trim() to right.trim()
        }

    fun getParam(name: String, defaultValue: String) : String =
        paramsMap.getOrDefault(name, defaultValue)
}
