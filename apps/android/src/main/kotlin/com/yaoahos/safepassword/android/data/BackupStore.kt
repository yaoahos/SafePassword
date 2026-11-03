package com.yaoahos.safepassword.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条本地备份。备份文件存放在用户自己选的目录里（SAF），不再写应用内部存储。 */
data class BackupEntry(
    val uri: Uri,
    val name: String,
    val createdAt: Long,
    val size: Long
)

/**
 * 基于存储访问框架（SAF）的备份仓库。
 *
 * 用户通过「选择备份位置」指定一个目录，应用只持有该目录的持久化授权；
 * 备份文件在文件管理器里可以直接看到、拷走，卸载应用也不会被删。
 */
class BackupStore(private val context: Context) {

    fun folderName(treeUri: String): String {
        if (treeUri.isBlank()) return ""
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name.orEmpty()
        }.getOrDefault("")
    }

    private fun tree(treeUri: String): DocumentFile? {
        if (treeUri.isBlank()) return null
        val doc = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull() ?: return null
        return doc.takeIf { it.canWrite() }
    }

    fun list(treeUri: String): List<BackupEntry> {
        val tree = tree(treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && it.name?.endsWith(".spv") == true }
            .map { file ->
                BackupEntry(
                    uri = file.uri,
                    name = file.name.orEmpty(),
                    createdAt = file.lastModified(),
                    size = file.length()
                )
            }
            .sortedByDescending { it.createdAt }
    }

    fun backup(treeUri: String, bytes: ByteArray, vaultName: String): BackupEntry? {
        val tree = tree(treeUri) ?: return null
        val stamp = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = vaultName.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(40).ifBlank { "vault" }
        val name = "$safeName-$stamp.spv"
        val file = tree.createFile("application/octet-stream", name) ?: return null
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) } ?: return null
        return BackupEntry(file.uri, file.name ?: name, file.lastModified(), bytes.size.toLong())
    }

    fun read(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    fun delete(uri: Uri): Boolean = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
    }.getOrDefault(false)

    fun prune(treeUri: String, keep: Int) {
        list(treeUri).drop(keep).forEach { delete(it.uri) }
    }

    /** 记住目录授权，重启后仍然可写。 */
    fun takePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    companion object {
        /** 备份位置选择器的 MIME 过滤器。 */
        const val TREE_MIME = "application/octet-stream"
    }
}
