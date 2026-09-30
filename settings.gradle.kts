// Multiverse Explorer — Gradle settings (TASK-014).
// Repositories and the root project name are declared here; the module set is
// included by the module-declaration commits that follow.

pluginManagement {
    // The convention plugins are Gradle tooling, not a project module: the
    // included build does not change the ADR-0001 module set (OD-3).
    includeBuild("build-logic")

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
    }
}

rootProject.name = "multiverse-explorer"

// The module set of ADR-0001 as amended by ADR-0010, in the order of its table.
include(
    ":core:domain",
    ":core:data",
    ":core:presentation",
    ":core:designsystem",
    ":core:testing",
    ":feature:discovery",
    ":feature:character-detail",
    ":feature:favorites",
    ":feature:episodes",
    ":feature:settings",
)
