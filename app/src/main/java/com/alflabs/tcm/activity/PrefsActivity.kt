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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppMonitor
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.app.LauncherRole
import com.alflabs.tcm.app.MainApp
import com.alflabs.tcm.dagger.ActivityScope
import com.alflabs.tcm.util.Analytics
import com.alflabs.tcm.util.GlobalDebug
import javax.inject.Inject

@ActivityScope
class PrefsActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = PrefsActivity::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        // Value from androidx.preference.PreferenceFragment
        private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }

    @Inject internal lateinit var analytics: Analytics
    @Inject internal lateinit var launcherRole: LauncherRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not access dagger objects before this call
        MainApp
            .getAppComponent(this)
            .prefsActivityComponentFactory
            .create()
            .inject(this)

        setContentView(R.layout.prefs_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment(launcherRole))
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        analytics.sendPageView("Prefs Activity", "/prefs")
    }

    class SettingsFragment(
        private val launcherRole: LauncherRole
    ) : PreferenceFragmentCompat() {

        private val startForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { activityResult -> if (DEBUG) Log.d(TAG, "@@ activityResult = $activityResult") }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val bootPref = findPreference<SwitchPreferenceCompat>(AppPrefsValues.PREF_SYSTEM__START_ON_BOOT)
            bootPref?.apply {
                // TBD: re-enable once Accessibility API is used for this feature.
                isEnabled = false
                if (!isEnabled) {
                    isChecked = false
                    summaryProvider = null
                    summary = "Feature Not Supported Yet"
                }
            }

            val homePref = findPreference<SwitchPreferenceCompat>(AppPrefsValues.PREF_SYSTEM__HOME)
            homePref?.apply {
                if (Build.VERSION.SDK_INT >= LauncherRole.MIN_USAGE_API) {
                    isChecked = isEnabled && launcherRole.isRoleHeld()

                    setOnPreferenceChangeListener { _, newValue ->
                        newValue as Boolean
                        Log.d(TAG, "@@ HomePref changed to $newValue")
                        launcherRole.onRolePrefChanged(newValue)?.let { intent ->
                            val i = if (newValue) intent else Intent.createChooser(intent, "Select")
                            startForResult.launch(i)
                        }
                        return@setOnPreferenceChangeListener true
                    }
                } else {
                    isEnabled = false
                    isChecked = false
                    summaryProvider = null
                    summary = "Feature Not Supported"
                }
            }

            val countPref = findPreference<ListPreference>(AppPrefsValues.PREF_CAMERAS__COUNT)
            countPref?.apply {
                val numbers: List<String> = 1.rangeTo(AppMonitor.MAX_CAMERAS).map { it.toString() }
                countPref.entries = numbers.toTypedArray()
                countPref.entryValues = countPref.entries
                countPref.setDefaultValue(AppMonitor.MAX_CAMERAS)
                if (countPref.value == null) countPref.value = AppMonitor.MAX_CAMERAS.toString()
            }
        }

        @Suppress("DEPRECATION")
        override fun onDisplayPreferenceDialog(preference: Preference) {
            try {
                // That's a weird API that doesn't return a usable error/boolean directly.
                super.onDisplayPreferenceDialog(preference)
            } catch (e: IllegalArgumentException) {
                if (preference is CameraTransformPref) {
                    // Note: this process mirrors the implementation from
                    //       androidx.preference.PreferenceFragment:onDisplayPreferenceDialog
                    val f = CamTransformDialogFragment.newInstance(preference.key)
                    f.setTargetFragment(this, 0)
                    f.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
                }
            }
        }
    }
}
