package com.safeincloud.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class WebDavConfig(
    var baseUrl: String = "",
    var username: String = "",
    var password: String = "",
    var remoteDir: String = "SafePassword",
    var enabled: Boolean = false
) {
    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/') + "/"

    fun normalizedDir(): String = remoteDir.trim().trim('/').ifBlank { "SafePassword" }

    fun remotePath(vaultKey: String): String = "${normalizedDir()}/SafePassword.spv"

    fun attachmentsDir(vaultKey: String): String = "${normalizedDir()}/attachments/$vaultKey"

    /**
     * 稳定的云端键：只依赖密码库名称，因此多个安卓设备会读写同一个文件。
     * 旧版本使用本地 vaultId，跨设备永远对不上；升级后统一改用名称键。
     */
    fun vaultKey(name: String): String {
        val key = name.trim().lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
        return key.take(64).ifBlank { "vault" }
    }

    fun isConfigured(): Boolean =
        baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
}

class WebDavException(message: String, val status: Int? = null) : Exception(message)

/** 远端文件的轻量元数据，用于判断“云端是否被别的设备改过”，避免每次都全量下载。 */
data class RemoteStat(
    val lastModified: Long,
    val size: Long,
    val etag: String?
)

class WebDavClient(
    private val config: WebDavConfig,
    engine: HttpClientEngine
) {
    /**
     * 只对用户配置的主机附带 Basic 凭据。
     * 之前是无条件 `sendWithoutRequest { true }`，一旦服务器 3xx 跳到别的主机，
     * ktor 会把用户名密码重新附加过去，等于把凭据送给第三方。
     */
    private val authHost: String = runCatching { Url(config.baseUrl).host }.getOrDefault("")

    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = true
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(config.username, config.password)
                }
                sendWithoutRequest { request ->
                    authHost.isNotBlank() && request.url.host.equals(authHost, ignoreCase = true)
                }
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun test() {
        val response = client.request(url("")) {
            method = HttpMethod("PROPFIND")
            header("Depth", "0")
            contentType(ContentType.Application.Xml)
            setBody(PROPFIND_BODY)
        }
        when (response.status.value) {
            in 200..299 -> Unit
            401, 403 -> throw WebDavException("认证失败，请检查用户名和密码", response.status.value)
            404 -> throw WebDavException("服务器地址或远程目录不存在", 404)
            else -> throw WebDavException(
                "连接失败（HTTP ${response.status.value}）：${response.bodyAsText().take(120)}",
                response.status.value
            )
        }
    }

    suspend fun get(path: String): ByteArray? {
        var lastStatus = 0
        repeat(2) { attempt ->
            val response = client.request(url(path)) {
                method = HttpMethod.Get
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.UserAgent, "SafePassword-Android/1.4")
            }
            lastStatus = response.status.value
            when {
                response.status == HttpStatusCode.NotFound -> return null
                response.status == HttpStatusCode.Conflict -> {
                    // Some WebDAV servers (notably 坚果云) answer GET for a not-yet-created
                    // vault with 409 instead of 404. Distinguish that from a real path
                    // conflict (usually a same-named collection) with a cheap HEAD probe.
                    val exists = runCatching { exists(path) }.getOrDefault(false)
                    if (!exists) return null
                    throw WebDavException(
                        "下载失败（HTTP 409，路径 $path）：远端路径存在冲突，通常是同名目录。" +
                            "请在 WebDAV 中删除该目录，或更换远程目录后重试",
                        409
                    )
                }
                response.status.value in 200..299 -> {
                    val bytes = response.bodyAsBytes()
                    if (bytes.isEmpty()) {
                        throw WebDavException("下载失败：服务器返回了空文件（$path）", lastStatus)
                    }
                    return bytes
                }
                response.status.value in 500..599 && attempt == 0 -> delay(600)
                response.status.value == 401 || response.status.value == 403 ->
                    throw WebDavException("WebDAV 认证失败，请检查用户名、密码或应用专用密码", lastStatus)
                else -> {
                    val location = response.headers[HttpHeaders.Location]
                    val suffix = if (location.isNullOrBlank()) "" else "，重定向到 $location"
                    throw WebDavException(
                        "下载失败（HTTP $lastStatus$suffix，路径 $path）：${response.bodyAsText().take(120)}",
                        lastStatus
                    )
                }
            }
        }
        throw WebDavException("下载失败（HTTP $lastStatus，路径 $path），服务器暂时不可用，请稍后重试", lastStatus)
    }

    suspend fun exists(path: String): Boolean {
        val response = client.request(url(path)) { method = HttpMethod.Head }
        return response.status.value in 200..299
    }

    /**
     * 读取远端文件的 Last-Modified / Content-Length / ETag。
     * 服务器未返回这些头时对应字段为 0 / -1 / null，调用方应把 lastModified 为 0 视为“无法判断是否变化”。
     */
    suspend fun stat(path: String): RemoteStat? {
        val response = client.request(url(path)) { method = HttpMethod.Head }
        if (response.status.value !in 200..299) return null
        val lastModified = response.headers[HttpHeaders.LastModified]?.let(::parseHttpDate) ?: 0L
        val size = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        return RemoteStat(lastModified, size, response.headers[HttpHeaders.ETag])
    }

    private fun parseHttpDate(value: String): Long = runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrElse {
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }

    suspend fun put(path: String, bytes: ByteArray) {
        ensureParent(path)
        val response = client.request(url(path)) {
            method = HttpMethod.Put
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        if (response.status.value !in 200..299) {
            throw WebDavException(
                "上传失败（HTTP ${response.status.value}）：${response.bodyAsText().take(120)}",
                response.status.value
            )
        }
    }

    suspend fun delete(path: String) {
        val response = client.request(url(path)) { method = HttpMethod.Delete }
        if (response.status.value !in 200..299 && response.status != HttpStatusCode.NotFound) {
            throw WebDavException("删除失败（HTTP ${response.status.value}）", response.status.value)
        }
    }

    suspend fun ensureParent(path: String) {
        val segments = path.split('/').filter { it.isNotBlank() }.dropLast(1)
        var current = ""
        for (segment in segments) {
            current = if (current.isBlank()) segment else "$current/$segment"
            mkcol(current)
        }
    }

    private suspend fun mkcol(path: String) {
        val response = client.request(url(path)) { method = HttpMethod("MKCOL") }
        when (response.status.value) {
            in 200..299 -> Unit
            405 -> Unit
            401, 403 -> throw WebDavException("没有创建目录的权限", response.status.value)
            else -> throw WebDavException(
                "创建目录失败（HTTP ${response.status.value}）：${response.bodyAsText().take(120)}",
                response.status.value
            )
        }
    }

    fun close() {
        client.close()
    }

    private fun url(path: String): String {
        val builder = URLBuilder(config.normalizedBaseUrl())
        path.split('/').filter { it.isNotBlank() }.forEach { builder.appendPathSegments(it) }
        return builder.buildString()
    }

    companion object {
        private const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><D:propfind xmlns:D=\"DAV:\"><D:prop><D:resourcetype/></D:prop></D:propfind>"
    }
}
