package com.alflabs.tcm.activity

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import com.alflabs.tcm.R
import com.alflabs.tcm.util.GlobalDebug

class CameraTransformPref : DialogPreference {
    // Note: The 'DialogPreference' is basically a ViewHolder pattern.

    companion object {
        private val TAG: String = CameraTransformPref::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    private fun init(context: Context) {
        if (DEBUG) Log.d(TAG, "PREF init")
        dialogLayoutResource = R.layout.cam_transform_dialog_pref
        summary = "init summary"
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        if (DEBUG) Log.d(TAG, "PREF onSetInitialValue")
    }

    override fun onSaveInstanceState(): Parcelable? {
        if (DEBUG) Log.d(TAG, "PREF onSaveInstanceState")
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        if (DEBUG) Log.d(TAG, "PREF onRestoreInstanceState")
    }

}
