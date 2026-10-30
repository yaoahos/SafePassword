package com.safeincloud.core.storage

import com.safeincloud.core.crypto.Crypto
import com.safeincloud.core.io.AtomicFiles
import com.safeincloud.core.model.AttachmentRef
import com.safeincloud.core.model.newId
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.crypto.SecretKey

class AttachmentStore(
    private val root: Path,
    private val contentKey: SecretKey
) {
    init {
        Files.createDirectories(root)
    }

    fun pathFor(id: String): Path = root.resolve("$id.sica")

    fun exists(id: String): Boolean = Files.exists(pathFor(id))

    fun add(input: InputStream, name: String, mime: String, maxBytes: Long = 10L * 1024 * 1024): AttachmentRef {
        val bytes = readLimited(input, maxBytes)
        val id = newId()
        val encrypted = Crypto.encrypt(contentKey, bytes, id.toByteArray(Charsets.UTF_8))
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION)
        out.write(encrypted)
        writeAtomic(pathFor(id), out.toByteArray())
        return AttachmentRef(id = id, name = name, mime = mime, size = bytes.size.toLong())
    }

    fun read(ref: AttachmentRef): ByteArray {
        val bytes = Files.readAllBytes(pathFor(ref.id))
        if (bytes.size < MAGIC.size + 1 || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalStateException("附件格式无效")
        }
        val version = bytes[MAGIC.size].toInt()
        if (version != VERSION) throw IllegalStateException("不支持的附件版本")
        val encrypted = bytes.copyOfRange(MAGIC.size + 1, bytes.size)
        return Crypto.decrypt(contentKey, encrypted, ref.id.toByteArray(Charsets.UTF_8))
    }

    fun readRaw(id: String): ByteArray? =
        if (exists(id)) Files.readAllBytes(pathFor(id)) else null

    fun writeRaw(id: String, bytes: ByteArray) {
        writeAtomic(pathFor(id), bytes)
    }

    fun delete(id: String) {
        Files.deleteIfExists(pathFor(id))
    }

    private fun writeAtomic(path: Path, bytes: ByteArray) {
        AtomicFiles.writeAtomic(path, bytes)
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IllegalArgumentException("附件不能超过 ${maxBytes / 1024 / 1024} MB")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte())
        private const val VERSION = 1
    }
}
