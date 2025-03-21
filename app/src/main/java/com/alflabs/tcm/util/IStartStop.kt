/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
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

interface IStartStop {
    /// Starts the thread loop
    @Throws(Exception::class)
    fun start()

    /// Requests the thread loop to stop but does not wait for it.
    @Throws(Exception::class)
    fun requestStopAsync()

    /// Requests the thread loop to stop and waits for it indefinitely.
    @Throws(Exception::class)
    fun stopSync()

    /// Requests the thread loop to stop and waits for it for the specified time.
    @Throws(Exception::class)
    fun stopSync(joinTimeoutMillis: Long)
}
