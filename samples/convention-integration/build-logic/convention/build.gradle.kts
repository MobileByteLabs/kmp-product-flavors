/*
 * Convention plugins for the sample project.
 *
 * This demonstrates how kmp-product-flavors can be integrated into
 * a project's build-logic using convention plugins.
 */

plugins {
    `kotlin-dsl`
}

group = "org.convention.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Kotlin Multiplatform plugin
    compileOnly(libs.kotlin.gradle.plugin)

    // KMP Product Flavors plugin - from Maven Central (mavenLocal for local development)
    implementation("io.github.mobilebytelabs.kmpflavors:flavor-plugin:1.0.0")
}

gradlePlugin {
    plugins {
        // KMP Flavors convention plugin
        register("kmpFlavors") {
            id = "org.convention.kmp.flavors"
            implementationClass = "KMPFlavorsConventionPlugin"
            description = "Configures KMP Product Flavors with project-wide defaults"
        }

        // KMP Library convention plugin (includes flavors)
        register("kmpLibrary") {
            id = "org.convention.kmp.library"
            implementationClass = "KMPLibraryConventionPlugin"
            description = "Configures KMP library modules with flavors support"
        }
    }
}
