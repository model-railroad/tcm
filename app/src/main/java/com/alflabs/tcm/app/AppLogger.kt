package com.alflabs.tcm.app

import android.util.Log
import com.alflabs.tcm.util.ILogger

class AppLogger : ILogger {
    override fun log(tag: String, msg: String) {
        Log.d(tag, msg)
    }

}
