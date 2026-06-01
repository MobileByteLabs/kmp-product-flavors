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

package com.mobilebytelabs.kmpflavors

import java.io.Serializable

/**
 * v2.6 Phase 4 — serializable spec for variant-aware network constants.
 *
 * Produced from `kmpFlavors { buildKonfig { network { baseUrl(...); timeout(N) } } }`
 * via [BuildKonfigDsl]. Read at task-execution time by
 * [com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask] to emit
 * `BuildKonfig.Network { BASE_URL; TIMEOUT_SECONDS }`.
 *
 * `Serializable` is required for round-trip through Gradle's configuration cache
 * (same as [KoinModuleSpec] / `BuildConfigField`).
 */
data class NetworkConfigSpec(
    /**
     * Per-flavor `flavor name → URL` mapping. The codegen task picks the URL
     * whose key matches one of the active variant's flavor names.
     */
    val baseUrls: Map<String, String>,
    /**
     * Global request-timeout constant emitted as `TIMEOUT_SECONDS`. Per-variant
     * overrides are out of scope for v2.6 (D14 — single global timeout).
     */
    val timeoutSeconds: Int = 30,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
