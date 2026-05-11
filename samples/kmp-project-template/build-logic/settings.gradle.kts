pluginManagement {
    // Composite-include the upstream flavor-plugin build so this sample's
    // build-logic compiles against THIS branch's source, not whatever's published
    // to Maven Central. Path is relative to this settings.gradle.kts file.
    includeBuild("../../../build-logic") { name = "kmp-product-flavors-build-logic" }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
