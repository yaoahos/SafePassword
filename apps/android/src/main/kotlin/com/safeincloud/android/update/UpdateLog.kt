package com.safeincloud.android.update

import android.content.Context

/**
 * 内置更新日志，格式与 GitHub 发布说明保持一致（日期在前、版本号在后）：
 *
 * ```
 * **2026/10/31 · 1.3.0**
 * - 新增 …
 * - 修复 …
 * ```
 *
 * 只写功能介绍与问题修复，不写包名、系统要求之类的发布信息。
 */
object UpdateLog {

    private const val ASSET_NAME = "updateLog.md"

    /** `2026/10/31 · 1.3.0` / `2026-10-31 1.3.0` / `2026/10/31` 都能解析。 */
    private val HEADER = Regex("""^(\d{4}[-/]\d{1,2}[-/]\d{1,2})(?:\s*[·\-—]\s*|\s+)?(v?\d+(?:\.\d+)*)?$""")

    fun load(context: Context): List<ReleaseNote> {
        val text = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        return parse(text)
    }

    internal fun parse(text: String): List<ReleaseNote> {
        val notes = mutableListOf<ReleaseNote>()
        var date = ""
        var version = ""
        val bullets = mutableListOf<String>()

        fun flush() {
            if (date.isNotBlank() && bullets.isNotEmpty()) {
                notes += ReleaseNote(
                    version = version,
                    name = "",
                    notes = bullets.joinToString("\n"),
                    date = date
                )
            }
            bullets.clear()
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.length in 4..40 && line.startsWith("**") && line.endsWith("**") -> {
                    flush()
                    val body = line.trim('*').trim()
                    val match = HEADER.matchEntire(body)
                    if (match != null) {
                        date = match.groupValues[1].trim()
                        version = match.groupValues[2].trim().removePrefix("v")
                    } else {
                        date = body
                        version = ""
                    }
                }

                line.startsWith("- ") -> bullets += line.removePrefix("- ").trim()
            }
        }
        flush()
        return notes
    }
}
