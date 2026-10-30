import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("io.ktor:ktor-client-java:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.safeincloud.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SafePassword"
            packageVersion = "1.1.0"
            description = "SafePassword Password Manager"
            vendor = "SafePassword"

            // ktor-client-java（JDK HttpClient 引擎）依赖 java.net.http 模块，
            // 默认 jlink 镜像不包含它，需显式声明否则运行时 NoClassDefFoundError。
            modules("java.net.http")

            windows {
                menuGroup = "SafePassword"
                upgradeUuid = "8f1b6c2e-4d5a-4a7b-9c3e-1f2a3b4c5d6e"
            }
        }
    }
}
