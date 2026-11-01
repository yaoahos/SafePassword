package com.safeincloud.android.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safeincloud.android.MainActivity

/** 下载更新时的系统通知栏进度。 */
object UpdateNotifications {
    const val EXTRA_INSTALL_APK = "extra_install_apk"

    private const val CHANNEL_ID = "app_updates"
    private const val PROGRESS_ID = 0x5A1E
    private const val DONE_ID = 0x5A1F

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "下载新版本安装包时显示进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun progress(context: Context, percent: Int) {
        ensureChannel(context)
        val indeterminate = percent < 0
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 SafePassword 更新")
            .setContentText(if (indeterminate) "准备下载…" else "已完成 $percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        runCatching { NotificationManagerCompat.from(context).notify(PROGRESS_ID, builder.build()) }
    }

    /**
     * 下载完成：撤掉进度通知，另发一条可点击的安装通知。
     *
     * 以前这里和进度共用同一个 id，且被 runCatching 吞掉异常，只要发通知时抛一次错，
     * 进度通知就会永远停在「已完成 100%」且没有点击行为，用户看到的就是「点了没反应」。
     * 现在两条通知分开，失败会记日志并清理，不会留下一条点不动的进度条。
     */
    fun completed(context: Context, info: UpdateInfo, apkPath: String) {
        val manager = NotificationManagerCompat.from(context)
        runCatching { manager.cancel(PROGRESS_ID) }
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_INSTALL_APK, apkPath)
        }
        val pending = PendingIntent.getActivity(
            context,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SafePassword ${info.version} 已下载")
            .setContentText("点击安装新版本")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        runCatching { manager.notify(DONE_ID, builder.build()) }
    }

    /** 撤掉进度与安装两条通知（下载失败、取消下载或安装流程结束时调用）。 */
    fun clear(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        runCatching { manager.cancel(PROGRESS_ID) }
        runCatching { manager.cancel(DONE_ID) }
    }
}
