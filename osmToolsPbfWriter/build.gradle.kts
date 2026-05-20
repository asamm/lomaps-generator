import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenCentral()
}

// 'bundled' configuration: dependencies that must be packaged INSIDE the shadow jar
// (with their crosby.binary references relocated). They go on the COMPILE classpath
// via compileOnly so OsmPbfWriter.kt can import crosby.binary.* at source level,
// but they are NOT on the runtime classpath and NOT exposed transitively to
// consumers — so their unrelocated copies cannot collide with planetiler-core's
// bundled crosby.binary.* on the consumer's runtime classpath.
val bundled: Configuration = configurations.create("bundled")
configurations.named("compileOnly") { extendsFrom(bundled) }

dependencies {
    // Bundled and relocated — see shadowJar config below.
    bundled("org.openstreetmap.osmosis:osmosis-pbf:0.49.2")
    bundled("org.openstreetmap.pbf:osmpbf:1.5.0")

    // Used by OsmPbfWriter internally (Node/Way/Relation/Tag etc.); no
    // crosby.binary classes, safe to leave unshaded.
    api("org.openstreetmap.osmosis:osmosis-core:0.49.2")

    // Exposed in OsmFeature's public API (JTS Geometry).
    api("org.locationtech.jts:jts-core:1.20.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Only bundle the conflicting jars; everything else stays as a normal
    // transitive dependency.
    configurations = listOf(bundled)
    // The actual fix: rewrite all crosby.binary.* references in the bundled
    // bytecode to a private package so they no longer collide with
    // planetiler-core's bundled crosby.binary.*.
    relocate("crosby.binary", "com.asamm.shadow.crosby.binary")
    // Do NOT bundle protobuf — osmpbf:1.5.0 transitively pulls in
    // protobuf-java:2.6.1 which conflicts with the newer protobuf version
    // that planetiler-core's internal crosby.binary expects at runtime.
    // The relocated classes will use whatever protobuf is on the classpath
    // (provided by planetiler's transitive deps).
    dependencies {
        exclude(dependency("com.google.protobuf:protobuf-java"))
    }
}

// Make the shadow jar the only artifact other modules consume.
tasks.jar { enabled = false }
configurations.runtimeElements.configure {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}
configurations.apiElements.configure {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}