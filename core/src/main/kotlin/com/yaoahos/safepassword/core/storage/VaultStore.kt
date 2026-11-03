package com.yaoahos.safepassword.core.storage

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.io.AtomicFiles
import com.yaoahos.safepassword.core.json.AppJson
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultIndex
import com.yaoahos.safepassword.core.model.VaultMeta
import com.yaoahos.safepassword.core.model.newId
import com.yaoahos.safepassword.core.model.now
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class VaultStore(private val root: Path) {
    private val vaultsDir: Path = root.resolve("vaults")
    private val indexFile: Path = vaultsDir.resolve("index.json")

    init {
        Files.createDirectories(vaultsDir)
    }

    fun vaultFile(meta: VaultMeta): Path = vaultsDir.resolve(meta.fileName)

    @Synchronized
    fun list(): List<VaultMeta> {
        val index = readIndex()
        var changed = false
        val missing = index.vaults.filterNot { Files.exists(vaultsDir.resolve(it.fileName)) }
        if (missing.isNotEmpty()) {
            index.vaults.removeAll(missing.toSet())
            changed = true
        }
        val known = index.vaults.map { it.fileName }.toSet()
        Files.newDirectoryStream(vaultsDir, "*.sicv").use { stream ->
            for (path in stream) {
                val fileName = path.fileName.toString()
                if (fileName !in known) {
                    index.vaults.add(
                        VaultMeta(
                            id = fileName.removeSuffix(".sicv"),
                            name = "密码库",
                            fileName = fileName,
                            createdAt = Files.getLastModifiedTime(path).toMillis(),
                            lastOpenedAt = 0
                        )
                    )
                    changed = true
                }
            }
        }
        if (changed) writeIndex(index)
        return index.vaults.sortedByDescending { it.lastOpenedAt }
    }

    @Synchronized
    fun create(name: String, password: CharArray): Pair<VaultContainer, VaultMeta> {
        val vault = Vault(
            name = name.trim().ifBlank { "我的密码库" },
            // 云端键在这里一次生成、终身不变；改名只改显示名，不换云端文件。
        )
        val container = VaultContainer.create(vault, password)
        val fileName = "${vault.id}.sicv"
        val meta = VaultMeta(
            id = vault.id,
            name = vault.name,
            fileName = fileName,
            createdAt = now(),
            lastOpenedAt = now()
        )
        container.saveTo(vaultsDir.resolve(fileName))
        val index = readIndex()
        index.vaults.add(meta)
        writeIndex(index)
        return container to meta
    }

    @Synchronized
    fun open(meta: VaultMeta, password: CharArray): VaultContainer {
        val container = VaultContainer.open(vaultFile(meta), password)
        val index = readIndex()
        val stored = index.vaults.firstOrNull { it.id == meta.id }
        if (stored != null) {
            stored.lastOpenedAt = now()
            stored.name = container.vault.name
            writeIndex(index)
        }
        return container
    }

    @Synchronized
    fun save(container: VaultContainer, meta: VaultMeta) {
        container.saveTo(vaultFile(meta))
        val index = readIndex()
        val stored = index.vaults.firstOrNull { it.id == meta.id }
        if (stored != null) {
            stored.name = container.vault.name
            stored.lastOpenedAt = now()
            writeIndex(index)
        }
    }

    @Synchronized
    fun updateMeta(meta: VaultMeta) {
        val index = readIndex()
        val position = index.vaults.indexOfFirst { it.id == meta.id }
        if (position >= 0) {
            index.vaults[position] = meta
            writeIndex(index)
        }
    }

    @Synchronized
    fun delete(meta: VaultMeta) {
        Files.deleteIfExists(vaultFile(meta))
        val index = readIndex()
        index.vaults.removeAll { it.id == meta.id }
        writeIndex(index)
    }

    @Synchronized
    fun importEncrypted(bytes: ByteArray, password: CharArray, newName: String): Pair<VaultContainer, VaultMeta> {
        val opened = VaultContainer.open(bytes, password)
        val imported = opened.vault.copy(
            id = newId(),
            name = newName.trim().ifBlank { opened.vault.name },
            updatedAt = now()
        )
        return createFromVault(imported, password)
    }

    @Synchronized
    fun importPlain(vault: Vault, password: CharArray, newName: String): Pair<VaultContainer, VaultMeta> {
        val imported = vault.copy(
            id = newId(),
            name = newName.trim().ifBlank { vault.name },
            updatedAt = now()
        )
        return createFromVault(imported, password)
    }

    private fun createFromVault(vault: Vault, password: CharArray): Pair<VaultContainer, VaultMeta> {
        val container = VaultContainer.create(vault, password)
        val fileName = "${vault.id}.sicv"
        val meta = VaultMeta(
            id = vault.id,
            name = vault.name,
            fileName = fileName,
            createdAt = now(),
            lastOpenedAt = now()
        )
        container.saveTo(vaultsDir.resolve(fileName))
        val index = readIndex()
        index.vaults.add(meta)
        writeIndex(index)
        return container to meta
    }

    private fun readIndex(): VaultIndex {
        if (!Files.exists(indexFile)) return VaultIndex()
        return try {
            AppJson.instance.decodeFromString(
                VaultIndex.serializer(),
                String(Files.readAllBytes(indexFile), Charsets.UTF_8)
            )
        } catch (_: Exception) {
            VaultIndex()
        }
    }

    private fun writeIndex(index: VaultIndex) {
        Files.createDirectories(vaultsDir)
        AtomicFiles.writeAtomic(
            indexFile,
            AppJson.instance.encodeToString(VaultIndex.serializer(), index).toByteArray(Charsets.UTF_8)
        )
    }
}
