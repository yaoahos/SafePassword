package com.yaoahos.safepassword.android

import android.app.Application
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yaoahos.safepassword.android.data.AppContainer
import com.yaoahos.safepassword.android.ui.theme.WallpaperColorBus
import com.yaoahos.safepassword.android.ui.theme.isSystemWallpaperChange
import com.yaoahos.safepassword.android.ui.theme.wallpaperColorSupported

class SafePasswordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        attachWallpaperWatcher()
    }

    /**
     * 换壁纸时系统把新的 WallpaperColors 推给监听器，主题层据此实时重算主色，
     * 不用等 Activity resume。门槛与动态取色共用 MIN_WALLPAPER_SDK；回调的 which
     * 必须包含 FLAG_SYSTEM，否则只换锁屏壁纸也会把主色带偏。
     *
     * 这不是常驻服务：listener 跟随进程生命周期，进程回收即消失；无轮询、无唤醒。
     */
    private fun attachWallpaperWatcher() {
        if (!wallpaperColorSupported()) return
        val app = this
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (!isSystemWallpaperChange(which)) return@OnColorsChangedListener
            WallpaperColorBus.publish(app, colors)
        }
        runCatching {
            WallpaperManager.getInstance(app)
                .addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }.onFailure {
            Log.w("SPV-THEME", "壁纸色监听注册失败：${it.javaClass.simpleName}: ${it.message}")
        }
    }
}
