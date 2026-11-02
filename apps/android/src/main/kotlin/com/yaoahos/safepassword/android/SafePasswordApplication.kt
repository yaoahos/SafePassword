package com.yaoahos.safepassword.android

import android.app.Application
import com.yaoahos.safepassword.android.data.AppContainer

class SafePasswordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
