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
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */

package cmp.shared.flavor

import org.openmf.kmptemplate.FlavorConfig

/**
 * Typed wrapper around the generated `FlavorConfig` object.
 *
 * Exposes only the BASE contract (demo/prod tier + debug/staging/release).
 * Consumer apps that add their own flavor dimensions via
 * `build-logic/convention/src/main/kotlin/local/LocalFlavors.kt` should expose
 * their consumer-specific helpers in a separate `LocalAppVariant.kt` next to
 * this file, NOT by editing this file (which is synced).
 */
object AppVariant {

    // ---- Base flavor (tier) ---------------------------------------------------
    val isDemo: Boolean get() = FlavorConfig.IS_DEMO
    val isProd: Boolean get() = FlavorConfig.IS_PROD

    val baseUrl: String        get() = FlavorConfig.BASE_URL
    val demoUsername: String   get() = FlavorConfig.DEMO_USERNAME
    val demoPassword: String   get() = FlavorConfig.DEMO_PASSWORD

    // ---- Build type -----------------------------------------------------------
    val buildType: String      get() = FlavorConfig.BUILD_TYPE
    val isDebug: Boolean       get() = FlavorConfig.IS_DEBUG
    val isStaging: Boolean     get() = FlavorConfig.IS_STAGING
    val isRelease: Boolean     get() = FlavorConfig.IS_RELEASE

    val enableLogging: Boolean    get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val logTag: String            get() = FlavorConfig.LOG_TAG

    // ---- Variant identity -----------------------------------------------------
    val variantName: String   get() = FlavorConfig.VARIANT_NAME
}
