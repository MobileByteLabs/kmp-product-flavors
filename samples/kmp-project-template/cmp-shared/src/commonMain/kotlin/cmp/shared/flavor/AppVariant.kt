/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */

package cmp.shared.flavor

import org.openmf.kmptemplate.FlavorConfig

object AppVariant {
    val variantName: String get() = FlavorConfig.VARIANT_NAME
    val buildType: String get() = FlavorConfig.BUILD_TYPE
    val isDebug: Boolean get() = FlavorConfig.IS_DEBUG
    val isStaging: Boolean get() = FlavorConfig.IS_STAGING
    val isRelease: Boolean get() = FlavorConfig.IS_RELEASE
    val isInternal: Boolean get() = FlavorConfig.IS_INTERNAL
    val isDemo: Boolean get() = FlavorConfig.IS_DEMO
    val isClientA: Boolean get() = FlavorConfig.IS_CLIENT_A
    val isClientB: Boolean get() = FlavorConfig.IS_CLIENT_B
    val isAdvanced: Boolean get() = FlavorConfig.IS_ADVANCED
    val isBasic: Boolean get() = FlavorConfig.IS_BASIC

    val clientId: String get() = FlavorConfig.CLIENT_ID
    val clientTier: String get() = FlavorConfig.CLIENT_TIER

    val apiUrlDebug: String get() = FlavorConfig.API_URL_DEBUG
    val apiUrlStaging: String get() = FlavorConfig.API_URL_STAGING
    val apiUrlRelease: String get() = FlavorConfig.API_URL_RELEASE
    val allowUrlOverride: Boolean get() = FlavorConfig.ALLOW_URL_OVERRIDE

    val featureAnalytics: Boolean get() = FlavorConfig.FEATURE_ANALYTICS
    val featureReports: Boolean get() = FlavorConfig.FEATURE_REPORTS
    val featureBulkOperations: Boolean get() = FlavorConfig.FEATURE_BULK_OPERATIONS

    val enableLogging: Boolean get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val allowEnvSwitch: Boolean get() = FlavorConfig.ALLOW_ENV_SWITCH
    val logTag: String get() = FlavorConfig.LOG_TAG

    val activeApiUrl: String
        get() = when (buildType) {
            "debug"   -> apiUrlDebug
            "staging" -> apiUrlStaging
            else      -> apiUrlRelease
        }
}
