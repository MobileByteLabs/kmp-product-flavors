/*
 * Root build file for the convention integration sample.
 */

plugins {
    // Apply convention plugins to all modules
    alias(libs.plugins.kotlinMultiplatform) apply false
}

allprojects {
    group = "com.example.sample"
    version = "1.0.0"
}
