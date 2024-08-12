package com.alflabs.tcm.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.preference.PreferenceDialogFragmentCompat
import com.alflabs.tcm.util.GlobalDebug

class CamTransformDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        private val TAG: String = CamTransformDialogFragment::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        fun newInstance(key: String) : CamTransformDialogFragment {
            if (DEBUG) Log.d(TAG, "FRAG newInstance")
            val fragment = CamTransformDialogFragment()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return fragment
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.d(TAG, "FRAG onCreate: $preference")
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        if (DEBUG) Log.d(TAG, "FRAG onBindDialogView")
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (DEBUG) Log.d(TAG, "FRAG onDialogClosed")
        preference.summary = "Update summary ${if (positiveResult) "saved" else "cancelled"}"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (DEBUG) Log.d(TAG, "FRAG onSaveInstanceState")
    }
}
