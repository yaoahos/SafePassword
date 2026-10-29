package com.safeincloud.android.data

import android.content.Context
import com.safeincloud.core.storage.AttachmentStore
import com.safeincloud.core.storage.BackupManager
import com.safeincloud.core.storage.VaultStore
import java.io.File
import javax.crypto.SecretKey

class AppContainer(context: Context) {
    val vaultStore = VaultStore(File(context.filesDir, "data").toPath())
    val backupManager = BackupManager(File(context.filesDir, "backups").toPath())
    val settings = SettingsStore(context)
    val biometric = BiometricStore(context)

    fun attachmentStore(vaultId: String, contentKey: SecretKey): AttachmentStore =
        AttachmentStore(vaultStore.attachmentsDir(vaultId), contentKey)
}
