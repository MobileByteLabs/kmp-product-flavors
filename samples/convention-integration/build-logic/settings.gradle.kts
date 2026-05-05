/*
 * Build logic for convention plugins.
 */

dependencyResolutionManagement {
    repositories {
        mavenLocal() // For local development with ./MavenLocalRelease.sh
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
