package com.alflabs.tcm.activity

import android.content.Context
import android.content.res.TypedArray
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import com.alflabs.tcm.R
import com.alflabs.tcm.util.GlobalDebug
import java.util.Locale

class CameraTransformPref : DialogPreference {
    // Note: The 'DialogPreference' is basically a ViewHolder pattern.

    private var mText = STRING_DEFAULT

    companion object {
        private val TAG: String = CameraTransformPref::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        const val STRING_TEMPLATE = "rotation: ROT\nscale: SC\npan: X;Y"
        const val STRING_DEFAULT = "rotation: 0\nscale: 1\npan: 0;0"
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        if (DEBUG) Log.d(TAG, "PREF init")
        dialogLayoutResource = R.layout.cam_transform_dialog_pref
        summaryProvider = CameraTransformSummaryProvider.instance
    }

    // --- Implementation mirrored from EditTextPreference

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        var string = a.getString(index)
        if (string.isNullOrEmpty()) {
            string = STRING_DEFAULT
        }
        return string
    }

    fun setValues(values: CamTransformValues) {
        setText(values.toString())
    }

    fun getvalues() : CamTransformValues {
        return CamTransformValues.parse(getText())
    }

    /**
     * Saves the text to the current data storage.
     *
     * @param text The text to save
     */
    fun setText(text: String) {
        val wasBlocking = shouldDisableDependents()

        mText = text.ifBlank { STRING_DEFAULT }

        if (DEBUG) Log.d(TAG, "PREF setText $mText")
        persistString(mText)

        val isBlocking = shouldDisableDependents()
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking)
        }

        notifyChanged()
    }

    /**
     * Gets the text from the current data storage.
     *
     * @return The current preference value
     */
    fun getText(): String {
        if (DEBUG) Log.d(TAG, "PREF getText $mText")
        return mText
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        if (DEBUG) Log.d(TAG, "PREF onSetInitialValue")
        setText(getPersistedString(defaultValue as String?))
    }

    override fun onSaveInstanceState(): Parcelable? {
        if (DEBUG) Log.d(TAG, "PREF onSaveInstanceState")
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState
        }

        val myState = CamTransformSavedState(superState)
        myState.mText = getText()
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (DEBUG) Log.d(TAG, "PREF onRestoreInstanceState")
        if (state == null || state !is CamTransformSavedState) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        setText(state.mText)
    }
}


private class CamTransformSavedState : Preference.BaseSavedState {
    var mText: String = CameraTransformPref.STRING_DEFAULT

    internal constructor(source: Parcel) : super(source) {
        mText = source.readString() ?: ""
    }

    internal constructor(superState: Parcelable?) : super(superState)

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(mText)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<CamTransformSavedState> {
            override fun createFromParcel(input: Parcel): CamTransformSavedState {
                return CamTransformSavedState(input)
            }

            override fun newArray(size: Int): Array<CamTransformSavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}


/**
 * A simple [androidx.preference.Preference.SummaryProvider] implementation for an
 * [CameraTransformPref]. If no value has been set, the summary displayed will be 'Not
 * set', otherwise the summary displayed will be the value set for this preference.
 */
private class CameraTransformSummaryProvider private constructor() : SummaryProvider<CameraTransformPref> {
    override fun provideSummary(preference: CameraTransformPref): CharSequence? {
        return preference.getText()
    }

    companion object {
        private var sSimpleSummaryProvider: CameraTransformSummaryProvider? = null

        val instance: CameraTransformSummaryProvider
            /**
             * Retrieve a singleton instance of this simple
             * [androidx.preference.Preference.SummaryProvider] implementation.
             *
             * @return a singleton instance of this simple
             * [androidx.preference.Preference.SummaryProvider] implementation
             */
            get() {
                if (sSimpleSummaryProvider == null) {
                    sSimpleSummaryProvider = CameraTransformSummaryProvider()
                }
                return sSimpleSummaryProvider!!
            }
    }
}

data class CamTransformValues(val rotation: Int, val scale: Float, val panX: Int, val panY: Int) {

    companion object {
        fun parse(s: String): CamTransformValues {
            var rot: Int = 0
            var sc: Float = 1f
            var panX: Int = 0
            var panY: Int = 0

            for (l in s.split("\n")) {
                val line = l.trim()
                if (line.startsWith("rotation:")) {
                    rot = line.split(":")[1].trim().toIntOrNull() ?: 0

                } else if (line.startsWith("scale:")) {
                    sc = line.split(":")[1].trim().toFloatOrNull() ?: 1f

                } else if (line.startsWith("pan:")) {
                    val pan = line.split(":")[1].trim()
                    val xy = pan.split(";")
                    panX = xy.getOrElse(0) { "0" }.toIntOrNull() ?: 0
                    panY = xy.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                }
            }

            return CamTransformValues(rot, sc, panX, panY)
        }
    }

    override fun toString(): String {
        return CameraTransformPref.STRING_TEMPLATE
            .replace("ROT", String.format(Locale.US, "%d", rotation))
            .replace("SC", String.format(Locale.US, "%f", scale).trimTrailingZeroes())
            .replace("X", String.format(Locale.US, "%d", panX))
            .replace("Y", String.format(Locale.US, "%d", panY))
    }
}


fun String.trimTrailingZeroes() : String {
    var s = this
    if (s.contains('.')) {
        while (s.length > 1 && s.endsWith("0")) {
            s = s.substring(0, s.length - 1)
        }
        if (s.length > 1 && s.endsWith(".")) {
            s = s.substring(0, s.length - 1)
        }
    }
    return s
}
