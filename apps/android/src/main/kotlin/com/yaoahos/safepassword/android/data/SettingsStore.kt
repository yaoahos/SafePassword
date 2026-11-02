package com.yaoahos.safepassword.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yaoahos.safepassword.core.json.AppJson
import com.yaoahos.safepassword.core.sync.WebDavConfig
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val autoLockSeconds: Int = 60,
    /** Legacy generic flag; kept so 1.2.x settings still decode. */
    val biometricEnabled: Boolean = false,
    val fingerprintEnabled: Boolean = false,
    val screenshotAllowed: Boolean = false,
    /** 自动备份间隔（天）；0 表示关闭，默认关闭，开启时再让用户选择备份目录。 */
    val autoBackupDays: Int = 0,
    /** 用户选定的备份目录（SAF tree uri）；为空表示还没选。 */
    val backupTreeUri: String = "",
    val lastBackupAt: Long = 0,
    val autoSync: Boolean = true,
    val hibpApiKey: String = "",
    val sortMode: String = "TITLE",
    val themeColor: Long = 0xFF1677FF,
    /** 外观模式：SYSTEM 跟随系统 / LIGHT 日间 / DARK 夜间。 */
    val themeMode: String = "SYSTEM",
    /** Empty means all entries; otherwise a label id shown by default. */
    val defaultLabelId: String? = null,
    /** Whether the vault search box also matches password field values. */
    val searchPasswords: Boolean = false,
    /**
     * 多久检查一次云端是否有更新（分钟）。0 表示关闭自动拉取。
     * 只做 HEAD 比对 Last-Modified，云上没有变化时不会产生整库下载。
     */
    /** 自动拉取云端更新的间隔（秒，0 = 关闭）。 */
    val pullIntervalSeconds: Int = 60
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context, "safepassword_settings", "safepassword_settings_key")

    init {
        // 1.4.x 的设置文件用的是改名前的旧文件名；这里只做一次性搬迁，避免用户重配 WebDAV。
        if (!prefs.getBoolean(KEY_MIGRATED_FROM_LEGACY, false)) {
            val legacy = runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    LEGACY_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrNull()
            if (legacy != null) {
                val editor = prefs.edit()
                legacy.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                editor.putBoolean(KEY_MIGRATED_FROM_LEGACY, true).apply()
            }
        }
    }

    /**
     * 加密存储是否真的可用。为 false 时 prefs 实际是明文文件，调用方应拒绝写入主密码、
     * WebDAV 密码等敏感内容，而不是静默降级。
     */
    val encrypted: Boolean
        get() = !degradedToPlaintext

    var settings: AppSettings
        get() {
            val stored = read(AppSettings.serializer(), KEY_SETTINGS) ?: return AppSettings()
            // 1.3.x 曾有“强制把允许截屏改回 true”的迁移，导致老用户被动继承可截屏状态。
            // 这里一次性改回默认的安全值（禁止截屏），需要的人可以自己再打开。
            if (!prefs.getBoolean(KEY_SCREENSHOT_SECURE_MIGRATED, false)) {
                prefs.edit().putBoolean(KEY_SCREENSHOT_SECURE_MIGRATED, true).apply()
                if (stored.screenshotAllowed) {
                    val migrated = stored.copy(screenshotAllowed = false)
                    write(AppSettings.serializer(), KEY_SETTINGS, migrated)
                    return migrated
                }
            }
            if (!prefs.getBoolean(KEY_THEME_DEFAULT_MIGRATED, false)) {
                prefs.edit().putBoolean(KEY_THEME_DEFAULT_MIGRATED, true).apply()
                if (stored.themeColor == 0xFFFF9500L) {
                    val migrated = stored.copy(themeColor = 0xFF1677FF)
                    write(AppSettings.serializer(), KEY_SETTINGS, migrated)
                    return migrated
                }
            }
            if (!prefs.getBoolean(KEY_BACKUP_DEFAULT_MIGRATED, false)) {
                prefs.edit().putBoolean(KEY_BACKUP_DEFAULT_MIGRATED, true).apply()
                if (stored.autoBackupDays == 7) {
                    val migrated = stored.copy(autoBackupDays = 1)
                    write(AppSettings.serializer(), KEY_SETTINGS, migrated)
                    return migrated
                }
            }
            return stored
        }
        set(value) = write(AppSettings.serializer(), KEY_SETTINGS, value)

    var webDav: WebDavConfig
        get() = read(WebDavConfig.serializer(), KEY_WEBDAV) ?: WebDavConfig()
        set(value) = write(WebDavConfig.serializer(), KEY_WEBDAV, value)

    var lastVaultId: String?
        get() = prefs.getString(KEY_LAST_VAULT, null)
        set(value) = prefs.edit().putString(KEY_LAST_VAULT, value).apply()

    private fun <T> read(serializer: kotlinx.serialization.KSerializer<T>, key: String): T? {
        val text = prefs.getString(key, null) ?: return null
        return runCatching { AppJson.instance.decodeFromString(serializer, text) }.getOrNull()
    }

    private fun <T> write(serializer: kotlinx.serialization.KSerializer<T>, key: String, value: T) {
        prefs.edit().putString(key, AppJson.instance.encodeToString(serializer, value)).apply()
    }

    companion object {
        private const val TAG = "SettingsStore"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_WEBDAV = "webdav"
        private const val KEY_LAST_VAULT = "last_vault"
        private const val KEY_SCREENSHOT_SECURE_MIGRATED = "screenshot_secure_migrated"
        private const val KEY_THEME_DEFAULT_MIGRATED = "theme_default_migrated"
        private const val KEY_BACKUP_DEFAULT_MIGRATED = "backup_default_migrated"
        private const val KEY_MIGRATED_FROM_LEGACY = "migrated_from_legacy_file"

        /** 改名前的设置文件名，只用于一次性迁移。 */
        private const val LEGACY_PREFS_NAME = "safeincloud_settings"

        /** 只要有一次创建加密存储失败就置位，供上层决定是否拒绝保存敏感字段。 */
        @Volatile
        private var degradedToPlaintext: Boolean = false

        fun createPrefs(context: Context, fileName: String, alias: String): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                prefs
            } catch (e: Exception) {
                degradedToPlaintext = true
                // 加密存储不可用时不再静默降级：记录日志，并由上层拒绝保存敏感字段。
                Log.w(TAG, "加密存储不可用，$fileName 已退回明文；将拒绝保存敏感内容", e)
                context.getSharedPreferences("${fileName}_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
