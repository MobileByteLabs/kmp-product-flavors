/*
 * Main application module.
 *
 * This module uses the KMP Library convention plugin which automatically
 * applies flavor support.
 */

plugins {
    id("org.convention.kmp.library")
}

dependencies {
    commonMainImplementation(project(":core"))
    commonMainImplementation(project(":feature"))
}
