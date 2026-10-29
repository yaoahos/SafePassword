package com.safeincloud.android

import android.app.Application
import com.safeincloud.android.data.AppContainer

class SafeInCloudApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
