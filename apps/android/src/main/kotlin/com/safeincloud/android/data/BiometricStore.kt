package com.safeincloud.android.data

import android.content.Context
import android.content.SharedPreferences

class BiometricStore(context: Context) {
    private val prefs: SharedPreferences =
        SettingsStore.createPrefs(context, "safeincloud_biometric", "safeincloud_biometric_key")

    fun save(vaultId: String, password: String) {
        prefs.edit().putString("pwd_$vaultId", password).apply()
    }

    fun get(vaultId: String): String? = prefs.getString("pwd_$vaultId", null)

    fun has(vaultId: String): Boolean = !get(vaultId).isNullOrBlank()

    fun clear(vaultId: String) {
        prefs.edit().remove("pwd_$vaultId").apply()
    }
}
