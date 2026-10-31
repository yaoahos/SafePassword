package com.safeincloud.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safeincloud.android.ui.AppRoot
import com.safeincloud.android.ui.AppViewModel
import com.safeincloud.android.update.ApkInstaller
import com.safeincloud.android.update.UpdateNotifications
import java.io.File

class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    fun setSecure(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        val file = resolveUpdateApk(raw) ?: return
        ApkInstaller.install(this, file)
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
