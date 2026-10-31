package com.safeincloud.core.crypto

import com.safeincloud.core.json.AppJson
import com.safeincloud.core.io.AtomicFiles
import com.safeincloud.core.model.Vault
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("主密码错误，或数据库文件已损坏")
class VaultFormatException(message: String) : Exception(message)

/**
 * 加密保险库容器。
 *
 * 文件结构：
 *   magic "SICV" / version / PBKDF2 迭代次数 / salt / 被 KEK 包裹的 DEK / AES-GCM 密文
 *
 * 主密码只用于派生 KEK；真正加密数据的是随机 DEK。修改主密码只需重新包裹 DEK，
 * 不需要重新加密整个数据库，附件也不受影响。
 */
class VaultContainer private constructor(
    var vault: Vault,
    private val dek: SecretKey,
    private var salt: ByteArray,
    private var wrappedDek: ByteArray,
    private var iterations: Int
) {
    val contentKey: SecretKey
        get() {
            if (vault.contentKey.isBlank()) {
                vault.contentKey = Crypto.toBase64(Crypto.randomBytes(32))
            }
            return SecretKeySpec(Crypto.fromBase64(vault.contentKey), "AES")
        }

    fun toBytes(): ByteArray {
        ensureContentKey()
        val payload = Crypto.encrypt(
            dek,
            AppJson.instance.encodeToString(Vault.serializer(), vault).toByteArray(Charsets.UTF_8),
            PAYLOAD_AAD
        )
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(iterations)
            data.writeInt(salt.size)
            data.write(salt)
            data.writeInt(wrappedDek.size)
            data.write(wrappedDek)
            data.writeInt(payload.size)
            data.write(payload)
        }
        return out.toByteArray()
    }

    fun saveTo(path: Path) {
        AtomicFiles.writeAtomic(path, toBytes())
    }

    fun changePassword(newPassword: CharArray): VaultContainer {
        val newSalt = Crypto.randomBytes(SALT_BYTES)
        val newKek = Crypto.deriveKey(newPassword, newSalt, iterations)
        val newWrapped = Crypto.wrapDek(newKek, dek)
        return VaultContainer(vault, dek, newSalt, newWrapped, iterations)
    }

    fun withVault(newVault: Vault): VaultContainer =
        VaultContainer(newVault, dek, salt, wrappedDek, iterations)

    fun ensureContentKey() {
        if (vault.contentKey.isBlank()) {
            vault.contentKey = Crypto.toBase64(Crypto.randomBytes(32))
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte(), 'V'.code.toByte())
        private const val VERSION = 1
        private const val SALT_BYTES = 16

        /** 单个库文件的密文上限，防止伪造头部把内存撑爆。 */
        private const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
        private val PAYLOAD_AAD = "SICV-PAYLOAD".toByteArray(Charsets.UTF_8)

        fun create(vault: Vault, password: CharArray): VaultContainer {
            val salt = Crypto.randomBytes(SALT_BYTES)
            val kek = Crypto.deriveKey(password, salt, Crypto.PBKDF2_ITERATIONS)
            val dek = SecretKeySpec(Crypto.randomBytes(32), "AES")
            val wrapped = Crypto.wrapDek(kek, dek)
            if (vault.contentKey.isBlank()) {
                vault.contentKey = Crypto.toBase64(Crypto.randomBytes(32))
            }
            return VaultContainer(vault, dek, salt, wrapped, Crypto.PBKDF2_ITERATIONS)
        }

        fun open(path: Path, password: CharArray): VaultContainer =
            open(Files.readAllBytes(path), password)

        fun open(bytes: ByteArray, password: CharArray): VaultContainer {
            val header = try {
                readHeader(bytes)
            } catch (e: Exception) {
                throw VaultFormatException("无法读取数据库文件：${e.message}")
            }
            val kek = Crypto.deriveKey(password, header.salt, header.iterations)
            val dek = try {
                Crypto.unwrapDek(kek, header.wrappedDek)
            } catch (_: Exception) {
                throw WrongPasswordException()
            }
            val plain = try {
                Crypto.decrypt(dek, header.payload, PAYLOAD_AAD)
            } catch (_: Exception) {
                throw WrongPasswordException()
            }
            val vault = try {
                AppJson.instance.decodeFromString(Vault.serializer(), plain.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                throw VaultFormatException("数据库内容无法解析：${e.message}")
            }
            if (vault.contentKey.isBlank()) {
                vault.contentKey = Crypto.toBase64(Crypto.randomBytes(32))
            }
            return VaultContainer(vault, dek, header.salt, header.wrappedDek, header.iterations)
        }

        private data class Header(
            val iterations: Int,
            val salt: ByteArray,
            val wrappedDek: ByteArray,
            val payload: ByteArray
        )

        private fun readHeader(bytes: ByteArray): Header {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val magic = ByteArray(4)
                data.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw VaultFormatException("不是有效的保险库文件")
                val version = data.readInt()
                if (version != VERSION) throw VaultFormatException("不支持的数据库版本：$version")
                val iterations = data.readInt()
                if (iterations < 10_000 || iterations > 10_000_000) {
                    throw VaultFormatException("PBKDF2 迭代次数异常")
                }
                val salt = data.readChunk(8, 64, "salt")
                val wrapped = data.readChunk(40, 256, "密钥包裹")
                val payload = data.readChunk(1, MAX_PAYLOAD_BYTES, "密文")
                return Header(iterations, salt, wrapped, payload)
            }
        }

        /**
         * 读取“长度 + 内容”字段：先校验长度范围与剩余可读字节数，再分配数组。
         * 直接 ByteArray(readInt()) 会让损坏或伪造的文件用超大长度把进程撑爆。
         */
        private fun DataInputStream.readChunk(min: Int, max: Int, name: String): ByteArray {
            val size = readInt()
            if (size < min || size > max) throw VaultFormatException("$name 长度异常")
            if (size > available()) throw VaultFormatException("$name 长度超出文件实际大小")
            return ByteArray(size).also { readFully(it) }
        }
    }
}
