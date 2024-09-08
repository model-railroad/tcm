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
package com.alflabs.tcm.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import androidx.preference.PreferenceDialogFragmentCompat
import com.alflabs.tcm.R
import com.alflabs.tcm.util.GlobalDebug
import java.util.Locale

class CamTransformDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        private val TAG: String = CamTransformDialogFragment::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        private val ROT_ID = mapOf<Int, Int>(
            0 to R.id.rotation_0,
            90 to R.id.rotation_90,
            180 to R.id.rotation_180,
            270 to R.id.rotation_270,
        )

        fun newInstance(key: String) : CamTransformDialogFragment {
            if (DEBUG) Log.d(TAG, "FRAG newInstance")
            val fragment = CamTransformDialogFragment()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var editRotationGroup: RadioGroup
    private lateinit var editScale: EditText
    private lateinit var editPanX: EditText
    private lateinit var editPanY: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.d(TAG, "FRAG onCreate: $preference")
    }

    override fun onBindDialogView(view: View) {
        if (DEBUG) Log.d(TAG, "FRAG onBindDialogView")
        super.onBindDialogView(view)
        editRotationGroup = view.findViewById(R.id.rotation_group)
        editScale = view.findViewById(R.id.scale_value)
        editPanX = view.findViewById(R.id.pan_x_value)
        editPanY = view.findViewById(R.id.pan_y_value)

        val pref = preference
        if (pref is CameraTransformPref) {
            val values = pref.getvalues()
            ROT_ID.get(values.rotation)?.let { id -> editRotationGroup.check(id) }
            editScale.setText(String.format(Locale.US, "%f", values.scale).trimTrailingZeroes())
            editPanX.setText(String.format(Locale.US, "%d", values.panX))
            editPanY.setText(String.format(Locale.US, "%d", values.panY))
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (DEBUG) Log.d(TAG, "FRAG onDialogClosed")

        val pref = preference
        if (pref is CameraTransformPref) {
            val rot = ROT_ID
                .filterValues { v -> v == editRotationGroup.checkedRadioButtonId }
                .keys
                .firstOrNull() ?: 0
            val sc = editScale.text.toString().toFloatOrNull() ?: 1f
            val panX = editPanX.text.toString().toIntOrNull() ?: 0
            val panY = editPanY.text.toString().toIntOrNull() ?: 0

            pref.setValues(CamTransformValues(rot, sc, panX, panY))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (DEBUG) Log.d(TAG, "FRAG onSaveInstanceState")
    }
}
