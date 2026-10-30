package com.safeincloud.core.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * 稳健的原子写工具：先写临时文件再替换目标文件。
 *
 * 相比裸写，这里对常见的瞬时失败做了兜底：
 * - 系统服务（杀软 / 索引程序）可能短暂占用刚写好的 .tmp，导致基于 rename 的 [Files.move] 失败；
 *   此处对 move 做多次退避重试。
 * - ATOMIC_MOVE 不被支持或仍失败时，回退到普通 move。
 * - 全部失败时回退到 copy + delete（不依赖 rename），尽量保证数据落盘而不是丢数据。
 *
 * 注意：调用方应保证目标父目录可写。
 */
object AtomicFiles {
    fun writeAtomic(path: Path, bytes: ByteArray) {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return
            } catch (e: Exception) {
                last = e
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
                    return
                } catch (e2: Exception) {
                    last = e2
                }
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(30L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        // 兜底：copy + delete，不依赖 rename，规避被占用时 rename 必然失败的问题。
        try {
            Files.copy(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(tmp)
        } catch (e: Exception) {
            throw last ?: e
        }
    }
}
