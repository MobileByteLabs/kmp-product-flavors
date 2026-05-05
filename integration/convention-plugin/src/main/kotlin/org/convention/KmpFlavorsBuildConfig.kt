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

package org.convention

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension

/**
 * Helper functions for configuring build config fields in a type-safe manner.
 *
 * These utilities make it easier to add common build config fields across
 * multiple flavors consistently.
 */
object KmpFlavorsBuildConfig {

    /**
     * Common build config field names used across the project.
     */
    object Fields {
        const val IS_DEMO = "IS_DEMO"
        const val IS_PRODUCTION = "IS_PRODUCTION"
        const val API_BASE_URL = "API_BASE_URL"
        const val ENVIRONMENT = "ENVIRONMENT"
        const val DEBUG_ENABLED = "DEBUG_ENABLED"
        const val ANALYTICS_ENABLED = "ANALYTICS_ENABLED"
        const val LOG_LEVEL = "LOG_LEVEL"
    }

    /**
     * Log levels for the LOG_LEVEL build config field.
     */
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        NONE,
    }
}

/**
 * Extension function to add common demo/prod build config fields.
 *
 * Adds IS_DEMO and IS_PRODUCTION boolean fields based on the flavor name.
 *
 * ```kotlin
 * flavors {
 *     register("demo") {
 *         addDemoProdFields(isDemo = true)
 *     }
 *     register("prod") {
 *         addDemoProdFields(isDemo = false)
 *     }
 * }
 * ```
 */
fun FlavorConfig.addDemoProdFields(isDemo: Boolean) {
    buildConfigField("Boolean", KmpFlavorsBuildConfig.Fields.IS_DEMO, isDemo.toString())
    buildConfigField("Boolean", KmpFlavorsBuildConfig.Fields.IS_PRODUCTION, (!isDemo).toString())
}

/**
 * Extension function to add API base URL build config field.
 *
 * ```kotlin
 * flavors {
 *     register("dev") {
 *         addApiBaseUrl("https://dev-api.example.com")
 *     }
 *     register("prod") {
 *         addApiBaseUrl("https://api.example.com")
 *     }
 * }
 * ```
 */
fun FlavorConfig.addApiBaseUrl(url: String) {
    buildConfigField("String", KmpFlavorsBuildConfig.Fields.API_BASE_URL, "\"$url\"")
}

/**
 * Extension function to add environment name build config field.
 *
 * ```kotlin
 * flavors {
 *     register("dev") {
 *         addEnvironment("development")
 *     }
 *     register("staging") {
 *         addEnvironment("staging")
 *     }
 *     register("prod") {
 *         addEnvironment("production")
 *     }
 * }
 * ```
 */
fun FlavorConfig.addEnvironment(environment: String) {
    buildConfigField("String", KmpFlavorsBuildConfig.Fields.ENVIRONMENT, "\"$environment\"")
}

/**
 * Extension function to add debug/analytics flags.
 *
 * ```kotlin
 * flavors {
 *     register("dev") {
 *         addDebugFlags(debugEnabled = true, analyticsEnabled = false)
 *     }
 *     register("prod") {
 *         addDebugFlags(debugEnabled = false, analyticsEnabled = true)
 *     }
 * }
 * ```
 */
fun FlavorConfig.addDebugFlags(debugEnabled: Boolean, analyticsEnabled: Boolean) {
    buildConfigField("Boolean", KmpFlavorsBuildConfig.Fields.DEBUG_ENABLED, debugEnabled.toString())
    buildConfigField("Boolean", KmpFlavorsBuildConfig.Fields.ANALYTICS_ENABLED, analyticsEnabled.toString())
}

/**
 * Extension function to add log level build config field.
 *
 * ```kotlin
 * flavors {
 *     register("dev") {
 *         addLogLevel(LogLevel.DEBUG)
 *     }
 *     register("prod") {
 *         addLogLevel(LogLevel.WARN)
 *     }
 * }
 * ```
 */
fun FlavorConfig.addLogLevel(level: KmpFlavorsBuildConfig.LogLevel) {
    buildConfigField("String", KmpFlavorsBuildConfig.Fields.LOG_LEVEL, "\"${level.name}\"")
}

/**
 * Configure all demo/prod flavors with standard build config fields.
 *
 * This is a convenience function that sets up common patterns:
 * - IS_DEMO / IS_PRODUCTION flags
 * - API_BASE_URL for each environment
 * - DEBUG_ENABLED / ANALYTICS_ENABLED flags
 * - LOG_LEVEL
 *
 * ```kotlin
 * kmpFlavors {
 *     configureStandardBuildConfig(
 *         demoApiUrl = "https://demo-api.example.com",
 *         prodApiUrl = "https://api.example.com"
 *     )
 * }
 * ```
 */
fun KmpFlavorExtension.configureStandardBuildConfig(
    demoApiUrl: String,
    prodApiUrl: String,
    demoDebugEnabled: Boolean = true,
    prodDebugEnabled: Boolean = false,
    demoAnalyticsEnabled: Boolean = false,
    prodAnalyticsEnabled: Boolean = true,
    demoLogLevel: KmpFlavorsBuildConfig.LogLevel = KmpFlavorsBuildConfig.LogLevel.DEBUG,
    prodLogLevel: KmpFlavorsBuildConfig.LogLevel = KmpFlavorsBuildConfig.LogLevel.WARN,
) {
    flavors {
        // Configure demo flavor
        matching { it.name.equals("demo", ignoreCase = true) }.configureEach {
            addDemoProdFields(isDemo = true)
            addApiBaseUrl(demoApiUrl)
            addDebugFlags(demoDebugEnabled, demoAnalyticsEnabled)
            addLogLevel(demoLogLevel)
        }

        // Configure prod flavor
        matching { it.name.equals("prod", ignoreCase = true) }.configureEach {
            addDemoProdFields(isDemo = false)
            addApiBaseUrl(prodApiUrl)
            addDebugFlags(prodDebugEnabled, prodAnalyticsEnabled)
            addLogLevel(prodLogLevel)
        }
    }
}
