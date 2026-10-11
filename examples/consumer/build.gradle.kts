plugins {
    application
}

dependencies {
    implementation("com.gravixcloud:gravix-token-java:0.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

application {
    mainClass.set("example.Main")
}
