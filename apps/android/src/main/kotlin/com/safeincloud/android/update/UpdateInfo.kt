package com.safeincloud.android.update

/** 一个可用的新版本。 */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
    val size: Long
)

/** 更新日志中的一条发布记录。 */
data class ReleaseNote(
    val version: String,
    val name: String,
    val notes: String,
    val date: String
)
