package com.safeincloud.core.security

import com.safeincloud.core.crypto.Crypto
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.Locale

class HibpException(message: String, cause: Throwable? = null) : Exception(message, cause)

class HibpClient(engine: HttpClientEngine) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    /**
     * k-anonymity 查询：只发送 SHA-1 前 5 位，密码本身不离开设备。
     */
    suspend fun passwordCount(password: String): Int {
        if (password.isBlank()) return 0
        val hash = Crypto.toHex(Crypto.sha1(password.toByteArray(Charsets.UTF_8))).uppercase(Locale.US)
        val prefix = hash.take(5)
        val suffix = hash.drop(5)
        val response = client.get("https://api.pwnedpasswords.com/range/$prefix") {
            header("Add-Padding", "true")
        }
        if (response.status.value != 200) {
            throw HibpException("泄露库查询失败（HTTP ${response.status.value}）")
        }
        return response.bodyAsText().lineSequence().mapNotNull { line ->
            val parts = line.trim().split(':')
            if (parts.size == 2 && parts[0].equals(suffix, ignoreCase = true)) {
                parts[1].trim().toIntOrNull()
            } else {
                null
            }
        }.firstOrNull() ?: 0
    }

    /**
     * 邮箱泄露检查需要 HIBP API Key（官方付费接口）。
     */
    suspend fun emailBreaches(email: String, apiKey: String): List<String> {
        if (email.isBlank()) return emptyList()
        if (apiKey.isBlank()) throw HibpException("邮箱泄露检查需要填写 HIBP API Key")
        val encoded = URLEncoder.encode(email, "UTF-8")
        val response = client.get("https://haveibeenpwned.com/api/v3/breachedaccount/$encoded?truncateResponse=true") {
            header("hibp-api-key", apiKey)
            header("user-agent", "SafeInCloudClient-Android")
        }
        return when (response.status.value) {
            200 -> {
                val text = response.bodyAsText()
                runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                        element.jsonObject["Name"]?.jsonPrimitive?.content
                    }
                }.getOrElse { throw HibpException("无法解析 HIBP 响应") }
            }
            404 -> emptyList()
            401 -> throw HibpException("HIBP API Key 无效")
            429 -> throw HibpException("HIBP 请求过于频繁，请稍后再试")
            else -> throw HibpException("邮箱查询失败（HTTP ${response.status.value}）")
        }
    }

    fun close() {
        client.close()
    }
}
