plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":platform"))
    implementation(project(":core"))
    implementation(project(":common"))
}

application {
    mainClass = "vesper.app.MainKt"
}
