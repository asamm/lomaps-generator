plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.asamm.osmTools"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":osmToolsCore"))
    implementation(project(":pmtiles"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // WebP ImageIO plugin — provides read/write via javax.imageio SPI (native libwebp)
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}