# SafePassword

SafePassword 是一个离线优先的密码管理器。当前提供 Android 客户端，使用 Kotlin + Jetpack Compose 实现；共享核心为纯 Kotlin/JVM。数据默认只保存在本机，通过主密码派生密钥后加密存储；需要多设备同步时，可选用任意兼容 WebDAV 的网盘。

- 应用名：`SafePassword`
- 项目名：`SafePassword`
- 包名：`com.safeincloud`
- 正式版本：`1.1.0`（versionCode 2）
- 最低系统：Android 8.0（API 26）
- 目标系统：Android 15（API 35）
- 正式 APK：`dist/SafePassword-1.1.0-release.apk`
- APK 大小：14,189,863 字节
- SHA-256：`C98799A2A5AB380B997153147EAC6B15B23579BB62D795BA50FBA2E7A1A46585`
- 发布下载：[Releases · v1.1.0](https://github.com/yaoahos/SafePassword/releases/tag/v1.1.0)

> 本项目是独立实现，与 SafeInCloud 官方无关联，也未使用其源代码。界面与交互仅作为设计参考；SafeInCloud 名称及相关权益归其权利人所有。

---

## 一、软件介绍

SafePassword 面向希望把密码数据掌握在自己手里的用户。它不依赖厂商云，不要求注册账号，也不包含密码填充、共享协作或广告分析。密码库文件在离开设备前已经完成加密，WebDAV 只负责传输密文。

设计目标：

1. **本地优先**：没有网络也能完整使用。
2. **可迁移**：密码库、备份和导出文件可以自由复制。
3. **可审计**：核心加密与同步逻辑位于独立的纯 Kotlin 模块，便于测试和复用。
4. **克制**：不申请无关权限，不接入统计 SDK，不做后台常驻。
5. **跨尺寸可用**：手机、平板和横屏共用一套 Compose UI。

---

## 二、已实现功能

### 密码库

- 创建多个密码库，每个密码库使用独立主密码。
- 主密码解锁、指纹解锁、自动锁定。
- 密码库重命名、切换、删除。
- 本地加密备份与恢复。
- 首次启动时支持创建密码库；仅在不存在密码库时显示“从本地恢复”和“从云端恢复”。
- 从 WebDAV 下载密码库到本地，用于新设备或重装后的恢复。

### 条目管理

- 新建、编辑、查看、复制、删除条目。
- 字段支持上移、下移、重命名、修改类型、删除。
- 账号、密码、网址、备注等常用字段。
- 自定义字段类型：文本、密码、网址、邮箱、电话、数字、日期、多行文本等。
- 密码字段独立显示/隐藏，点击哪个密码的眼睛只影响对应字段。
- 密码强度提示。
- 条目收藏，收藏后立即刷新显示。
- 回收站：软删除、恢复、永久删除、清空。
- 批量选择：长按进入选择模式，支持全选、批量删除、批量设置标签。
- 条目详情字段之间使用主题色分隔线；首栏不重复加顶部分隔线，末栏保留底部分隔线。

### 标签

- 新建、编辑、删除标签。
- 标签颜色选择，标签以彩色旗标显示。
- 标签排序值：数字越小越靠前，并同步影响密码库顶部的标签顺序。
- 标签管理支持长按多选、全选和批量删除。
- 条目可按标签筛选；一个条目可属于多个标签。

### 搜索与排序

- 密码库顶部常驻搜索框。
- 可搜索标题、账号、网址；可在设置中选择是否搜索密码内容。
- 搜索框支持清空与显式搜索按钮。
- 搜索框获得焦点后，侧滑/返回优先清空搜索，不直接退出密码库。
- 排序支持标题、最近修改等模式。
- 可选择默认展示“全部”或指定标签。

### 图标

- 条目支持上传自定义图片作为图标。
- 上传图片会压缩为正方形，最大 256×256。
- 自定义图标按需异步解码，带内存缓存，滑动列表时避免主线程阻塞。
- 未上传图标时，使用标题首字符和稳定的随机颜色生成占位图标。
- XML 导入支持原版 `custom_icon`、`image` 和 `symbol` 三种图标来源。

### 密码生成器

- 随机密码与口令短语两种模式。
- 可调整长度、大小写、数字、符号、排除易混淆字符。
- 生成结果可复制。

### 安全检查

- 密码强度、重复使用、长期未修改等统计。
- 可选的 Have I Been Pwned 泄露查询。
- HIBP 查询使用 k-匿名：只发送 SHA-1 哈希前 5 位，不发送完整密码或完整哈希。
- 安全检查不包含邮箱泄露查询。

### WebDAV 同步

- 支持任意标准 WebDAV 服务。
- 配置服务器地址、用户名、密码、远程目录。
- 测试连接、拉取云端数据、保存并同步。
- 同步文件统一为 `SafePassword.spv`，远程目录默认 `SafePassword`。
- 多设备使用同一个密码库名称和同步文件，避免因本地库 ID 不同而互相找不到。
- 默认本地优先：本地修改后同步会覆盖云端版本；需要以云端为准时，使用“拉取云端数据（覆盖本地）”。
- 同步状态短暂显示后自动隐藏，不长期占用顶栏。

### 导入与导出

- 导入 CSV、JSON 和原版 SafeInCloud XML。
- 支持一次选择多个文件，逐个导入并汇总成功/失败结果。
- 导出 CSV、JSON 和 SafeInCloud XML。
- 导出的 XML 包含标签、字段、备注和自定义图标，可用于迁移或备份。
- 默认导出文件名为 `SafePassword.xml`、`SafePassword.csv`、`SafePassword.json`。
- 本地加密密码库文件扩展名为 `.sicv`，云端同步文件扩展名为 `.spv`。

### 外观与体验

- 默认主题色与启动图标一致的蓝色。
- 预置主题色与自定义颜色；主题色影响按钮、选中态、标签和分隔线。
- 主题预置色为圆形色块。
- 密码库、工具、详情、编辑、标签管理等页面统一顶栏结构。
- 页面切换使用淡入淡出并保留页面状态。
- 平板/横屏使用限宽内容区和多列网格。
- 可配置是否允许截屏。
- 密码库条目卡固定高度，标签名称在固定高度内完整展示。

---

## 三、技术选型

| 层次 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin 2.1.0 | Android 与共享核心均使用 Kotlin |
| UI | Jetpack Compose + Material 3 | 声明式 UI，支持手机/平板 |
| 构建 | Gradle 8.11.1 + AGP 8.7.3 | JDK 17 字节码，构建环境使用 JDK 21 |
| 共享核心 | 纯 Kotlin/JVM `:core` | 加密、模型、导入导出、WebDAV、同步 |
| 网络 | Ktor Client 3.0.3 + OkHttp | WebDAV 与 HIBP 请求 |
| 序列化 | kotlinx.serialization 1.7.3 | JSON 密码库与配置 |
| 协程 | kotlinx.coroutines 1.9.0 | IO 与 UI 状态切换 |
| 设置存储 | AndroidX Security Crypto | `EncryptedSharedPreferences` 保存敏感配置 |
| 生物识别 | AndroidX Biometric 1.1.0 | 指纹解锁，调用系统能力 |
| 图标 | Material Icons Extended | 界面图标 |
| 最低版本 | API 26 | Android 8.0 |
| 目标版本 | API 35 | Android 15 |

核心模块不依赖 Android，只承载与平台无关的加密、模型与同步逻辑，便于单元测试与复用。Android 层负责 Compose UI、文件选择器、生物识别和系统设置跳转。

---

## 四、安全设计

### 1. 密码库加密

- 主密码通过 **PBKDF2-HMAC-SHA256** 派生密钥，迭代次数 **210,000**。
- 使用 **AES-256-GCM** 加密密码库内容。
- 每次加密生成随机 12 字节 IV，GCM 认证标签 128 位。
- 密码库内容使用数据密钥（DEK）加密，DEK 再由主密码派生的密钥包裹，便于后续更换主密码而无需重加密全部数据。
- 主密码不写入磁盘，也不上传到任何服务器。
- 密码库文件、备份文件在离开设备前均为密文。

### 2. 本地配置

- WebDAV 地址、用户名、密码等敏感配置保存在 `EncryptedSharedPreferences` 中。
- 加密密钥由 Android Keystore 的 AES-256-GCM 主密钥保护。
- 普通偏好设置（主题、排序、默认标签等）与敏感配置分离。

### 3. 生物识别与自动锁定

- 使用 AndroidX BiometricPrompt 调用系统指纹能力，不接触指纹原始数据。
- 指纹开关的开启和关闭都需要系统生物识别确认。
- 支持自动锁定；应用退到后台后按设置锁定。
- 生物识别失败时保留主密码解锁入口。

### 4. 网络与隐私

- 仅在用户主动配置 WebDAV 或启用 HIBP 查询时访问网络。
- 不包含统计、广告、崩溃上报 SDK。
- HIBP 使用 SHA-1 前缀查询，服务端无法从请求中直接得到完整密码。
- 密码库同步传输的是密文文件；WebDAV 服务商只能看到密文和文件大小。
- 截屏默认允许，可在设置中关闭；关闭后系统会阻止截屏与最近任务预览。

### 5. 发布签名

- 正式 APK 使用单独的 release keystore 签名。
- `release.keystore` 与 `keystore.properties` 已加入 `.gitignore`，不应提交到版本库。
- 一旦丢失 release keystore，已安装用户将无法覆盖升级，请离线备份。

> 安全边界：主密码遗忘后无法恢复；请同时保留加密备份。任何密码管理器都不能防御已 root、被植入木马或被实时录屏的设备。

---

## 五、项目构建

### 1. 环境要求

- JDK 17 或更高（本项目使用 JDK 21 构建）。
- Android SDK Platform 35、Build-Tools 35.0.0（仅构建 Android 版需要）。
- Gradle 使用项目自带的 Wrapper（8.11.1），无需单独安装。
- Windows、macOS、Linux 均可构建 Android 版。

### 2. 配置 SDK 路径

在项目根目录创建 `local.properties`：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

也可以直接使用 Android Studio 打开项目，由 IDE 生成。

### 3. 调试构建

```bat
gradlew.bat :core:test :apps:android:assembleDebug
```

产物：

```text
apps/android/build/outputs/apk/debug/android-debug.apk
```

### 4. 正式构建

正式构建需要 release keystore。项目根目录应存在：

```text
release.keystore
keystore.properties
```

`keystore.properties` 示例：

```properties
storeFile=release.keystore
storePassword=你的_store_password
keyAlias=safepassword
keyPassword=你的_key_password
```

生成新的 keystore：

```bat
keytool -genkeypair -v -keystore release.keystore -alias safepassword -keyalg RSA -keysize 2048 -validity 10000
```

构建：

```bat
gradlew.bat :core:test :apps:android:assembleRelease
```

产物：

```text
apps/android/build/outputs/apk/release/android-release.apk
```

重命名并复制到 `dist/`：

```bat
copy apps\android\build\outputs\apk\release\android-release.apk dist\SafePassword-1.1.0-release.apk
```

### 5. 校验 APK

```bat
apksigner.bat verify --verbose dist\SafePassword-1.1.0-release.apk
aapt.exe dump badging dist\SafePassword-1.1.0-release.apk
```

当前正式包的校验结果：

- `package: com.safeincloud`
- `versionCode: 2`
- `versionName: 1.1.0`
- `application-label: SafePassword`
- APK Signature Scheme v2：通过
- 权限：`INTERNET`、`USE_BIOMETRIC`、`USE_FINGERPRINT`、`POST_NOTIFICATIONS`、`REQUEST_INSTALL_PACKAGES`

### 6. 构建环境变量（可选）

如果使用脚本构建，可显式指定：

```bat
set JAVA_HOME=C:\path\to\jdk21
set ANDROID_HOME=C:\path\to\android-sdk
set ANDROID_USER_HOME=C:\path\to\android-user-home
set GRADLE_USER_HOME=C:\path\to\gradle-home
set TMP=C:\path\to\tmp
set TEMP=C:\path\to\tmp
set JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/path/to/tmp -Duser.home=C:/path/to/android-user-home
```

### 7. 测试

- `:core:test` 覆盖加密、密码库容器、WebDAV 客户端、XML 导入导出、工具函数等。
- 当前核心测试 12/12 通过。
- 当前环境无法启动 Android 模拟器（Windows 命名管道限制），因此未做模拟器 UI 冒烟；发布前建议在真机走查一次。

---

## 六、使用说明

### 1. 首次启动

1. 打开应用，进入欢迎页。
2. 点击“创建密码库”，输入主密码并确认。
3. 主密码用于派生加密密钥，不会上传；遗忘后无法找回。
4. 创建完成后进入密码库列表。

只有在**不存在任何密码库**时，欢迎页才显示：

- 创建密码库
- 从本地恢复
- 从云端恢复

已有密码库时，欢迎页只显示解锁入口，避免误操作覆盖现有数据。

### 2. 从本地恢复

1. 在欢迎页点击“从本地恢复”。
2. 选择 `.sicv` 或导出的密码库文件。
3. 输入该文件的主密码。
4. 可修改恢复后的密码库名称。
5. 恢复完成后进入密码库。

### 3. 从云端恢复

1. 在欢迎页点击“从云端恢复”。
2. 填写 WebDAV 地址、用户名、密码、远程目录。
3. 点击“保存并立即同步”。
4. 应用先提示输入主密码。
5. 验证通过后下载 `SafePassword.spv` 并导入本地。
6. 成功后进入密码库；失败会保留配置并提示原因。

### 4. 日常解锁

- 主密码解锁：输入创建密码库时设置的主密码。
- 指纹解锁：在设置中开启指纹，按系统提示验证。
- 自动锁定：按设置的时间在后台自动锁定。
- 指纹开关的开启与关闭都会再次验证指纹。

### 5. 条目操作

- 点击条目进入详情。
- 点击右下角橙色按钮新建条目。
- 详情页右下角按钮进入编辑。
- 长按条目进入多选，可批量删除或批量设置标签。
- 条目星标点击后立即生效，不需要二次刷新。
- 删除的条目进入侧边栏“回收站”，可恢复或永久删除。
- 条目详情中的字段支持独立显示/隐藏密码。

### 6. 标签

- 侧边栏 → 标签管理。
- 点击右下角“+”新建标签，设置名称、颜色和排序值。
- 排序值越小，标签越靠前；密码库顶部标签顺序与之一致。
- 长按标签进入多选，可批量删除。
- 条目编辑页可多选标签；标签以主题色 FilterChip 显示。

### 7. 搜索

- 密码库顶部搜索框输入标题、账号或网址。
- 设置中可开启“搜索密码”；关闭时占位提示不会暗示搜索密码。
- 搜索框聚焦后，侧滑/返回会先清空搜索，不会退出页面。
- 搜索框右侧提供清空和搜索按钮。

### 8. 主题与外观

- 工具 → 主题。
- 预置色为圆形色块，默认第一个是图标蓝。
- 支持输入 `#RRGGBB` 自定义颜色。
- 主题色会应用到按钮、选中态、标签、分隔线等。
- 可切换深色模式（跟随系统）。

### 9. WebDAV 同步

1. 工具 → WebDAV 同步。
2. 填写服务器地址，例如 `https://dav.example.com/remote.php/dav/files/用户名/`。
3. 填写用户名、密码。
4. 远程目录默认 `SafePassword`。
5. 点击“测试连接”。
6. 点击“保存并立即同步”。
7. 多设备使用同一个远程目录和 `SafePassword.spv`。

同步策略：

- 本地修改后同步：默认本地优先，覆盖云端。
- 需要云端覆盖本地：使用“拉取云端数据（覆盖本地）”。
- 首次同步会创建 `SafePassword.spv`。
- 同步文件为密文，WebDAV 服务商无法读取内容。

### 10. 备份与恢复

- 工具 → 本地备份。
- 支持手动创建加密备份。
- 可设置自动备份间隔（默认每天）。
- 恢复备份会要求输入该备份的主密码。
- 建议同时保留本地备份和云端密文副本。

### 11. 导入

- 工具 → 导入密码。
- 支持 CSV、JSON、SafeInCloud XML。
- 可一次选择多个文件，应用会逐个导入并汇总结果。
- XML 导入支持标签、字段、备注、`custom_icon`、`image`、`symbol`。
- 导入前建议先创建备份。

### 12. 导出

- 设置 → 数据。
- 支持导出 CSV、JSON、SafeInCloud XML。
- 默认文件名分别为 `SafePassword.csv`、`SafePassword.json`、`SafePassword.xml`。
- 导出的 XML 可用于迁移到其他支持该格式的密码管理器。

### 13. 平板/横屏

- 宽屏下内容区限宽，避免文字行长过大。
- 密码库和工具页使用多列布局。
- 顶栏、抽屉、详情和编辑页保持同一套结构。
- 旋转屏幕后页面状态会尽量保留。

---

## 七、项目结构

项目采用"平台应用 + 共享核心"的结构。`core` 是纯 Kotlin/JVM 共享核心，Android 应用层直接复用它的加密、模型、导入导出和 WebDAV 能力，UI 与平台粘合层在 `apps/android` 内实现。

```text
SafePassword/
├── apps/
│   └── android/                Android 应用层（Compose UI、Activity、文件选择器）
│       ├── src/main/kotlin/com/safeincloud/android/
│       │   ├── ui/             页面、组件、主题、导航
│       │   ├── data/           设置、备份、同步调度
│       │   ├── security/       生物识别
│       │   └── MainActivity.kt
│       └── src/main/res/       图标、字符串、主题资源
├── core/                       纯 Kotlin/JVM 共享核心
│   └── src/main/kotlin/com/safeincloud/core/
│       ├── crypto/             AES-GCM、PBKDF2、密钥包裹
│       ├── model/              密码库、条目、标签、字段
│       ├── vault/              密码库容器、加解密、导入导出
│       ├── sync/               WebDAV、同步引擎、冲突策略
│       └── security/           密码强度、HIBP
├── gradle/                     Gradle Wrapper
├── dist/                       正式 APK
├── release.keystore            正式签名（本地，不提交）
├── keystore.properties         签名配置（本地，不提交）
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

Gradle 模块路径：

- `:core`：共享核心
- `:apps:android`：Android 客户端

包名说明：

- Android `applicationId`：`com.safeincloud`
- Android 命名空间：`com.safeincloud.android`
- 核心库包名：`com.safeincloud.core`

---

## 八、兼容性与限制

- 仅支持 Android 8.0 及以上。
- 仅支持 WebDAV 同步，不接入专有云 API。
- 不包含密码填充/自动填充。
- 不包含共享协作、团队空间。
- 不包含 2FA/TOTP。
- 不包含附件管理。
- 主密码遗忘后无法恢复；请务必保留备份。
- XML 导入以标准条目、字段、标签、备注和图标为主；原版附件文件不会导入。
- 云端冲突默认本地优先；如需以云端为准，请使用“拉取云端数据（覆盖本地）”。
- 当前构建环境无法启动 Android 模拟器，正式发布前建议在真机完成一次 UI 与同步走查。

---

## 九、开源组件与致谢

本项目使用或依赖以下开源项目，感谢其作者与社区：

- [Kotlin](https://kotlinlang.org/) — Apache-2.0
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Apache-2.0
- [AndroidX](https://developer.android.com/jetpack/androidx) — Apache-2.0
- [Material Components for Android](https://github.com/material-components/material-components-android) — Apache-2.0
- [Ktor](https://ktor.io/) — Apache-2.0
- [OkHttp](https://square.github.io/okhttp/) — Apache-2.0
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — Apache-2.0
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — Apache-2.0
- [AndroidX Biometric](https://developer.android.com/jetpack/androidx/releases/biometric) — Apache-2.0
- [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) — Apache-2.0
- [Have I Been Pwned](https://haveibeenpwned.com/) — 泄露查询 API

界面设计参考 SafeInCloud 的公开截图，但代码、数据结构和实现均为独立完成。SafeInCloud 是其权利人的商标/产品名称，本项目与官方无隶属或合作关系。

---

## 十、版本记录

**2026/10/30**
- 新增 检查更新与自更新，可在通知栏查看下载进度，下载完成后直接安装
- 新增 「关于」页与侧边栏软件名处的检查更新入口，更新日志随包内置、离线可看
- 新增 秘密字段类型，与密码一样默认隐藏，但不参与弱密码、重复密码与泄露检测
- 优化 页面切换动画：标签页淡入缩放，层级页面左右滑动，返回时方向相反
- 优化 同步状态提示 3 秒后自动消失，常驻显示上次同步时间
- 修复 从安全中心进入条目编辑后，保存会跳回首页的问题

**2026/10/29**
- 新增 密码库创建、恢复与解锁，支持主密码和指纹两种方式
- 新增 条目、标签、自定义字段、图标、搜索、收藏、回收站与批量操作
- 新增 WebDAV 云端同步，支持保存后自动同步与冲突合并
- 新增 CSV、JSON 与 SafeInCloud XML 导入导出
- 新增 密码生成器、安全检查与泄露检测
- 新增 主题配色、自动备份，以及手机、平板、横屏适配
