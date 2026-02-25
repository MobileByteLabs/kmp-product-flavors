/*
 * Copyright 2026 Mifos Initiative
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

package org.convention

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Helper functions for adding common build config fields to KMP flavors.
 *
 * ## Usage
 *
 * ```kotlin
 * kmpFlavors {
 *     flavors {
 *         named("demo") {
 *             addApiConfig("https://demo-api.mifos.org")
 *             addFeatureFlags(showDebugMenu = true)
 *         }
 *         named("prod") {
 *             addApiConfig("https://api.mifos.org")
 *             addFeatureFlags(showDebugMenu = false)
 *         }
 *     }
 * }
 * ```
 */
object KmpFlavorsBuildConfig {

    /**
     * Adds API configuration build config fields to a flavor.
     *
     * @param baseUrl The base URL for the API
     * @param apiVersion Optional API version string
     */
    fun FlavorConfig.addApiConfig(
        baseUrl: String,
        apiVersion: String = "v1",
    ) {
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "API_VERSION", "\"$apiVersion\"")
    }

    /**
     * Adds feature flag build config fields to a flavor.
     *
     * @param showDebugMenu Whether to show the debug menu
     * @param enableAnalytics Whether analytics are enabled
     * @param enableCrashReporting Whether crash reporting is enabled
     */
    fun FlavorConfig.addFeatureFlags(
        showDebugMenu: Boolean = false,
        enableAnalytics: Boolean = true,
        enableCrashReporting: Boolean = true,
    ) {
        buildConfigField("Boolean", "SHOW_DEBUG_MENU", showDebugMenu.toString())
        buildConfigField("Boolean", "ENABLE_ANALYTICS", enableAnalytics.toString())
        buildConfigField("Boolean", "ENABLE_CRASH_REPORTING", enableCrashReporting.toString())
    }

    /**
     * Adds environment-specific build config fields to a flavor.
     *
     * @param environmentName The environment name (e.g., "demo", "staging", "production")
     * @param isProduction Whether this is a production environment
     */
    fun FlavorConfig.addEnvironmentConfig(
        environmentName: String,
        isProduction: Boolean,
    ) {
        buildConfigField("String", "ENVIRONMENT", "\"$environmentName\"")
        buildConfigField("Boolean", "IS_PRODUCTION", isProduction.toString())
    }
}

/**
 * Extension function to configure KMP flavors with custom build config fields.
 *
 * ## Example
 *
 * ```kotlin
 * project.configureFlavorBuildConfig {
 *     flavors {
 *         named("demo") {
 *             buildConfigField("String", "API_URL", "\"https://demo.mifos.org\"")
 *         }
 *         named("prod") {
 *             buildConfigField("String", "API_URL", "\"https://api.mifos.org\"")
 *         }
 *     }
 * }
 * ```
 */
fun Project.configureFlavorBuildConfig(
    block: KmpFlavorExtension.() -> Unit,
) {
    extensions.configure<KmpFlavorExtension> {
        block()
    }
}
