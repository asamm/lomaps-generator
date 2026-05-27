plugins {
    kotlin("jvm")
}

group = "com.asamm.osmTools"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.locationtech.jts:jts-core:1.20.0")
}

tasks.test {
    useJUnitPlatform()
}
