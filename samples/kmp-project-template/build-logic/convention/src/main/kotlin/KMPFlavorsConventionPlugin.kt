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
                    register("consumer") { priority.set(0) }
                    register("tier")     { priority.set(1) }
                }

                flavors {
                    register("internal") {
                        dimension.set("consumer")
                        isDefault.set(true)
                        buildConfigField("String", "CLIENT_ID",        "\"internal\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.yourdomain.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }

                    register("demo") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("String", "CLIENT_ID",        "\"demo\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://demo.yourdomain.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","true")
                    }

                    register("clientA") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clienta")
                        bundleIdSuffix.set(".clienta")
                        buildConfigField("String", "CLIENT_ID",        "\"clientA\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.banka.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.banka.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.banka.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }

                    register("clientB") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clientb")
                        bundleIdSuffix.set(".clientb")
                        buildConfigField("String", "CLIENT_ID",        "\"clientB\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.bankb.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.bankb.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.bankb.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
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
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","true")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "true")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate-DEBUG\"")
                    }

                    register("staging") {
                        isDebuggable.set(false)
                        applicationIdSuffix.set(".staging")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate-STAGING\"")
                    }

                    register("release") {
                        isDebuggable.set(false)
                        isMinifyEnabled.set(true)
                        buildConfigField("Boolean","ENABLE_LOGGING",    "false")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate\"")
                    }
                }

                bridgeAgpBuildTypes.set(true)
                bridgeAgpProductFlavors.set(true)
            }
        }
    }
}
