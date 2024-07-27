package com.alflabs.tcm.app

import com.alflabs.tcm.activity.MainActivity

/**
 * This is the main core "Monitor" of the TCM application.
 *
 * There is a single instance of this held by the MainActivity during its
 * lifetime. It handles all the monitoring lifecycle -- deciding when to connect,
 * and when to disconnect (due to power saving, or the activity finishing).
 *
 * This is an "activity mixin", tightly connected to the MainActivity lifecycle
 */
class Monitor(private val activity: MainActivity) {

    companion object {
        const val MAX_CAMERAS = 2
    }

    fun onCreate() {}

    fun onPause() {}

}
