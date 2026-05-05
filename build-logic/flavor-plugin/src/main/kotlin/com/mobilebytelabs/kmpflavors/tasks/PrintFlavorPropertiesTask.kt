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

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task that prints the platform-specific properties for the active variant.
 *
 * This is useful for debugging and CI/CD scripts that need to know the
 * current variant's configuration values.
 *
 * Usage:
 * ```bash
 * ./gradlew printFlavorProperties
 * ```
 *
 * Output:
 * ```
 * [KMP Flavors] Properties for variant: freeDev
 *   applicationIdSuffix: .free.dev
 *   bundleIdSuffix: .free.dev
 *   versionNameSuffix: -free-dev
 *   desktopTitleSuffix: Free Dev
 *   webTitleSuffix: Free Dev
 * ```
 */
abstract class PrintFlavorPropertiesTask : DefaultTask() {

    /**
     * The active variant name.
     */
    @get:Input
    abstract val variantName: Property<String>

    /**
     * The combined application ID suffix for Android.
     */
    @get:Input
    @get:Optional
    abstract val applicationIdSuffix: Property<String>

    /**
     * The combined bundle ID suffix for iOS.
     */
    @get:Input
    @get:Optional
    abstract val bundleIdSuffix: Property<String>

    /**
     * The combined version name suffix.
     */
    @get:Input
    @get:Optional
    abstract val versionNameSuffix: Property<String>

    /**
     * The combined desktop window title suffix.
     */
    @get:Input
    @get:Optional
    abstract val desktopTitleSuffix: Property<String>

    /**
     * The combined web page title suffix.
     */
    @get:Input
    @get:Optional
    abstract val webTitleSuffix: Property<String>

    init {
        group = "kmp flavors"
        description = "Prints the platform-specific properties for the active variant"
    }

    @TaskAction
    fun print() {
        logger.lifecycle("[KMP Flavors] Properties for variant: ${variantName.get()}")
        logger.lifecycle("  applicationIdSuffix: ${applicationIdSuffix.orNull ?: "(not set)"}")
        logger.lifecycle("  bundleIdSuffix: ${bundleIdSuffix.orNull ?: "(not set)"}")
        logger.lifecycle("  versionNameSuffix: ${versionNameSuffix.orNull ?: "(not set)"}")
        logger.lifecycle("  desktopTitleSuffix: ${desktopTitleSuffix.orNull ?: "(not set)"}")
        logger.lifecycle("  webTitleSuffix: ${webTitleSuffix.orNull ?: "(not set)"}")
    }
}
