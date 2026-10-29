package com.safeincloud.core.security

import kotlin.math.ln
import kotlin.math.pow

data class StrengthResult(
    val score: Int,
    val label: String,
    val entropyBits: Double,
    val crackTime: String
)

object PasswordStrength {
    private val commonPasswords = setOf(
        "123456", "123456789", "qwerty", "password", "111111", "12345678", "abc123",
        "1234567", "password1", "12345", "1234567890", "iloveyou", "000000", "123123",
        "admin", "letmein", "welcome", "monkey", "dragon", "sunshine", "princess",
        "football", "baseball", "master", "shadow", "superman", "qazwsx", "666666",
        "888888", "woaini", "5201314", "a123456", "123qwe", "zxcvbnm", "asdfgh",
        "qwertyuiop", "1qaz2wsx", "passw0rd", "p@ssw0rd", "root", "guest", "test"
    )

    fun evaluate(password: String): StrengthResult {
        if (password.isBlank()) {
            return StrengthResult(0, "很弱", 0.0, "瞬间")
        }
        if (password.lowercase() in commonPasswords) {
            return StrengthResult(0, "很弱", 0.0, "瞬间")
        }

        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 33
        if (pool == 0) pool = 1

        var entropy = password.length * (ln(pool.toDouble()) / ln(2.0))
        if (password.length < 6) entropy *= 0.6
        if (password.length >= 8 && password.all { it.isDigit() }) entropy *= 0.55
        if (hasRun(password, 4)) entropy *= 0.75
        if (hasSequence(password, 4)) entropy *= 0.85

        val score = when {
            entropy < 28 -> 0
            entropy < 36 -> 1
            entropy < 60 -> 2
            entropy < 80 -> 3
            else -> 4
        }
        val label = when (score) {
            0 -> "很弱"
            1 -> "弱"
            2 -> "一般"
            3 -> "强"
            else -> "很强"
        }
        return StrengthResult(score, label, entropy, crackTime(entropy))
    }

    private fun hasRun(text: String, length: Int): Boolean {
        var run = 1
        for (i in 1 until text.length) {
            run = if (text[i] == text[i - 1]) run + 1 else 1
            if (run >= length) return true
        }
        return false
    }

    private fun hasSequence(text: String, length: Int): Boolean {
        if (text.length < length) return false
        var ascending = 1
        var descending = 1
        for (i in 1 until text.length) {
            val delta = text[i].code - text[i - 1].code
            ascending = if (delta == 1) ascending + 1 else 1
            descending = if (delta == -1) descending + 1 else 1
            if (ascending >= length || descending >= length) return true
        }
        return false
    }

    private fun crackTime(entropyBits: Double): String {
        val guesses = 2.0.pow(entropyBits - 1.0)
        val seconds = guesses / 1.0e10
        return when {
            seconds < 1 -> "瞬间"
            seconds < 60 -> "${seconds.toLong()} 秒"
            seconds < 3600 -> "${(seconds / 60).toLong()} 分钟"
            seconds < 86_400 -> "${(seconds / 3600).toLong()} 小时"
            seconds < 31_536_000 -> "${(seconds / 86_400).toLong()} 天"
            seconds < 31_536_000.0 * 1000 -> "${(seconds / 31_536_000).toLong()} 年"
            else -> "超过 1000 年"
        }
    }
}
