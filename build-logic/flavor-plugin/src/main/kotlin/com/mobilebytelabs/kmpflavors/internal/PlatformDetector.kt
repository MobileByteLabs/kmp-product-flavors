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
    private val TVOS_TARGETS = setOf("tvosX64", "tvosArm64", "tvosSimulatorArm64")
    private val WATCHOS_TARGETS = setOf(
        "watchosX64",
        "watchosArm64",
        "watchosSimulatorArm64",
        "watchosDeviceArm64",
    )
    private val LINUX_TARGETS = setOf("linuxX64", "linuxArm64")
    private val MINGW_TARGETS = setOf("mingwX64")
    private val DESKTOP_TARGETS = setOf("desktop", "jvm")
    private val ANDROID_NATIVE_TARGETS = setOf(
        "androidNativeArm64",
        "androidNativeX64",
        "androidNativeArm32",
        "androidNativeX86",
    )

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

        // tvOS
        if (targetNames.any { it in TVOS_TARGETS }) {
            platforms.add(PlatformGroup("tvos", "tvosMain", parent = "native"))
            needsNativeIntermediate.add("tvos")
            logger.info("[KMP Flavors] Detected tvOS target(s)")
        }

        // watchOS
        if (targetNames.any { it in WATCHOS_TARGETS }) {
            platforms.add(PlatformGroup("watchos", "watchosMain", parent = "native"))
            needsNativeIntermediate.add("watchos")
            logger.info("[KMP Flavors] Detected watchOS target(s)")
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

        // Android Native (server-side native binaries on Android NDK toolchains)
        if (targetNames.any { it in ANDROID_NATIVE_TARGETS }) {
            platforms.add(PlatformGroup("androidNative", "androidNativeMain", parent = "native"))
            needsNativeIntermediate.add("androidNative")
            logger.info("[KMP Flavors] Detected Android Native target(s)")
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

        // WasmWasi
        if ("wasmWasi" in targetNames) {
            platforms.add(PlatformGroup("wasmWasi", "wasmWasiMain", parent = "web"))
            needsWebIntermediate.add("wasmWasi")
            logger.info("[KMP Flavors] Detected WasmWasi target")
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
        // Apply the default hierarchy template first so Kotlin owns nativeMain and its
        // platform→nativeMain edges. The plugin only adds dependsOn for its own custom
        // flavor source sets (commonDev, nativeFree, etc.) on top of that.
        kotlin.applyDefaultHierarchyTemplate()

        val sourceSets = kotlin.sourceSets

        // webMain and its platform→webMain edges are owned by the default hierarchy
        // template (applied above) on Kotlin 2.1+. We only register source dirs so
        // consumers can drop code into src/webMain/kotlin/. No explicit dependsOn —
        // the probe in earlier versions was racy with the template's lazy edge
        // installation and produced spurious "Redundant dependsOn" warnings.
        if (platforms.any { it.prefix == "web" && it.isIntermediate }) {
            sourceSets.maybeCreate("webMain").apply {
                this.kotlin.srcDir("src/webMain/kotlin")
                resources.srcDir("src/webMain/resources")
            }
        }

        // nativeMain and its platform→nativeMain edges are owned by the default hierarchy
        // template (applied above). Only register source dirs so the plugin can resolve
        // nativeMain when creating flavor intermediates (nativeDev, nativeFree, etc.).
        if (platforms.any { it.prefix == "native" && it.isIntermediate }) {
            sourceSets.maybeCreate("nativeMain").apply {
                this.kotlin.srcDir("src/nativeMain/kotlin")
                resources.srcDir("src/nativeMain/resources")
            }
            // dependsOn(commonMain) and platform→nativeMain edges intentionally omitted —
            // the default hierarchy template already manages them.
        }
    }

    /**
     * Add a [from] dependsOn(target) edge only if it isn't already present (directly
     * or transitively) in [from]'s dependsOn chain. Prevents the
     * "Redundant dependsOn Kotlin Source Sets" warning when Kotlin's default
     * hierarchy template already manages an edge.
     */
    private fun wireIfMissing(from: KotlinSourceSet, target: KotlinSourceSet) {
        if (target in from.dependsOn) return
        // Walk transitive dependsOn to catch edges added by the hierarchy template.
        val seen = mutableSetOf<KotlinSourceSet>()
        val queue = ArrayDeque(from.dependsOn)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (!seen.add(next)) continue
            if (next == target) return
            queue.addAll(next.dependsOn)
        }
        from.dependsOn(target)
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
