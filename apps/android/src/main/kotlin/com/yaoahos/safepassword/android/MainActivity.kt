package com.yaoahos.safepassword.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yaoahos.safepassword.android.ui.AppRoot
import com.yaoahos.safepassword.android.ui.AppViewModel
import com.yaoahos.safepassword.android.update.ApkInstaller
import com.yaoahos.safepassword.android.update.UpdateNotifications
import java.io.File

/**
 * 用 [FragmentActivity] 而不是 `AppCompatActivity`。
 *
 * `AppCompatActivity.onCreate` 会执行 `AppCompatDelegate.applyDayNight()`：首次冷启动时
 * 若系统深浅色与上次记录不一致，它会写回偏好并 `recreate()` 自己，系统启动动画放到
 * 一半被打断，用户看到的就是「弹一下又缩回桌面，点第二次才正常」；第二次本地记录
 * 已同步，不再重建，于是又表现得完全正常。本应用界面全由 Compose 的 MaterialTheme
 * 按应用内设置绘制，不依赖 AppCompat 的夜间模式，也不需要 ActionBar。
 * 保留 FragmentActivity 是因为 BiometricPrompt 的 CryptoObject 需要它。
 */
class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    /**
     * 从通知点安装时，通知的点击事件常常早于 Compose 首帧组合，直接写 state.message
     * 会被首帧的 collect 覆盖。先挂在这里，由 AppRoot 首帧取走并显示。
     */
    var pendingInstallError: String? = null
        private set

    internal fun consumePendingInstallError(): String? =
        pendingInstallError.also { pendingInstallError = null }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 默认禁止截屏：这里只登记期望值，真正改窗口 flag 推迟到启动放大动画播完
        // （见 onWindowFocusChanged）。在动画期间改窗口属性会让 MIUI/HyperOS 掐断
        // 放大动画退回桌面，用户看到的就是「弹到一半缩回」。设置里允许截屏时，
        // AppRoot 读到设置后会把期望值翻成 false。
        setSecure(true)
        setContent {
            AppRoot(viewModel = viewModel, activity = this)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
        busyTicker.removeCallbacks(busyTick)
        busyTicker.post(busyTick)
    }

    override fun onStop() {
        busyTicker.removeCallbacks(busyTick)
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    /** 禁止截屏的期望值；`onCreate` 与 AppRoot 首帧都会调 [setSecure]，真正改 flag 的时机见 [windowReady]。 */
    private var secureDesired = true

    /**
     * 系统启动放大动画是否已经播完。
     *
     * MIUI/HyperOS 从桌面图标放大窗口的动画期间，窗口属性一变（哪怕是挂上 FLAG_SECURE）
     * 就会取消放大、退回桌面——用户看到的就是「弹到一半缩回，点第二次才正常」。
     * 所以启动阶段只记下期望值，等窗口拿到焦点、动画播完再真正改 flag。
     * 热启动没有放大动画问题（flag 早已稳定），首帧的幂等调用也不会改动窗口。
     */
    private var windowReady = false

    fun setSecure(enabled: Boolean) {
        secureDesired = enabled
        if (!windowReady) return
        applySecure()
    }

    /** 幂等地把窗口 flag 对齐到 [secureDesired]；值没变就别碰窗口属性。 */
    private fun applySecure() {
        val secure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (secure == secureDesired) return
        if (secureDesired) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !windowReady) {
            windowReady = true
            // 再让一拍，等系统的放大动画真正收尾。这约 300ms 内窗口尚未禁止截屏，
            // 用户来不及操作；退后台抓最近任务快照时 flag 早已挂上，预览仍然是空白。
            window.decorView.postDelayed({ applySecure() }, 300)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 主窗口的任何触摸都算「用户还在用」。对话框是独立窗口、键盘输入也不走这里，
        // 那两种情况由下面的 busyTicker 另行上报，避免「正在打字/点对话框却被锁」。
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) viewModel.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 「输入法开着」「对话框在前台」这两类操作不经过主窗口的触摸上报，
     * 但显然不是闲置。每秒看一眼，命中就上报一次用户活动，闲置计时随之重置。
     */
    private val busyTicker = android.os.Handler(android.os.Looper.getMainLooper())
    private val busyTick = object : Runnable {
        override fun run() {
            val decor = window.decorView
            val imeVisible = androidx.core.view.ViewCompat
                .getRootWindowInsets(decor)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            // 对话框会抢走窗口焦点；键盘不会（IME 窗口不夺焦点），所以两条都要判。
            val dialogInFront = !decor.hasWindowFocus()
            if (imeVisible || dialogInFront) viewModel.onUserInteraction()
            busyTicker.postDelayed(this, 1000)
        }
    }

    /** Android 13+ 下载更新前请求通知权限，失败也不阻塞下载。 */
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 点击“更新已下载”通知后直接拉起系统安装器。
     *
     * MainActivity 必须 exported（LAUNCHER），因此这个 intent extra 任何应用都能伪造；
     * 只接受本应用缓存目录下我们自己的 .apk，避免被当作任意安装包的启动器。
     */
    private fun handleIntent(intent: Intent?) {
        val raw = intent?.getStringExtra(UpdateNotifications.EXTRA_INSTALL_APK) ?: return
        intent.removeExtra(UpdateNotifications.EXTRA_INSTALL_APK)
        UpdateNotifications.clear(this)
        val file = resolveUpdateApk(raw)
        if (file == null) {
            viewModel.showMessage("安装包已失效，请在应用内重新下载")
            return
        }
        val error = ApkInstaller.install(this, file).exceptionOrNull()
        if (error != null) {
            // 以前这里吞掉异常，表现就是「点了通知没反应」。
            // 通知的点击事件会比 Compose 首帧先到，直接读 state.message 大概率被首帧组合覆盖掉，
            // 所以记一个待发提示，等 AppRoot 起来后取用，确保用户一定看得见失败原因。
            pendingInstallError = error.message ?: "无法启动安装器，请手动安装"
            android.util.Log.w("MainActivity", "从通知安装失败", error)
        }
    }

    /** 只放行 cacheDir/updates/<name>.apk 内的真实文件。 */
    private fun resolveUpdateApk(raw: String): File? = runCatching {
        val dir = File(cacheDir, "updates").canonicalFile
        val file = File(raw).canonicalFile
        val inside = file.path.startsWith(dir.path + File.separator)
        if (!inside || !file.name.endsWith(".apk", ignoreCase = true) || !file.isFile) return null
        file
    }.getOrNull()
}
