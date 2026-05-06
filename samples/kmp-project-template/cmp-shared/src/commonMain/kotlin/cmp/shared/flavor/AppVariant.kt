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

package cmp.shared.flavor

import org.openmf.kmptemplate.FlavorConfig

object AppVariant {
    val variantName: String  get() = FlavorConfig.VARIANT_NAME
    val buildType: String    get() = FlavorConfig.BUILD_TYPE
    val isDebug: Boolean     get() = FlavorConfig.IS_DEBUG
    val isStaging: Boolean   get() = FlavorConfig.IS_STAGING
    val isRelease: Boolean   get() = FlavorConfig.IS_RELEASE
    val isInternal: Boolean  get() = FlavorConfig.IS_INTERNAL
    val isDemo: Boolean      get() = FlavorConfig.IS_DEMO
    val isClientA: Boolean   get() = FlavorConfig.IS_CLIENT_A
    val isClientB: Boolean   get() = FlavorConfig.IS_CLIENT_B
    val isAdvanced: Boolean  get() = FlavorConfig.IS_ADVANCED
    val isBasic: Boolean     get() = FlavorConfig.IS_BASIC
    val clientId: String     get() = FlavorConfig.CLIENT_ID
    val clientTier: String   get() = FlavorConfig.CLIENT_TIER
    val apiUrlDebug: String   get() = FlavorConfig.API_URL_DEBUG
    val apiUrlStaging: String get() = FlavorConfig.API_URL_STAGING
    val apiUrlRelease: String get() = FlavorConfig.API_URL_RELEASE
    val allowUrlOverride: Boolean      get() = FlavorConfig.ALLOW_URL_OVERRIDE
    val featureAnalytics: Boolean      get() = FlavorConfig.FEATURE_ANALYTICS
    val featureReports: Boolean        get() = FlavorConfig.FEATURE_REPORTS
    val featureBulkOperations: Boolean get() = FlavorConfig.FEATURE_BULK_OPERATIONS
    val enableLogging: Boolean    get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val allowEnvSwitch: Boolean   get() = FlavorConfig.ALLOW_ENV_SWITCH
    val logTag: String            get() = FlavorConfig.LOG_TAG

    val activeApiUrl: String get() = when (buildType) {
        "debug"   -> apiUrlDebug
        "staging" -> apiUrlStaging
        else      -> apiUrlRelease
    }
}
