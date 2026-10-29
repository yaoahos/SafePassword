plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.safeincloud"
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
