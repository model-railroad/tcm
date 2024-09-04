/*
 * Project: RTAC
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

import android.app.Application
import android.content.Context
import com.alflabs.tcm.util.ILogger
import javax.inject.Inject

class MainApp : Application() {

    companion object {
        fun getAppComponent(context: Context): IAppComponent {
            return (context.applicationContext as MainApp).appComponent
        }
    }

    lateinit var appComponent: IAppComponent
        private set

    @Inject
    lateinit var mLogger: ILogger

    override fun onCreate() {
        super.onCreate()

        appComponent = createDaggerAppComponent()
        appComponent.inject(this)
        mLogger.log("App", "onCreate")
    }

    private fun createDaggerAppComponent(): IAppComponent {
        return DaggerIAppComponent
            .builder()
//            .appContextModule(AppContextModule(applicationContext))
//            .appDataModule(AppDataModule())
            .build()
    }
}
