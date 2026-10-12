import com.vanniktech.maven.publish.Checksum
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

// LICENSE in META-INF of every published jar (jar, sources, javadoc). Jar tasks only, not every
// archive: the centralBundle zip below is an archive too, and a stray META-INF/LICENSE at the root
// of a Central Portal bundle is not part of the Maven repository layout the Portal expects.
// org.gradle.jvm.tasks.Jar, not the DSL's default `Jar` (its subclass): vanniktech's javadoc jar
// task extends only the former and would otherwise ship without the LICENSE.
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
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
//
// -PconformanceJar=<path to a gravix-token-java jar> mints with THAT jar instead of this build's
// classes, e.g. the one publishToMavenLocal wrote or one downloaded from Maven Central, so the
// server-verifier check covers the artifact users actually get. The generator prints the jar
// (code source) GravixToken was loaded from.
val conformanceJar = providers.gradleProperty("conformanceJar")
val conformanceTokens by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes build/conformance/java-tokens.txt (+ .manifest.jsonl) for gravix-token-go/conformance."
    val testRuntime = sourceSets.test.get().runtimeClasspath
    classpath = if (conformanceJar.isPresent) {
        val jar = file(conformanceJar.get())
        require(jar.isFile) { "conformanceJar: not a file: $jar" }
        // The jar first, and this build's own main classes removed, so nothing else can supply them.
        files(jar) + testRuntime.minus(sourceSets.main.get().output)
    } else {
        testRuntime
    }
    mainClass.set("com.gravixcloud.token.ConformanceTokens")
    args(layout.buildDirectory.dir("conformance").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}

// Signing. Maven Central refuses unsigned artifacts. The key, first match wins (blank = unset):
//   1. signing.gnupg.keyName: the gpg command (Gradle's useGpgCmd()) with the maintainer's own gpg
//      and gpg-agent, plus signing.gnupg.executable / useLegacyGpg / homeDir. No key and no
//      passphrase in any file; gpg-agent must already hold the passphrase (README, Releasing).
//   2. signingInMemoryKey (+ signingInMemoryKeyId, signingInMemoryKeyPassword): an ASCII-armored
//      key held in memory, e.g. ORG_GRADLE_PROJECT_* environment variables in CI.
//   3. signing.keyId (+ signing.password, signing.secretKeyRingFile): the signing plugin's default.
fun nonBlankProperty(name: String): String? = providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
val signingSource: String? = when {
    nonBlankProperty("signing.gnupg.keyName") != null -> "gpg command (signing.gnupg.keyName)"
    nonBlankProperty("signingInMemoryKey") != null -> "in-memory key (signingInMemoryKey)"
    nonBlankProperty("signing.keyId") != null -> "keyring file (signing.keyId)"
    else -> null
}

// Signing runs only in builds that ship to Central or build its bundle, i.e. when one of these
// tasks is named on the command line. vanniktech 0.37.0's signAllPublications() makes signing
// REQUIRED for every publish of a non-SNAPSHOT version, publishToMavenLocal included; with the
// maintainer's signing.gnupg.keyName in ~/.gradle/gradle.properties every local publish would
// otherwise call gpg and ask for the release key's passphrase.
fun isReleaseTask(path: String): Boolean = path.substringAfterLast(':').let {
    it == "publishToMavenCentral" || it == "publishAndReleaseToMavenCentral" || it == "centralBundle" ||
        it.endsWith("ToMavenCentralRepository") || it.endsWith("ToLocalCentralBundleRepository")
}
val releaseRequested = gradle.startParameter.taskNames.any(::isReleaseTask)

// The guard: anything that would ship to Central stops BEFORE vanniktech's
// prepareMavenCentralPublishing runs (that task registers the deployment the plugin zips and
// uploads at the end of the build) unless signing is on. Without it, a missing key, or a release
// reached through another task name (e.g. `publish`, or an abbreviation), would still upload an
// unsigned bundle, which the Portal then fails in validation.
val checkReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless release signing is on. Maven Central uploads and centralBundle depend on it."
    val source = signingSource
    val requested = releaseRequested
    doLast {
        if (source == null) {
            throw GradleException(
                "No signing key configured. Set signing.gnupg.keyName (gpg + gpg-agent) or " +
                    "signingInMemoryKey / signingInMemoryKeyId / signingInMemoryKeyPassword in " +
                    "~/.gradle/gradle.properties or as ORG_GRADLE_PROJECT_* environment variables. " +
                    "See README, Releasing.",
            )
        }
        if (!requested) {
            throw GradleException(
                "Release signing is switched on only when publishToMavenCentral, " +
                    "publishAndReleaseToMavenCentral or centralBundle is named on the command line. " +
                    "Run the release by that full task name.",
            )
        }
        logger.lifecycle("Release signing: $source")
    }
}
tasks.matching { it.name == "prepareMavenCentralPublishing" }.configureEach { dependsOn(checkReleaseSigning) }
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToMavenCentralRepository") || name.endsWith("ToLocalCentralBundleRepository")) {
        dependsOn(checkReleaseSigning)
    }
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
    coordinates("com.gravixcloud", "gravix-token-java", version.toString())

    // Maven Central (Central Portal). `publishToMavenCentral` uploads, then the release waits for a
    // manual "Publish" in the Portal (automaticRelease=false); nothing in a normal build runs it.
    // Credentials: the Portal user token as the Gradle properties mavenCentralUsername /
    // mavenCentralPassword (vanniktech's names), kept in ~/.gradle/gradle.properties, never here.
    publishToMavenCentral()
    // md5 + sha1 are what Central requires; sha256 + sha512 are accepted and cost nothing. The
    // centralBundle task at the end of this file builds the same file set locally.
    checksums(Checksum.MD5, Checksum.SHA1, Checksum.SHA256, Checksum.SHA512)

    // See signingSource / releaseRequested / checkReleaseSigning above. signAllPublications() wires
    // the in-memory key itself when signingInMemoryKey is set; useGpgCmd() after it takes over
    // when signing.gnupg.keyName is set.
    if (releaseRequested && signingSource != null) {
        signAllPublications()
        if (nonBlankProperty("signing.gnupg.keyName") != null) {
            extensions.getByType<SigningExtension>().useGpgCmd()
        }
    }

    pom {
        name.set("Gravix Token for Java")
        description.set(
            "Mint Gravix Cloud join tokens in your own Java or Kotlin backend: HS256 JWTs the " +
                "Gravix server verifies, no token server on the join path. Zero runtime dependencies, Java 8+.",
        )
        inceptionYear.set("2026")
        url.set("https://gravixcloud.com")
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
                organizationUrl.set("https://gravixcloud.com")
            }
        }
        // The public repository the owner creates before the first release (README, Releasing).
        scm {
            url.set("https://github.com/assajeeb/gravix-token-java")
            connection.set("scm:git:https://github.com/assajeeb/gravix-token-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/assajeeb/gravix-token-java.git")
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/assajeeb/gravix-token-java/issues")
        }
    }
}

// Local stand-in for the Central upload: `./gradlew centralBundle` (needs a signing key) publishes
// into build/central-bundle/repo and zips it to build/central-bundle/<artifact>-<version>-bundle.zip
// with the file set vanniktech 0.37.0 uploads: no maven-metadata, md5/sha1/sha256/sha512 of each
// artifact, .asc signatures without checksums of their own (its excludeSignatureChecksums default).
// Nothing here touches the network. The zip can be inspected before a release, or uploaded by hand
// in the Portal ("Publish Component") instead of running publishToMavenCentral. The repository is
// deliberately NOT named "...MavenCentral...": vanniktech hooks its upload into every publish task
// whose name ends in "ToMavenCentralRepository".
val centralBundleDir = layout.buildDirectory.dir("central-bundle")
publishing {
    repositories {
        maven {
            name = "localCentralBundle"
            url = uri(centralBundleDir.map { it.dir("repo") })
        }
    }
}
val cleanCentralBundle by tasks.registering(Delete::class) {
    delete(centralBundleDir)
}
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToLocalCentralBundleRepository")) {
        dependsOn(cleanCentralBundle)
    }
}
val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Builds the signed Maven Central upload bundle locally (no upload)."
    dependsOn("publishAllPublicationsToLocalCentralBundleRepository")
    from(centralBundleDir.map { it.dir("repo") }) {
        exclude("**/maven-metadata*")
        exclude("**/*.asc.md5", "**/*.asc.sha1", "**/*.asc.sha256", "**/*.asc.sha512")
    }
    destinationDirectory.set(centralBundleDir)
    archiveFileName.set("gravix-token-java-$version-bundle.zip")
}
