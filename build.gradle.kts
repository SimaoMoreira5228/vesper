plugins {
    kotlin("jvm") version "2.4.10" apply false
}

allprojects {
    group = "vesper"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

tasks.register("testAll") {
    dependsOn(":tests:test")
    description = "Run all tests"
    group = "verification"
}

tasks.register("testCore") {
    dependsOn(":core:test")
    description = "Run core module tests"
    group = "verification"
}
