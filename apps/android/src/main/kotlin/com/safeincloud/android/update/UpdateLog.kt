package com.safeincloud.android.update

import android.content.Context

/**
 * 内置更新日志，格式与 GitHub 发布说明保持一致：
 *
 * ```
 * **2026/10/30**
 * - 新增 …
 * - 修复 …
 * ```
 *
 * 只写功能介绍与问题修复，不写包名、版本号、系统要求之类的发布信息。
 */
object UpdateLog {

    private const val ASSET_NAME = "updateLog.md"

    fun load(context: Context): List<ReleaseNote> {
        val text = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        return parse(text)
    }

    internal fun parse(text: String): List<ReleaseNote> {
        val notes = mutableListOf<ReleaseNote>()
        var date = ""
        val bullets = mutableListOf<String>()

        fun flush() {
            if (date.isNotBlank() && bullets.isNotEmpty()) {
                notes += ReleaseNote(
                    version = "",
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
                line.length in 4..24 && line.startsWith("**") && line.endsWith("**") -> {
                    flush()
                    date = line.trim('*').trim()
                }

                line.startsWith("- ") -> bullets += line.removePrefix("- ").trim()
            }
        }
        flush()
        return notes
    }
}
