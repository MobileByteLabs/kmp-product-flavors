/*
 * Feature module using the KMP Library convention plugin.
 *
 * This module inherits flavor configuration from the convention plugin
 * and can add feature-specific build config fields.
 */

plugins {
    id("org.convention.kmp.library")
}

dependencies {
    commonMainImplementation(project(":core"))
}

// Optional: Add feature-specific build config fields
kmpFlavors {
    flavors {
        named("demo") {
            buildConfigField("Boolean", "SHOW_DEBUG_UI", "true")
        }
        named("prod") {
            buildConfigField("Boolean", "SHOW_DEBUG_UI", "false")
        }
    }
}
