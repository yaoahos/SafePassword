package com.safeincloud.desktop.data

import com.safeincloud.core.json.AppJson
import com.safeincloud.core.sync.WebDavConfig
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * 桌面端数据目录：优先 %APPDATA%\SafePassword，依次回退 ~/.safeincloud、~/SafePassword，
 * 最后兜底到系统临时目录。任一候选位置创建成功即使用该位置；全部失败时也不会抛异常，
 * 仅让后续读写操作按需报错，避免应用启动即崩溃。
 */
object DesktopPaths {
    val root: Path = pickRoot()

    val backups: Path get() = root.resolve("backups")
    val settingsFile: Path get() = root.resolve("settings.json")

    private fun pickRoot(): Path {
        val candidates = buildList {
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?.let { add(Path.of(it, "SafePassword")) }
            val home = System.getProperty("user.home")
            if (!home.isNullOrBlank()) {
                add(Path.of(home, ".safeincloud"))
                add(Path.of(home, "SafePassword"))
            }
            add(Path.of(System.getProperty("java.io.tmpdir"), "SafePassword"))
        }
        for (candidate in candidates) {
            try {
                Files.createDirectories(candidate.resolve("backups"))
                return candidate
            } catch (_: Exception) {
                // 当前候选位置不可写，继续尝试下一个
            }
        }
        return candidates.first()
    }
}

@Serializable
data class DesktopSettings(
    val webDav: WebDavConfig = WebDavConfig(),
    val lastVaultId: String = "",
    /** 搜索时是否匹配密码/秘密字段的值。 */
    val searchPasswords: Boolean = true,
    /** 保存后自动同步到 WebDAV（仅在已启用时生效）。 */
    val autoSync: Boolean = true
)

object SettingsStore {
    fun load(): DesktopSettings {
        val file = DesktopPaths.settingsFile
        if (!Files.exists(file)) return DesktopSettings()
        return try {
            AppJson.instance.decodeFromString(
                DesktopSettings.serializer(),
                String(Files.readAllBytes(file), Charsets.UTF_8)
            )
        } catch (_: Exception) {
            DesktopSettings()
        }
    }

    fun save(settings: DesktopSettings) {
        val file = DesktopPaths.settingsFile
        val tmp = file.resolveSibling("settings.json.tmp")
        Files.write(
            tmp,
            AppJson.instance.encodeToString(DesktopSettings.serializer(), settings).toByteArray(Charsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
