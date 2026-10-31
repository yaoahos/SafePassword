package com.safeincloud.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.safeincloud.core.json.AppJson
import com.safeincloud.core.sync.WebDavConfig
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val autoLockSeconds: Int = 60,
    /** Legacy generic flag; kept so 1.2.x settings still decode. */
    val biometricEnabled: Boolean = false,
    val fingerprintEnabled: Boolean = false,
    val screenshotAllowed: Boolean = true,
    val autoBackupDays: Int = 1,
    val lastBackupAt: Long = 0,
    val autoSync: Boolean = true,
    val hibpApiKey: String = "",
    val sortMode: String = "TITLE",
    val themeColor: Long = 0xFF1677FF,
    /** Empty means all entries; otherwise a label id shown by default. */
    val defaultLabelId: String? = null,
    /** Whether the vault search box also matches password field values. */
    val searchPasswords: Boolean = false,
    /**
     * 多久检查一次云端是否有更新（分钟）。0 表示关闭自动拉取。
     * 只做 HEAD 比对 Last-Modified，云上没有变化时不会产生整库下载。
     */
    val pullIntervalMinutes: Int = 1
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context, "safeincloud_settings", "safeincloud_settings_key")

    var settings: AppSettings
        get() {
            val stored = read(AppSettings.serializer(), KEY_SETTINGS) ?: return AppSettings()
            if (!prefs.getBoolean(KEY_SCREENSHOT_DEFAULT_MIGRATED, false)) {
                prefs.edit().putBoolean(KEY_SCREENSHOT_DEFAULT_MIGRATED, true).apply()
                if (!stored.screenshotAllowed) {
                    val migrated = stored.copy(screenshotAllowed = true)
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
        private const val KEY_SCREENSHOT_DEFAULT_MIGRATED = "screenshot_default_migrated"
        private const val KEY_THEME_DEFAULT_MIGRATED = "theme_default_migrated"
        private const val KEY_BACKUP_DEFAULT_MIGRATED = "backup_default_migrated"

        fun createPrefs(context: Context, fileName: String, alias: String): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // 加密存储不可用时回退到明文，保持原有可用性；仅记录日志便于排查，不改变行为。
                Log.w(TAG, "加密存储初始化失败，回退到明文 SharedPreferences：$fileName", e)
                context.getSharedPreferences("${fileName}_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
