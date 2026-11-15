pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                // KSP and Hilt are published on Maven Central / Gradle Plugin Portal, not Google Maven.
                excludeGroupByRegex("com\\.google\\.devtools\\.ksp.*")
                excludeGroupByRegex("com\\.google\\.dagger.*")
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
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                excludeGroupByRegex("com\\.google\\.devtools\\.ksp.*")
                excludeGroupByRegex("com\\.google\\.dagger.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Smart Track"
include(":app")
