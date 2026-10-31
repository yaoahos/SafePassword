package com.safeincloud.android.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 指纹解锁凭据存储。
 *
 * 主密码不再以明文形式写进 SharedPreferences：它用 Android Keystore 里一把
 * “每次使用都要用户认证”的 AES 密钥加密后保存，解密必须经过 BiometricPrompt 的
 * CryptoObject 授权。没有指纹就无法把密文还原成主密码，指纹不再是纯 UI 门禁。
 *
 * 指纹变化（新录入或全部删除）会让 Keystore 作废该密钥，旧密文自动失效，
 * 需要用户重新开启一次指纹解锁。
 */
class BiometricStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // 1.3.x 及更早版本把主密码明文存在这两个文件里，升级后立即清掉。
        purgeLegacy(context)
    }

    val available: Boolean
        get() = runCatching { secretKey(create = true) != null }.getOrDefault(false)

    fun has(vaultId: String): Boolean =
        prefs.contains(keyCipher(vaultId)) && prefs.contains(keyIv(vaultId))

    fun clear(vaultId: String) {
        prefs.edit().remove(keyCipher(vaultId)).remove(keyIv(vaultId)).apply()
    }

    /** 开启指纹解锁：返回一个绑定到 Keystore 密钥、等待用户认证的加密 Cipher。 */
    fun prepareEncrypt(): Cipher? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(create = true) ?: return null)
        cipher
    }.getOrNull()

    /** 认证通过后把主密码加密落盘；失败时不留下半截数据。 */
    fun persist(vaultId: String, password: String, cipher: Cipher): Boolean = runCatching {
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(keyCipher(vaultId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(keyIv(vaultId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        true
    }.getOrDefault(false)

    /** 解锁前准备一个待授权的解密 Cipher；密钥被指纹变更作废时返回 null 并清除旧数据。 */
    fun prepareDecrypt(vaultId: String): Cipher? = runCatching {
        if (decode(keyCipher(vaultId)) == null || decode(keyIv(vaultId)) == null) return null
        val iv = decode(keyIv(vaultId)) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(create = false) ?: return null,
            GCMParameterSpec(TAG_BITS, iv)
        )
        cipher
    }.onFailure { if (it is KeyPermanentlyInvalidatedException) clear(vaultId) }.getOrNull()

    /** 认证通过后用同一个 Cipher 解密出主密码。 */
    fun decrypt(vaultId: String, cipher: Cipher): String? = runCatching {
        val encrypted = decode(keyCipher(vaultId)) ?: return null
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun decode(key: String): ByteArray? =
        prefs.getString(key, null)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    private fun secretKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (!create) return null

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Keystore 的“每次使用都要认证”只支持强生物识别（Class 3）；
            // 仅支持弱生物识别的设备会在 prepareEncrypt 处失败，由上层提示改用主密码。
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** 清掉旧版明文凭据（升级迁移，只跑一次且失败不影响启动）。 */
    private fun purgeLegacy(context: Context) {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "safeincloud_biometric",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).edit().clear().apply()
        }
        runCatching {
            context.getSharedPreferences("safeincloud_biometric_fallback", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    private fun keyCipher(vaultId: String) = "$KEY_CIPHER$vaultId"

    private fun keyIv(vaultId: String) = "$KEY_IV$vaultId"

    private companion object {
        const val PREFS_NAME = "safeincloud_biometric_v2"
        const val KEY_CIPHER = "cipher_"
        const val KEY_IV = "iv_"
        const val KEY_ALIAS = "safeincloud.biometric.v2"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
