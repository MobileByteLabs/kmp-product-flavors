/*
 * Core module using the KMP Library convention plugin.
 *
 * This module demonstrates how flavors are automatically applied
 * through the convention plugin system.
 */

plugins {
    id("org.convention.kmp.library")
}

// Optional: Add module-specific build config fields
kmpFlavors {
    flavors {
        named("demo") {
            buildConfigField("String", "API_URL", "\"https://demo-api.example.com\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        }
        named("prod") {
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "false")
        }
    }
}
