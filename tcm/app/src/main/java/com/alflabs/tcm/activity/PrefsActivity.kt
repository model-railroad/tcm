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
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.alflabs.tcm.BuildConfig
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.app.BootReceiver
import com.alflabs.tcm.app.LauncherRole
import com.alflabs.tcm.app.MonitorMixin

class PrefsActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = PrefsActivity::class.java.simpleName
        private val DEBUG: Boolean = BuildConfig.DEBUG
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prefs_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val startForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { activityResult -> if (DEBUG) Log.d(TAG, "@@ activityResult = $activityResult") }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val bootPref = findPreference<SwitchPreferenceCompat>(AppPrefsValues.PREF_SYSTEM__START_ON_BOOT)
            bootPref?.apply {
                isEnabled = Build.VERSION.SDK_INT <= BootReceiver.MAX_USAGE_API
                if (!isEnabled) isChecked = false
            }

            val homePref = findPreference<SwitchPreferenceCompat>(AppPrefsValues.PREF_SYSTEM__HOME)
            homePref?.apply {
                if (Build.VERSION.SDK_INT >= LauncherRole.MIN_USAGE_API) {
                    val launcherRole = LauncherRole(context)
                    isEnabled = launcherRole.isRoleAvailable()
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
                }
            }

            val countPref = findPreference<ListPreference>(AppPrefsValues.PREF_CAMERAS__COUNT)
            countPref?.apply {
                val numbers: List<String> = 1.rangeTo(MonitorMixin.MAX_CAMERAS).map { it.toString() }
                countPref.entries = numbers.toTypedArray()
                countPref.entryValues = countPref.entries
                countPref.setDefaultValue(MonitorMixin.MAX_CAMERAS)
                if (countPref.value == null) countPref.value = MonitorMixin.MAX_CAMERAS.toString()
            }
        }
    }
}
