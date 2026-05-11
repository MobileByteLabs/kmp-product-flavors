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

/**
 * Per-flavor credentials behaviour. Demonstrates the v1.1.2 source-set
 * wiring — `actual` declarations live in `commonDemo/` or `commonProd/`
 * depending on the active flavor.
 */
expect object Credentials {
    /** True when this build ships with demo credentials baked in. */
    val hasPreloadedCredentials: Boolean

    /** True when the login screen should allow the user to override the URL. */
    val allowUrlOverride: Boolean

    /** Default username for demo builds; empty for prod. */
    val defaultUsername: String
}
