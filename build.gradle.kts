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

val compileShaders by tasks.registering {
    description = "Compile GLSL shaders to SPIR-V using glslangValidator"
    val shadersDir = file("platform/src/main/resources/shaders")
    inputs.dir(shadersDir)
    outputs.files(fileTree(shadersDir) { include("*.spv") })
    doLast {
        fileTree(shadersDir) { include("*.vert", "*.frag") }.forEach { src ->
            val dst = file("${src.absolutePath}.spv")
            val proc = ProcessBuilder("glslangValidator", "-V", src.name, "-o", "${src.name}.spv")
                .directory(shadersDir)
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                if (!dst.exists()) {
                    throw GradleException("glslangValidator failed for ${src.name}")
                }
                logger.warn("glslangValidator failed for ${src.name}, using existing .spv")
            }
        }
    }
}
