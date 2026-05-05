/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")

            extensions.configure<KmpFlavorExtension> {
                buildConfigPackage.set("org.openmf.kmptemplate")
                buildConfigClassName.set("FlavorConfig")

                flavorDimensions {
                    register("consumer") {
                        priority.set(0)
                    }
                    register("tier") {
                        priority.set(1)
                    }
                }

                flavors {
                    register("internal") {
                        dimension.set("consumer")
                        isDefault.set(true)
                        buildConfigField("String", "CLIENT_ID", "\"internal\"")
                        buildConfigField("String", "API_URL_DEBUG",   "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING", "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE", "\"https://api.yourdomain.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE", "false")
                    }

                    register("demo") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("String", "CLIENT_ID", "\"demo\"")
                        buildConfigField("String", "API_URL_DEBUG",   "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING", "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE", "\"https://demo.yourdomain.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE", "true")
                    }

                    register("clientA") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clienta")
                        bundleIdSuffix.set(".clienta")
                        buildConfigField("String", "CLIENT_ID", "\"clientA\"")
                        buildConfigField("String", "API_URL_DEBUG",   "\"https://dev.banka.com\"")
                        buildConfigField("String", "API_URL_STAGING", "\"https://staging.banka.com\"")
                        buildConfigField("String", "API_URL_RELEASE", "\"https://api.banka.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE", "false")
                    }

                    register("clientB") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clientb")
                        bundleIdSuffix.set(".clientb")
                        buildConfigField("String", "CLIENT_ID", "\"clientB\"")
                        buildConfigField("String", "API_URL_DEBUG",   "\"https://dev.bankb.com\"")
                        buildConfigField("String", "API_URL_STAGING", "\"https://staging.bankb.com\"")
                        buildConfigField("String", "API_URL_RELEASE", "\"https://api.bankb.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE", "false")
                    }

                    register("advanced") {
                        dimension.set("tier")
                        isDefault.set(true)
                        buildConfigField("String",  "CLIENT_TIER",             "\"advanced\"")
                        buildConfigField("Boolean", "FEATURE_ANALYTICS",       "true")
                        buildConfigField("Boolean", "FEATURE_REPORTS",         "true")
                        buildConfigField("Boolean", "FEATURE_BULK_OPERATIONS", "true")
                    }

                    register("basic") {
                        dimension.set("tier")
                        buildConfigField("String",  "CLIENT_TIER",             "\"basic\"")
                        buildConfigField("Boolean", "FEATURE_ANALYTICS",       "false")
                        buildConfigField("Boolean", "FEATURE_REPORTS",         "false")
                        buildConfigField("Boolean", "FEATURE_BULK_OPERATIONS", "false")
                    }
                }

                buildTypes {
                    register("debug") {
                        isDefault.set(true)
                        isDebuggable.set(true)
                        applicationIdSuffix.set(".debug")
                        buildConfigField("Boolean", "ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean", "SHOW_DEBUG_OVERLAY","true")
                        buildConfigField("Boolean", "ALLOW_ENV_SWITCH",  "true")
                        buildConfigField("String",  "LOG_TAG",           "\"KMPTemplate-DEBUG\"")
                    }

                    register("staging") {
                        isDebuggable.set(false)
                        applicationIdSuffix.set(".staging")
                        buildConfigField("Boolean", "ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean", "SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean", "ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String",  "LOG_TAG",           "\"KMPTemplate-STAGING\"")
                    }

                    register("release") {
                        isDebuggable.set(false)
                        isMinifyEnabled.set(true)
                        buildConfigField("Boolean", "ENABLE_LOGGING",    "false")
                        buildConfigField("Boolean", "SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean", "ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String",  "LOG_TAG",           "\"KMPTemplate\"")
                    }
                }

                bridgeAgpBuildTypes.set(true)
                bridgeAgpProductFlavors.set(true)
            }
        }
    }
}
