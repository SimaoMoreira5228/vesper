plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

sourceSets {
    test {
        resources {
            srcDir("src/test/resources")
        }
    }
}

tasks.register<Exec>("clonePspAutotests") {
    description = "Clone pspautotests repo for test resources"
    val dest = file("$rootDir/build/pspautotests")
    commandLine("git", "clone", "--depth", "1",
        "https://github.com/hrydgard/pspautotests.git", dest.absolutePath)
    enabled = !dest.resolve(".git").exists()
}

tasks.register<Copy>("copyPspAutotests") {
    description = "Copy pspautotests PRX and expected files for use in tests"
    dependsOn("clonePspAutotests")
    duplicatesStrategy = DuplicatesStrategy.WARN
    from("$rootDir/build/pspautotests/tests")
    include("**/*.prx", "**/*.expected")
    into("$projectDir/src/test/resources/pspautotests/tests")
    eachFile {
        path = path.replace("/prx/", "/")
    }
    includeEmptyDirs = false
}

tasks.named("processTestResources") {
    dependsOn("copyPspAutotests")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runPspAutotest") {
    description = "Run one PSP PRX without the full test suite"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("vesper.core.loader.PspAutotestStandaloneKt")
    val prx = providers.gradleProperty("prx")
    val expected = providers.gradleProperty("expected")
    val trace = providers.gradleProperty("trace")
    val maxInstructions = providers.gradleProperty("maxInstructions")
    args = buildList {
        prx.orNull?.let { add(rootProject.file(it).absolutePath) }
        expected.orNull?.let { add(rootProject.file(it).absolutePath) }
        trace.orNull?.let { add(it) }
        maxInstructions.orNull?.let { add(it) }
    }
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
