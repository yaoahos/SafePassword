package com.yaoahos.safepassword.android.data

import android.content.Context
import com.yaoahos.safepassword.core.storage.VaultStore
import java.io.File

class AppContainer(context: Context) {
    val vaultStore = VaultStore(File(context.filesDir, "data").toPath())
    val backupStore = BackupStore(context)
    val settings = SettingsStore(context)
    val biometric = BiometricStore(context)
}
