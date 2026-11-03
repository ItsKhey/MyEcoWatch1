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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MyEcoWatch1"

// The application shell owns the manifest, launcher and dashboard host. The
// modules below it keep the data layer and each feature independently
// compilable. Project paths map to directories, so :app:core lives in app/core
// and :app:feature:auth lives in app/feature/auth.
include(":app")
include(":app:core")
include(":app:feature:auth")
include(":app:feature:reports")
include(":app:feature:profile")
include(":app:feature:alerts")
