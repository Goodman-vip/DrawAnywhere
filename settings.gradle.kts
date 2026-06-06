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

rootProject.name = "DrawAnywhere"
include(":app")

plugins {
    id("com.autonomousapps.build-health") version "3.14.1"
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
    id("com.android.application") version "9.2.1" apply false
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
