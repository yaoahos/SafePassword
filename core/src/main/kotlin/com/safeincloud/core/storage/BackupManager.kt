package com.safeincloud.core.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupInfo(
    val path: Path,
    val name: String,
    val createdAt: Long,
    val size: Long
)

class BackupManager(private val root: Path) {
    init {
        Files.createDirectories(root)
    }

    fun backup(vaultFile: Path, vaultName: String): BackupInfo {
        Files.createDirectories(root)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = vaultName.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(40).ifBlank { "vault" }
        val target = root.resolve("$safeName-$stamp.sicv")
        Files.copy(vaultFile, target, StandardCopyOption.REPLACE_EXISTING)
        return BackupInfo(target, target.fileName.toString(), Files.getLastModifiedTime(target).toMillis(), Files.size(target))
    }

    fun list(): List<BackupInfo> {
        if (!Files.exists(root)) return emptyList()
        return Files.newDirectoryStream(root, "*.sicv").use { stream ->
            stream.mapNotNull { path ->
                try {
                    BackupInfo(
                        path = path,
                        name = path.fileName.toString(),
                        createdAt = Files.getLastModifiedTime(path).toMillis(),
                        size = Files.size(path)
                    )
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.createdAt }
        }
    }

    fun restore(backup: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING)
    }

    fun prune(keep: Int = 10) {
        list().drop(keep).forEach { info ->
            runCatching { Files.deleteIfExists(info.path) }
        }
    }

    fun delete(info: BackupInfo) {
        Files.deleteIfExists(info.path)
    }
}
