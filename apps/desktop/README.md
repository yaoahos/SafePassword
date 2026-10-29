# SafePassword Desktop（Windows，预留模块）

当前状态：**设计占位，尚未加入 Gradle 构建**。

计划定位：Windows 桌面客户端，使用 Kotlin/JVM + Compose Desktop，直接复用 `:core` 的加密、模型、WebDAV 同步和导入导出能力。

## 计划模块

- Gradle 模块：`:apps:desktop`
- 包名：`com.safeincloud.desktop`
- 共享核心：`implementation(project(":core"))`
- 构建入口：`apps/desktop/build.gradle.kts`
- 启动入口：`apps/desktop/src/main/kotlin/com/safeincloud/desktop/Main.kt`

## 设计边界

桌面端不应重新实现以下逻辑：

- 密码库文件格式与加解密
- 主密码派生、DEK 包裹
- 条目/标签/字段模型
- WebDAV 连接与同步
- CSV/JSON/SafeInCloud XML 导入导出
- 密码强度与 HIBP 查询

桌面端只实现：

- 窗口、菜单、系统托盘/快捷键
- Windows 文件选择器与本地路径
- Windows 凭据存储（如 DPAPI/Windows Credential Manager）
- 桌面布局与键盘操作
- 安装包/更新机制

## 接入步骤

实现桌面端时，在根目录 `settings.gradle.kts` 中增加：

```kotlin
include(":apps:desktop")
```

然后在 `apps/desktop/build.gradle.kts` 中应用 Kotlin JVM 与 Compose Desktop 插件，并依赖 `:core`。不要在 Android 模块中新增桌面专用代码；跨平台差异应留在各 `apps/*` 模块内。
