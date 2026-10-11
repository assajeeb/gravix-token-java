import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
    // Only for the test-only :kotlin-usage subproject; not applied to the library.
    kotlin("jvm") version "2.4.10" apply false
}

group = "com.gravixcloud"
version = "0.1.0"

dependencies {
    // ZERO runtime dependencies: everything below is test scope.
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Decodes payloads in tests independently of the library's own JSON writer.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
}

// Java 8 bytecode and API (widest backend support), built with any JDK >= 17.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-options", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
    }
}

// LICENSE in META-INF of every published archive (jar, sources, javadoc).
tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.gravixcloud.token",
            "Implementation-Title" to "gravix-token-java",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// Writes Java-minted tokens for the Go conformance test (GRAVIX_FOREIGN_TOKENS, app APPconf,
// dummy certificate) plus a manifest of the grants each one must carry. See README.
val conformanceTokens by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes build/conformance/java-tokens.txt (+ .manifest.jsonl) for gravix-token-go/conformance."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.gravixcloud.token.ConformanceTokens")
    args(layout.buildDirectory.dir("conformance").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
    coordinates("com.gravixcloud", "gravix-token-java", version.toString())

    // Maven Central (Central Portal) is configured so a release is one command away, but nothing
    // here runs it: `publishToMavenLocal` never uploads. Releases are manual (automaticRelease=false).
    publishToMavenCentral()

    // Sign only when a key is configured (ORG_GRADLE_PROJECT_signingInMemoryKey, or the Gradle
    // signing plugin's signing.keyId / signing.gnupg.keyName properties), so local builds and
    // publishToMavenLocal work without one.
    val signingConfigured = listOf("signingInMemoryKey", "signing.keyId", "signing.gnupg.keyName")
        .any { providers.gradleProperty(it).isPresent }
    if (signingConfigured) {
        signAllPublications()
    }

    pom {
        name.set("Gravix Token for Java")
        description.set(
            "Mint Gravix Cloud join tokens in your own Java or Kotlin backend: HS256 JWTs the " +
                "Gravix server verifies, no token server on the join path. Zero runtime dependencies, Java 8+.",
        )
        inceptionYear.set("2026")
        url.set("https://www.gravixcloud.com/docs")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("gravitycompile")
                name.set("Gravity Compile")
                organization.set("Gravity Compile")
                organizationUrl.set("https://www.gravixcloud.com")
            }
        }
        // TODO(before the first Maven Central release): point scm at the public source repository.
        // These are placeholders on the reserved .invalid domain, not a real location.
        scm {
            url.set("https://TODO-set-before-release.invalid/gravix-token-java")
            connection.set("scm:git:https://TODO-set-before-release.invalid/gravix-token-java.git")
            developerConnection.set("scm:git:https://TODO-set-before-release.invalid/gravix-token-java.git")
        }
    }
}
