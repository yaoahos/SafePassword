package com.safeincloud.android.update

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

/** 通过 GitHub Releases API 检查并下载新版本。 */
object GitHubUpdater {
    const val REPO = "yaoahos/SafePassword"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases?per_page=20"
    private const val USER_AGENT = "SafePassword-Android"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fetchReleases(): List<GitHubRelease> {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(
                    "GitHub 返回 HTTP $code" + if (detail.isBlank()) "" else "：${detail.take(160)}"
                )
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString<List<GitHubRelease>>(text)
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
                    date = release.publishedAt.take(10)
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

    fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("下载失败，HTTP $code")
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
            if (target.length() <= 0L) throw IllegalStateException("下载的文件为空")
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V").ifBlank { version.trim() }

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
