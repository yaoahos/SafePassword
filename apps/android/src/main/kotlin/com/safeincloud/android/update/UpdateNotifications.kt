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
    private const val NOTIFICATION_ID = 0x5A1E

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
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    fun completed(context: Context, info: UpdateInfo, apkPath: String) {
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
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
