/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors.internal

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Represents a platform group for source set organization.
 *
 * @property prefix The platform prefix (e.g., "android", "ios", "desktop")
 * @property mainSourceSet The main source set name (e.g., "androidMain")
 * @property parent The parent intermediate group (e.g., "web" for js/wasmJs)
 * @property isIntermediate Whether this is an intermediate group (webMain, nativeMain)
 */
data class PlatformGroup(val prefix: String, val mainSourceSet: String, val parent: String? = null, val isIntermediate: Boolean = false)

/**
 * Detects active KMP platforms and manages intermediate source sets.
 */
object PlatformDetector {

    private val IOS_TARGETS = setOf("iosX64", "iosArm64", "iosSimulatorArm64")
    private val MACOS_TARGETS = setOf("macosX64", "macosArm64")
    private val LINUX_TARGETS = setOf("linuxX64", "linuxArm64")
    private val MINGW_TARGETS = setOf("mingwX64")
    private val DESKTOP_TARGETS = setOf("desktop", "jvm")

    /**
     * Detects all active platforms in the Kotlin Multiplatform project.
     *
     * @param kotlin The KMP extension
     * @param logger Logger for debug output
     * @return List of detected platform groups
     */
    fun detect(kotlin: KotlinMultiplatformExtension, logger: Logger): List<PlatformGroup> {
        val targetNames = kotlin.targets.map { it.name }.toSet()
        val platforms = mutableListOf<PlatformGroup>()
        val needsNativeIntermediate = mutableSetOf<String>()
        val needsWebIntermediate = mutableSetOf<String>()

        logger.info("[KMP Flavors] Detected targets: $targetNames")

        // Android
        if ("android" in targetNames) {
            platforms.add(PlatformGroup("android", "androidMain"))
            logger.info("[KMP Flavors] Detected Android target")
        }

        // iOS
        if (targetNames.any { it in IOS_TARGETS }) {
            platforms.add(PlatformGroup("ios", "iosMain", parent = "native"))
            needsNativeIntermediate.add("ios")
            logger.info("[KMP Flavors] Detected iOS target(s)")
        }

        // macOS
        if (targetNames.any { it in MACOS_TARGETS }) {
            platforms.add(PlatformGroup("macos", "macosMain", parent = "native"))
            needsNativeIntermediate.add("macos")
            logger.info("[KMP Flavors] Detected macOS target(s)")
        }

        // Linux
        if (targetNames.any { it in LINUX_TARGETS }) {
            platforms.add(PlatformGroup("linux", "linuxMain", parent = "native"))
            needsNativeIntermediate.add("linux")
            logger.info("[KMP Flavors] Detected Linux target(s)")
        }

        // Windows (MinGW)
        if (targetNames.any { it in MINGW_TARGETS }) {
            platforms.add(PlatformGroup("mingw", "mingwMain", parent = "native"))
            needsNativeIntermediate.add("mingw")
            logger.info("[KMP Flavors] Detected Windows (MinGW) target(s)")
        }

        // Desktop (JVM)
        if (targetNames.any { it in DESKTOP_TARGETS }) {
            // Handle both "desktop" and "jvm" naming
            val sourceSetName = if ("desktop" in targetNames) "desktopMain" else "jvmMain"
            platforms.add(PlatformGroup("desktop", sourceSetName))
            logger.info("[KMP Flavors] Detected Desktop (JVM) target: $sourceSetName")
        }

        // JS
        if ("js" in targetNames) {
            platforms.add(PlatformGroup("js", "jsMain", parent = "web"))
            needsWebIntermediate.add("js")
            logger.info("[KMP Flavors] Detected JS target")
        }

        // WasmJS
        if ("wasmJs" in targetNames) {
            platforms.add(PlatformGroup("wasmJs", "wasmJsMain", parent = "web"))
            needsWebIntermediate.add("wasmJs")
            logger.info("[KMP Flavors] Detected WasmJS target")
        }

        // Add intermediate groups if needed
        if (needsNativeIntermediate.isNotEmpty()) {
            platforms.add(PlatformGroup("native", "nativeMain", isIntermediate = true))
            logger.info("[KMP Flavors] Will create nativeMain intermediate for: $needsNativeIntermediate")
        }

        if (needsWebIntermediate.isNotEmpty()) {
            platforms.add(PlatformGroup("web", "webMain", isIntermediate = true))
            logger.info("[KMP Flavors] Will create webMain intermediate for: $needsWebIntermediate")
        }

        return platforms
    }

    /**
     * Creates and wires intermediate source sets (webMain, nativeMain).
     *
     * @param kotlin The KMP extension
     * @param platforms The detected platforms
     */
    fun wireIntermediateSourceSets(kotlin: KotlinMultiplatformExtension, platforms: List<PlatformGroup>) {
        val sourceSets = kotlin.sourceSets
        val commonMain = sourceSets.getByName("commonMain")

        // Create webMain if needed
        if (platforms.any { it.prefix == "web" && it.isIntermediate }) {
            val webMain = sourceSets.maybeCreate("webMain").apply {
                this.kotlin.srcDir("src/webMain/kotlin")
                resources.srcDir("src/webMain/resources")
            }
            webMain.dependsOn(commonMain)

            // Wire js and wasmJs to webMain
            platforms.filter { it.parent == "web" }.forEach { platform ->
                val platformSourceSet = sourceSets.findByName(platform.mainSourceSet)
                platformSourceSet?.dependsOn(webMain)
            }
        }

        // Create nativeMain if needed
        if (platforms.any { it.prefix == "native" && it.isIntermediate }) {
            val nativeMain = sourceSets.maybeCreate("nativeMain").apply {
                this.kotlin.srcDir("src/nativeMain/kotlin")
                resources.srcDir("src/nativeMain/resources")
            }
            nativeMain.dependsOn(commonMain)

            // Wire native platforms to nativeMain
            platforms.filter { it.parent == "native" }.forEach { platform ->
                val platformSourceSet = sourceSets.findByName(platform.mainSourceSet)
                platformSourceSet?.dependsOn(nativeMain)
            }
        }
    }

    /**
     * Maps platform groups to their actual source sets.
     *
     * @param kotlin The KMP extension
     * @param platforms The detected platforms
     * @return Map of platform groups to source sets
     */
    fun resolveSourceSets(kotlin: KotlinMultiplatformExtension, platforms: List<PlatformGroup>): Map<PlatformGroup, KotlinSourceSet> {
        val sourceSets = kotlin.sourceSets

        return platforms.mapNotNull { platform ->
            val sourceSet = sourceSets.findByName(platform.mainSourceSet)
            if (sourceSet != null) {
                platform to sourceSet
            } else {
                null
            }
        }.toMap()
    }
}
