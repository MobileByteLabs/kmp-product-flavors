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

import org.openmf.kmptemplate.BuildKonfig

/**
 * Typed wrapper around the generated `BuildKonfig` object.
 *
 * Exposes only the BASE contract (demo/prod tier + debug/staging/release).
 * Consumer apps that add their own flavor dimensions via
 * `build-logic/convention/src/main/kotlin/local/LocalFlavors.kt` should expose
 * their consumer-specific helpers in a separate `LocalAppVariant.kt` next to
 * this file, NOT by editing this file (which is synced).
 */
object AppVariant {

    // ---- Base flavor (tier) ---------------------------------------------------
    val isDemo: Boolean get() = BuildKonfig.IS_DEMO
    val isProd: Boolean get() = BuildKonfig.IS_PROD

    val baseUrl: String        get() = BuildKonfig.BASE_URL
    val demoUsername: String   get() = BuildKonfig.DEMO_USERNAME
    val demoPassword: String   get() = BuildKonfig.DEMO_PASSWORD

    // ---- Build type -----------------------------------------------------------
    val buildType: String      get() = BuildKonfig.BUILD_TYPE
    val isDebug: Boolean       get() = BuildKonfig.IS_DEBUG
    val isStaging: Boolean     get() = BuildKonfig.IS_STAGING
    val isRelease: Boolean     get() = BuildKonfig.IS_RELEASE

    val enableLogging: Boolean    get() = BuildKonfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = BuildKonfig.SHOW_DEBUG_OVERLAY
    val logTag: String            get() = BuildKonfig.LOG_TAG

    // ---- Variant identity -----------------------------------------------------
    val variantName: String   get() = BuildKonfig.VARIANT_NAME
}
