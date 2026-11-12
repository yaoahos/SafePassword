plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.yaoahos.safepassword"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("io.ktor:ktor-client-core:3.0.3")
    api("io.ktor:ktor-client-auth:3.0.3")
    // 扫码解动态验证码密钥：zxing 是纯 Java，放在 core 里就能用真实合成帧做单测。
    api("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
