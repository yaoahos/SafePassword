package com.safeincloud.desktop

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

/** 通过 GitHub Releases API 检查新版本（桌面版）。 */
class GitHubUpdater(private val engine: io.ktor.client.engine.HttpClientEngine) {
    companion object {
        const val REPO = "yaoahos/SafePassword"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases"
        private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases?per_page=10"
        private const val USER_AGENT = "SafePassword-Desktop"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(engine) {
        expectSuccess = false
    }

    suspend fun fetchReleases(): List<GitHubRelease> {
        val text = client.get(RELEASES_API) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", USER_AGENT)
        }.bodyAsText()
        return json.decodeFromString<List<GitHubRelease>>(text)
    }

    suspend fun latestRelease(): UpdateInfo? {
        val releases = fetchReleases()
        val release = releases.firstOrNull { !it.draft && !it.prerelease && it.tagName.isNotBlank() } ?: return null
        val version = normalize(release.tagName)
        return UpdateInfo(
            version = version,
            notes = release.body.trim(),
            publishedAt = release.publishedAt.take(10),
            pageUrl = release.htmlUrl.ifBlank { "$RELEASES_PAGE/tag/${release.tagName}" }
        )
    }

    suspend fun recentReleases(limit: Int = 5): List<ReleaseNote> =
        fetchReleases()
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

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun normalize(version: String): String =
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
