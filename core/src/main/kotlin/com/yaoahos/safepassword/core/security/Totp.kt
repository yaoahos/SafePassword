package com.yaoahos.safepassword.core.security

import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP 一次性密码，用于充当其它网站/应用（例如 GitHub 双因素登录）的认证器。
 *
 * [Field.value][com.yaoahos.safepassword.core.model.Field] 可存两种来源：
 * 1. 网站给出的 `otpauth://totp/...?...` 链接（含密钥与参数）；
 * 2. 裸 Base32 密钥（网站生成的“密钥文件码”，容忍空格/小写/缺 padding）。
 */
object Totp {

    /** 解析后的 TOTP 参数。[secret] 为 Base32 解码后的原始字节。 */
    data class Params(
        val secret: ByteArray,
        val period: Int = 30,
        val digits: Int = 6,
        val algorithm: String = "SHA1",
        val issuer: String = "",
        val label: String = ""
    ) {
        override fun equals(other: Any?): Boolean =
            other is Params && secret.contentEquals(other.secret) &&
                period == other.period && digits == other.digits &&
                algorithm == other.algorithm && issuer == other.issuer && label == other.label

        override fun hashCode(): Int = secret.contentHashCode()
    }

    /**
     * 解析 `otpauth://` URI 或裸 Base32 密钥；无法识别时返回 null。
     * 裸密钥容忍空格、连字符、小写与缺失的 padding。
     */
    fun parse(input: String): Params? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("otpauth://", ignoreCase = true)) return parseUri(text)
        val secret = decodeBase32(text) ?: return null
        return Params(secret = secret)
    }

    /** 生成 [atMillis] 时刻的 TOTP 码；参数无法解析时返回 null。 */
    fun currentCode(input: String, atMillis: Long = System.currentTimeMillis()): String? {
        val params = parse(input) ?: return null
        val counter = atMillis / 1000L / params.period
        return generate(params.secret, counter, params.digits, params.algorithm)
    }

    /** 距离当前周期结束还剩几秒（1..[period]）。 */
    fun remainingSeconds(atMillis: Long = System.currentTimeMillis(), period: Int = 30): Int {
        if (period <= 0) return 0
        val elapsed = ((atMillis / 1000L) % period).toInt()
        return period - elapsed
    }

    /** RFC 4226 HOTP：HMAC 截断取模。[counter] 为周期序号（时间/period）。 */
    fun generate(
        secret: ByteArray,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String? {
        if (secret.isEmpty() || digits !in 4..10) return null
        val hmac = hmac(algorithm, secret, counter.toBigEndianBytes()) ?: return null
        val offset = hmac[hmac.size - 1].toInt() and 0x0F
        val binary =
            ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)
        val code = binary % IntMath.pow10(digits)
        return code.toString().padStart(digits, '0')
    }

    private fun parseUri(text: String): Params? {
        val uri = runCatching { java.net.URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("otpauth", ignoreCase = true)) return null
        if (!uri.host.equals("totp", ignoreCase = true)) return null
        val label = uri.rawPath?.trimStart('/')?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
        val query = uri.rawQuery ?: ""
        val args = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8").lowercase(Locale.US)
            val value = java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
        val secret = decodeBase32(args["secret"] ?: return null) ?: return null
        val digits = args["digits"]?.toIntOrNull() ?: 6
        val period = args["period"]?.toIntOrNull() ?: 30
        val algorithm = normalizeAlgorithm(args["algorithm"] ?: "SHA1") ?: return null
        if (period <= 0) return null
        // issuer 可能在 query 里，也可能在 label 的 "Issuer:账号" 前缀里。
        val issuer = args["issuer"]?.takeIf { it.isNotBlank() }
            ?: label.substringBefore(':', missingDelimiterValue = "").trim()
        return Params(
            secret = secret,
            period = period,
            digits = digits,
            algorithm = algorithm,
            issuer = issuer,
            label = label
        )
    }

    private fun normalizeAlgorithm(raw: String): String? {
        val name = raw.uppercase(Locale.US).replace("-", "").replace("_", "")
        return when (name) {
            "SHA1", "SHA" -> "SHA1"
            "SHA256" -> "SHA256"
            "SHA512" -> "SHA512"
            else -> null
        }
    }

    private fun hmac(algorithm: String, key: ByteArray, message: ByteArray): ByteArray? {
        val jceName = when (algorithm) {
            "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> return null
        }
        return runCatching {
            val mac = Mac.getInstance(jceName)
            mac.init(SecretKeySpec(key, jceName))
            mac.doFinal(message)
        }.getOrNull()
    }

    private fun Long.toBigEndianBytes(): ByteArray {
        val bytes = ByteArray(8)
        var value = this
        for (i in 7 downTo 0) {
            bytes[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return bytes
    }

    /** RFC 4648 Base32 解码，容忍空格/连字符/小写/缺 padding；非法输入返回 null。 */
    internal fun decodeBase32(input: String): ByteArray? {
        val cleaned = buildString {
            for (c in input) {
                when {
                    c.isWhitespace() || c == '-' || c == '=' -> Unit
                    else -> append(c.uppercaseChar())
                }
            }
        }
        if (cleaned.isEmpty()) return null
        var bits = 0
        var buffer = 0
        val out = ArrayList<Byte>(cleaned.length * 5 / 8 + 1)
        for (c in cleaned) {
            val value = when (c) {
                in 'A'..'Z' -> c - 'A'
                in '2'..'7' -> c - '2' + 26
                else -> return null
            }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        // 余下不足 8 bit 的部分必须为 0（RFC 4648 填充位）。
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }

    /** 小工具：10^n，避开浮点。 */
    private object IntMath {
        fun pow10(n: Int): Int {
            var result = 1
            repeat(n) { result *= 10 }
            return result
        }
    }
}
