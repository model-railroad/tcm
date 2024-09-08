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
package com.alflabs.tcm.app

import android.util.Log
import com.alflabs.tcm.util.ILogger
import com.alflabs.tcm.util.LoggerDelegate

/**
 * The app specific implementation of ILogger.
 * It can defer to an activity delegate.
 *
 * Note: don't use this directly. Instead @Inject ILogger from the App component.
 */
class AppLogger : ILogger {

    private var delegate: LoggerDelegate? = null

    override fun log(tag: String, msg: String) {
        Log.d(tag, msg)
        delegate?.invoke(tag, msg)
    }

    override fun attachDelegate(delegate: LoggerDelegate) {
        this.delegate = delegate
    }

    override fun removeDelegate() {
        delegate = null
    }
}
