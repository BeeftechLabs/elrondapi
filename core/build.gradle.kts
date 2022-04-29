val ktor_version: String by project
val ktor_client_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.beeftechlabs"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":googlestorage"))

    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("com.soywiz.korlibs.krypto:krypto:2.4.12")
    implementation("com.ionspin.kotlin:bignum:0.3.4")
    implementation("redis.clients:jedis:4.1.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:1.4.16")
}