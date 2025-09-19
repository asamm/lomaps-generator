plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("org.openapi.generator") version "6.6.0"
}

group = "com.asamm.locus"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":osmToolsCore"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.14")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:0.8.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

//sourceSets.main {
//    kotlin.srcDir("${buildDir.path}/maptilerapi")
//}

tasks {
    openApiGenerate {
        generatorName.set("kotlin")
        inputSpec.set(file("src/main/resources/maptiler_openapi.yml").absolutePath)
        outputDir.set(layout.buildDirectory.dir("maptilerapi").get().asFile.absolutePath)
        ignoreFileOverride.set(file("src/main/resources/openapi-generator-ignore").absolutePath)
        apiPackage.set("com.asamm.locus.client.api")
        modelPackage.set("com.asamm.locus.client.model")
        additionalProperties.set(
            mapOf(
                "library" to "jvm-retrofit2",
                "serializationLibrary" to "kotlinx_serialization",
                "useCoroutines" to "true"
            )
        )
        // skip open api validation
        skipValidateSpec.set(true) // Skip OpenAPI specification validation
    }
}