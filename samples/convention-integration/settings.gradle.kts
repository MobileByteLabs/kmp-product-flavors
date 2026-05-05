/*
 * Copyright 2026 MobileByteLabs
 *
 * Sample project demonstrating convention plugin integration with kmp-product-flavors.
 */

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenLocal() // For local development with ./MavenLocalRelease.sh
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // For local development
        mavenCentral()
        google()
    }
}

rootProject.name = "convention-integration-sample"

include(":app")
include(":core")
include(":feature")
