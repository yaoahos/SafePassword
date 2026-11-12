package com.yaoahos.safepassword.core

import com.yaoahos.safepassword.core.security.Totp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RFC 6238 / RFC 4226 官方测试向量。 */
class TotpTest {

    // RFC 6238 Appendix B 的种子（ASCII），时间戳单位：秒。
    private val sha1Seed = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val sha256Seed = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
    private val sha512Seed =
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)

    @Test
    fun rfc6238_sha1_vectors() {
        val cases = mapOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )
        for ((time, expected) in cases) {
            val counter = time / 30
            assertEquals("T=$time", expected, Totp.generate(sha1Seed, counter, digits = 8, algorithm = "SHA1"))
        }
    }

    @Test
    fun rfc6238_sha256_vector() {
        assertEquals("46119246", Totp.generate(sha256Seed, 59L / 30, digits = 8, algorithm = "SHA256"))
    }

    @Test
    fun rfc6238_sha512_vector() {
        assertEquals("90693936", Totp.generate(sha512Seed, 59L / 30, digits = 8, algorithm = "SHA512"))
    }

    @Test
    fun rfc4226_hotp_six_digit_vector() {
        // RFC 4226 附录 D：counter=1 → 287082（与 TOTP 在 T=59 的 6 位截断一致）。
        assertEquals("287082", Totp.generate(sha1Seed, 1L))
        assertEquals("755224", Totp.generate(sha1Seed, 0L))
    }

    @Test
    fun currentCode_from_raw_base32() {
        // "12345678901234567890" 的 Base32 是 GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ。
        val code = Totp.currentCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", atMillis = 59_000L)
        assertEquals("287082", code)
    }

    @Test
    fun base32_is_lenient_about_spaces_case_and_padding() {
        val strict = Totp.decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val loose = Totp.decodeBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")
        assertArrayEquals(strict, loose)
        // 缺 padding 也能解。
        assertArrayEquals(strict, Totp.decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ==="))
    }

    @Test
    fun base32_rejects_illegal_input() {
        assertNull(Totp.decodeBase32(""))
        assertNull(Totp.decodeBase32("1ABC")) // '1' 不在 Base32 字母表
        assertNull(Totp.decodeBase32("ABC!"))
    }

    @Test
    fun parse_otpauth_uri() {
        val params = Totp.parse(
            "otpauth://totp/Example:alice@gitHub.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                "&issuer=Example&digits=6&period=30&algorithm=SHA1"
        )
        requireNotNull(params)
        assertEquals(30, params.period)
        assertEquals(6, params.digits)
        assertEquals("SHA1", params.algorithm)
        assertEquals("Example", params.issuer)
        assertArrayEquals(sha1Seed, params.secret)
    }

    @Test
    fun parse_otpauth_uri_defaults() {
        val params = Totp.parse("otpauth://totp/GitHub:me?secret=gezdgnbvgy3tqojqgezdgnbvgy3tqojq")
        requireNotNull(params)
        assertEquals(30, params.period)
        assertEquals(6, params.digits)
        assertEquals("SHA1", params.algorithm)
        assertEquals("GitHub", params.issuer)
    }

    @Test
    fun parse_rejects_non_totp_and_garbage() {
        assertNull(Totp.parse("otpauth://hotp/Example:me?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1"))
        assertNull(Totp.parse("https://example.com"))
        assertNull(Totp.parse(""))
        assertNull(Totp.parse("not base32 !!"))
    }

    @Test
    fun remaining_seconds_counts_down_within_period() {
        // T=59s 在 30s 周期里（59 % 30 = 29），剩 1 秒。
        assertEquals(1, Totp.remainingSeconds(59_000L, period = 30))
        // T=30s 恰好是新周期起点，剩一整轮。
        assertEquals(30, Totp.remainingSeconds(30_000L, period = 30))
        assertEquals(21, Totp.remainingSeconds(39_000L, period = 30))
    }
}
