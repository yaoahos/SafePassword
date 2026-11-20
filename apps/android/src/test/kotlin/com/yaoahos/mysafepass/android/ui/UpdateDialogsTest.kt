package com.yaoahos.mysafepass.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 更新弹窗对 GitHub Release 正文的解析：标题要丢掉，正常变更条目不能被误过滤。 */
class UpdateDialogsTest {
    private val body = """
        **2026/11/20 · 1.1.7**
        - 变更：统一应用标识与云端文件命名
        - 变更：本版包名与旧版不同，可与旧版同时安装；两版数据互不共享，旧版密码库需先导出再在本版导入
        - 变更：请勿让新旧两版同时开启自动同步并连接同一 WebDAV 目录，避免互相同步同一批文件产生覆盖或冲突
        - 优化：提升跨版本数据兼容性
    """.trimIndent()

    @Test
    fun `conciseNotes 保留包名共存提示并过滤版本标题`() {
        val notes = conciseNotes(body, limit = 6)
        assertEquals(4, notes.size)
        assertFalse(notes.any { it.contains("2026/11/20") })
        assertEquals(
            "变更：本版包名与旧版不同，可与旧版同时安装；两版数据互不共享，旧版密码库需先导出再在本版导入",
            notes[1]
        )
    }
}
