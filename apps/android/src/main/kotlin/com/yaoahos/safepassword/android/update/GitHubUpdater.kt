package com.yaoahos.safepassword.android.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

/**
 * 检查更新的失败原因。
 *
 * 与桌面端 `UpdateFailure` 一一对应：每种原因都有自己的提示语，
 * 不要再用「检查更新失败，请检查网络后重试」把限流、404、服务端故障混成一句。
 */
sealed class UpdateException(message: String) : Exception(message)

/** GitHub 限流（未登录每 IP 每小时 60 次）。[resetEpoch] 是可重试的 Unix 秒，拿不到时为空。 */
class RateLimitedException(val resetEpoch: Long? = null) : UpdateException("更新检查已被限制")

/** 仓库里还没有发布过正式版本（HTTP 404）。 */
class NoReleaseException : UpdateException("还没有可更新的版本")

/** 更新服务返回了 4xx/5xx（除限流与 404 外）。 */
class UpdateUnavailableException(val status: Int) : UpdateException("更新服务暂时不可用：HTTP $status")

/** 返回内容不是预期的 JSON，多半被代理或认证页改写了。 */
class BadPayloadException : UpdateException("更新服务返回内容异常")

/** 下载安装包失败（地址不合法、被劫持、内容不像 APK）。消息可直接展示给用户。 */
class UpdateDownloadException(message: String) : UpdateException(message)

/** 通过 GitHub Releases API 检查并下载新版本。 */
object GitHubUpdater {
    const val REPO = "yaoahos/SafePassword"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    /** 只取最新一版，用于检查更新；比拉整页列表省流量，也少烧限流配额。 */
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    /** 更新日志要列多版，才需要列表接口。 */
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases?per_page=50"

    private const val USER_AGENT = "SafePassword-Android"

    /** 安装包至少这么大；被劫持成错误页时不可能达到。 */
    private const val MIN_APK_BYTES = 512L * 1024L

    /** 只允许从 GitHub 的域名取安装包。 */
    private val ALLOWED_HOSTS = listOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 拉取最新版本（单条）。 */
    fun fetchLatest(attempts: Int = 3): GitHubRelease {
        val text = fetchWithRetry(LATEST_API, attempts)
        return runCatching { json.decodeFromString<GitHubRelease>(text) }
            .getOrElse { throw BadPayloadException() }
    }

    /** 拉取发布列表（更新日志用）。 */
    fun fetchReleases(attempts: Int = 3): List<GitHubRelease> {
        val text = fetchWithRetry(RELEASES_API, attempts)
        return runCatching { json.decodeFromString<List<GitHubRelease>>(text) }
            .getOrElse { throw BadPayloadException() }
    }

    /**
     * 网络抖动重试两三次就够；限流、404、4xx/5xx、内容异常都不重试——
     * 重试不会变好，只会多烧一份配额。
     */
    private fun fetchWithRetry(url: String, attempts: Int): String {
        var lastError: Exception? = null
        for (attempt in 1..attempts) {
            try {
                return requestBody(url)
            } catch (e: UpdateException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts) {
                    // 700ms → 1400ms，给网络栈一点时间把 DNS/连接准备好。
                    Thread.sleep(700L * attempt)
                }
            }
        }
        throw lastError ?: IllegalStateException("检查更新失败")
    }

    private fun requestBody(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code == 404) {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw NoReleaseException()
            }
            if (code == 403 || code == 429) {
                val reset = connection.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull()
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw RateLimitedException(reset)
            }
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                android.util.Log.w("GitHubUpdater", "更新服务返回 HTTP $code：${detail.take(200)}")
                throw UpdateUnavailableException(code)
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun releaseNotes(releases: List<GitHubRelease>, limit: Int = 10): List<ReleaseNote> =
        releases
            .filter { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
            .take(limit)
            .map { release ->
                val version = normalize(release.tagName)
                ReleaseNote(
                    version = version,
                    name = release.name.ifBlank { "SafePassword $version" },
                    notes = release.body.trim(),
                    date = release.publishedAt.take(10).replace('-', '/')
                )
            }

    fun toUpdateInfo(release: GitHubRelease): UpdateInfo {
        val version = normalize(release.tagName)
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return UpdateInfo(
            version = version,
            tag = release.tagName,
            name = release.name.ifBlank { "SafePassword $version" },
            notes = release.body.trim(),
            apkUrl = apk?.downloadUrl.orEmpty(),
            pageUrl = release.htmlUrl.ifBlank { "$RELEASES_PAGE/tag/${release.tagName}" },
            size = apk?.size ?: 0L
        )
    }

    /**
     * 下载安装包。
     *
     * 三道校验（与桌面端一致）：地址必须 https 且落在 GitHub 域名下、
     * 内容必须是 ZIP（APK 就是 zip，头两字节 "PK"）、体积不能小得离谱。
     * 少了这些，被劫持成一张错误页也会被当安装包交给系统。
     */
    fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        if (!url.startsWith("https://")) throw UpdateDownloadException("安装包地址无效")
        val host = url.removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (ALLOWED_HOSTS.none { host.equals(it, ignoreCase = true) }) {
            throw UpdateDownloadException("安装包地址无效")
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateDownloadException("下载失败，HTTP $code")
            val total = connection.contentLengthLong
            target.parentFile?.mkdirs()
            var downloaded = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (target.length() < MIN_APK_BYTES || !looksLikeZip(target)) {
                target.delete()
                throw UpdateDownloadException("安装包内容异常，已取消下载")
            }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    private fun looksLikeZip(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(2)
            input.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V").ifBlank { version.trim() }

    /**
     * 下载完成后核对安装包：包名必须一致，签名证书必须与当前应用相同。
     *
     * 系统安装时也会校验签名，但那一步的报错是「应用未安装」这种无信息量的结果；
     * 这里提前拦下，能明确告诉用户包不对。签名不一致通常意味着资产被替换过。
     *
     * @return 校验失败的原因；通过时返回 null。
     */
    fun verifyDownloadedApk(context: Context, file: File): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            }
        }.getOrNull() ?: return "下载的安装包无法解析，请重试"

        if (archive.packageName != context.packageName) {
            return "下载的安装包不是本应用，已丢弃"
        }

        val current = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
        }.getOrNull() ?: return "无法读取本应用签名，已丢弃该安装包"

        val mine = current.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            ?: return "无法读取本应用签名，已丢弃该安装包"
        val theirs = archive.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            ?: return "无法读取下载包的签名，已丢弃"

        return if (mine.contentEquals(theirs)) null else "下载的安装包签名与本应用不一致，已丢弃"
    }

    private fun compare(a: String, b: String): Int {
        val left = numbers(a)
        val right = numbers(b)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun numbers(version: String): List<Int> =
        normalize(version)
            .split('.', '-', '+', ' ')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}
