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

    implementation(platform("com.google.cloud:libraries-bom:24.3.0"))
    implementation("com.google.cloud:google-cloud-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
}