pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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
    }
}

rootProject.name = "BarberiaApp"

include(":app")
include(":core:common")
include(":core:mvi")
include(":core:firebase")
include(":core:designsystem")
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:catalog:domain")
include(":feature:catalog:data")
include(":feature:catalog:presentation")
include(":feature:booking:domain")
include(":feature:booking:data")
