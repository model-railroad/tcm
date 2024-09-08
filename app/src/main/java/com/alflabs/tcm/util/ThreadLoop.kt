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

import kotlin.concurrent.Volatile

abstract class ThreadLoop : IStartStop {
    @Suppress("MemberVisibilityCanBePrivate")
    protected var mThread: Thread? = null

    @Volatile
    protected var mQuit: Boolean = false

    @Throws(Exception::class)
    override fun start() {
        this.start("" /* default thread name */)
    }

    @Throws(Exception::class)
    fun start(name: String?) {
        if (mThread == null) {
            mThread = if (name.isNullOrEmpty()) {
                Thread { this.runInThread() }
            } else {
                Thread({ this.runInThread() }, name)
            }
            mQuit = false
            mThread?.start()
        }
    }

    @Throws(Exception::class)
    override fun requestStopAsync() {
        if (mThread != null) {
            val t: Thread = mThread!!
            mThread = null
            mQuit = true
            t.interrupt()
        }
    }

    @Throws(Exception::class)
    override fun stopSync() {
        stopSync(0)
    }

    @Throws(Exception::class)
    override fun stopSync(joinTimeoutMillis: Long) {
        if (mThread != null) {
            val t: Thread = mThread!!
            requestStopAsync()
            t.join(joinTimeoutMillis)
        }
    }

    private fun runInThread() {
        beforeThreadLoop()
        try {
            while (!mQuit) {
                runInThreadLoop()
            }
        } catch (ignored: EndLoopException) {
            // No-logging, just end the loop.
        } catch (t: Throwable) {
            println("ThreadLoop._runInThread [${Thread.currentThread().name}] unhanlded exception: $t")
        } finally {
            afterThreadLoop()
        }
    }

    protected inner class EndLoopException : Exception()

    /** Called once before the first `_runInThreadLoop` call.  */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun beforeThreadLoop() {}

    /** Called in a loop as long as `mQuit` is false.  */
    @Throws(EndLoopException::class)
    protected abstract fun runInThreadLoop()

    /** Called once after the last `_runInThreadLoop` call.  */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun afterThreadLoop() {}
}
