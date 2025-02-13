/*
 * Project: TCM
 * Copyright (C) 2017 alf.labs gmail com,
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

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.alflabs.tcm.dagger.AppQualifier
import com.alflabs.tcm.util.ILogger
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppContextModule(private val context: Context) {
    /**
     * Provides an Android context, specifically this one from the app component.
     * Users request it by using the @AppQualifier to distinguish it from the one provided by the activity.
     */
    @Provides
    @AppQualifier
    fun providesContext(): Context {
        return context
    }

    /**
     * Provides a singleton instance of the android logger. This method doesn't do any logic
     * to make sure it's a singleton. However in the DaggerIAppComponent, the result is wrapped
     * in a DoubleCheck that will cache and return a singleton value. Because it's a @Singleton
     * it is also app-wide and shared with all sub-components.
     */
    @Provides
    @Singleton
    fun providesLogger(): ILogger {
        return AppLogger()
    }

    @Provides
    @Singleton
    fun providesPowerManager(): PowerManager {
        return context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Provides
    @Singleton
    fun providesWifiManager(): WifiManager {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

//    @Provides
//    @Singleton
//    fun providesAlarmManager(): AlarmManager {
//        return mContext.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//    }
//
//    @Provides
//    @Singleton
//    fun providesNotificationManager(): NotificationManager {
//        return mContext.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//    }
}
