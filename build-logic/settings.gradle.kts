// Settings for the `build-logic` included build: Gradle tooling, not a project
// module. It reads the root version catalog so a single catalog serves both
// builds, and uses the same repositories as the main build (OD-3).

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // The ktlint Gradle plugin is published on the Gradle Plugin Portal, not Maven Central
        // (`org.jlleitschuh.gradle:ktlint-gradle`). The repository is filtered to that one group,
        // so no other artifact can be resolved from the portal (SECURITY.md 9.1).
        gradlePluginPortal {
            content { includeGroup("org.jlleitschuh.gradle") }
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")
