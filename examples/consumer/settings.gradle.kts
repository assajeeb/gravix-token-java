// A separate build (not part of the library's build): consumes gravix-token-java the way an
// application would, from mavenLocal() ONLY. Resolving with no other repository also proves the
// artifact has no transitive dependencies.
//
//   ./gradlew publishToMavenLocal                  # in the library root
//   ./gradlew -p examples/consumer run             # this project, same wrapper
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
    }
}

rootProject.name = "gravix-token-consumer"
