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

tasks.test {
    useJUnitPlatform()
}
