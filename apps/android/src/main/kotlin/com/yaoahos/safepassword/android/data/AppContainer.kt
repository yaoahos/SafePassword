package com.yaoahos.safepassword.android.data

import android.content.Context
import com.yaoahos.safepassword.core.storage.VaultStore
import java.io.File

/**
 * 应用级单例依赖。
 *
 * 四个 store 全部懒加载：Application.onCreate 里只 new 一个空壳，不在主线程碰
 * 磁盘与 Keystore；真正构造发生在 ViewModel 的 IO 协程首次访问时。
 * （以前 SettingsStore 构造期就同步建两次 MasterKey 并打开加密配置，
 * 冷启动首帧被推后，桌面上的启动动画会「弹起又缩回」。）
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val vaultStore: VaultStore by lazy { VaultStore(File(appContext.filesDir, "data").toPath()) }
    val backupStore: BackupStore by lazy { BackupStore(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val biometric: BiometricStore by lazy { BiometricStore(appContext) }
}
