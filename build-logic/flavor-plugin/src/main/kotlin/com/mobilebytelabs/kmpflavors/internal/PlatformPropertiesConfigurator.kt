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

import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Configures platform-specific properties based on the active variant.
 *
 * This class applies flavor-specific suffixes and properties to various platforms:
 * - Android: applicationIdSuffix, versionNameSuffix
 * - iOS: bundleIdSuffix
 * - Desktop: window title suffix
 * - Web: page title suffix
 */
class PlatformPropertiesConfigurator(private val logger: Logger) {

    /**
     * Applies platform-specific properties from the active variant.
     *
     * This sets up Gradle extra properties that can be consumed by platform-specific
     * build configurations. The properties are exposed at project level and can be
     * accessed in build scripts.
     *
     * @param project The Gradle project
     * @param activeVariant The currently active variant
     */
    fun configure(project: Project, activeVariant: FlavorVariant) {
        val extras = project.extensions.extraProperties

        // Set variant info properties
        extras.set("kmpFlavor.variantName", activeVariant.name)
        extras.set("kmpFlavor.flavorNames", activeVariant.flavorNames)

        // Set platform-specific suffix properties
        val appIdSuffix = activeVariant.combinedApplicationIdSuffix
        if (appIdSuffix.isNotEmpty()) {
            extras.set("kmpFlavor.applicationIdSuffix", appIdSuffix)
            logger.info("[KMP Flavors] Set applicationIdSuffix: $appIdSuffix")
        }

        val bundleIdSuffix = activeVariant.combinedBundleIdSuffix
        if (bundleIdSuffix.isNotEmpty()) {
            extras.set("kmpFlavor.bundleIdSuffix", bundleIdSuffix)
            logger.info("[KMP Flavors] Set bundleIdSuffix: $bundleIdSuffix")
        }

        val versionNameSuffix = activeVariant.combinedVersionNameSuffix
        if (versionNameSuffix.isNotEmpty()) {
            extras.set("kmpFlavor.versionNameSuffix", versionNameSuffix)
            logger.info("[KMP Flavors] Set versionNameSuffix: $versionNameSuffix")
        }

        val desktopTitleSuffix = activeVariant.combinedDesktopTitleSuffix
        if (desktopTitleSuffix.isNotEmpty()) {
            extras.set("kmpFlavor.desktopTitleSuffix", desktopTitleSuffix)
            logger.info("[KMP Flavors] Set desktopTitleSuffix: $desktopTitleSuffix")
        }

        val webTitleSuffix = activeVariant.combinedWebTitleSuffix
        if (webTitleSuffix.isNotEmpty()) {
            extras.set("kmpFlavor.webTitleSuffix", webTitleSuffix)
            logger.info("[KMP Flavors] Set webTitleSuffix: $webTitleSuffix")
        }

        // Set merged extras
        val mergedExtras = activeVariant.mergedExtras
        if (mergedExtras.isNotEmpty()) {
            mergedExtras.forEach { (key, value) ->
                extras.set("kmpFlavor.extra.$key", value)
                logger.info("[KMP Flavors] Set extra.$key: $value")
            }
        }

        // Configure Android extension if available
        configureAndroidExtension(project, activeVariant)
    }

    /**
     * Configures the Android extension with flavor-specific properties.
     *
     * This is only executed if the Android plugin is applied.
     */
    private fun configureAndroidExtension(project: Project, activeVariant: FlavorVariant) {
        // Try to find Android extension (either application or library)
        val androidExtension = project.extensions.findByName("android") ?: return

        val appIdSuffix = activeVariant.combinedApplicationIdSuffix
        val versionSuffix = activeVariant.combinedVersionNameSuffix

        if (appIdSuffix.isEmpty() && versionSuffix.isEmpty()) {
            return
        }

        // Use reflection to safely configure without direct dependency
        try {
            val defaultConfig = androidExtension.javaClass.getMethod("getDefaultConfig").invoke(androidExtension)

            if (appIdSuffix.isNotEmpty()) {
                val setApplicationIdSuffix = defaultConfig.javaClass.getMethod("setApplicationIdSuffix", String::class.java)
                setApplicationIdSuffix.invoke(defaultConfig, appIdSuffix)
                logger.lifecycle("[KMP Flavors] Applied Android applicationIdSuffix: $appIdSuffix")
            }

            if (versionSuffix.isNotEmpty()) {
                val setVersionNameSuffix = defaultConfig.javaClass.getMethod("setVersionNameSuffix", String::class.java)
                setVersionNameSuffix.invoke(defaultConfig, versionSuffix)
                logger.lifecycle("[KMP Flavors] Applied Android versionNameSuffix: $versionSuffix")
            }
        } catch (e: Exception) {
            logger.info("[KMP Flavors] Could not configure Android extension: ${e.message}")
        }
    }
}
