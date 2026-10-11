// Kotlin usage tests for the Java library. Test-only and never published: the Kotlin
// plugin and kotlin-stdlib stay in this subproject.
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":"))
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
