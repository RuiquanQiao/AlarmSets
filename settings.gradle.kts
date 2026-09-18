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
        google()
        mavenCentral()
    }
}

rootProject.name = "AlarmSets"

// ---------------------------------------------------------------------------
// Module layout.
//
// `core:model` and `core:domain` are plain Kotlin (JVM) modules with no Android
// dependency whatsoever. That boundary is the whole point of the layout: every
// platform-specific concern reaches the core through an interface declared in
// `core:domain`, so porting to another platform means writing new
// implementations, not touching the core.
// ---------------------------------------------------------------------------
include(":app")

include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:alarm")
include(":core:audio")
include(":core:designsystem")
include(":core:update")
