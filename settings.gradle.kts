pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gravix-token-java"

// Test-only: proves the library reads well from Kotlin. Not published; the library itself
// never applies the Kotlin plugin, so no Kotlin runtime reaches its POM.
include(":kotlin-usage")
