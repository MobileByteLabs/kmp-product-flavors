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
 * v2.2 Phase 4A — variant promotion declaration.
 *
 * Captures the consumer's `kmpFlavors.promote("freeDev", "freeStaging") { … }`
 * call. The configuration block populates [transforms]; the configurator
 * registers a Gradle task from this data class.
 */
data class VariantPromotion(
    val from: String,
    val to: String,
    internal val transforms: MutableList<VariantPromotionTransform> = mutableListOf(),
    internal var copyResources: Boolean = true,
    internal var copyTests: Boolean = true,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * v2.2 Phase 4A — a single transformation step applied during promotion.
 *
 * Today supports `kind = "renamePackage"` (string-replace from → to across all
 * .kt source files). v2.3+ can add new kinds.
 */
data class VariantPromotionTransform(val kind: String, val from: String, val to: String) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * v2.2 Phase 4A — configuration scope for a single promotion declaration.
 *
 * Consumers receive this via:
 * ```kotlin
 * kmpFlavors.promote("freeDev", "freeStaging") { /* this: VariantPromotionScope */
 *     applyTransform("renamePackage", "com.example.dev" to "com.example.staging")
 *     copyResources(true)
 *     copyTests(true)
 * }
 * ```
 */
class VariantPromotionScope(internal val promotion: VariantPromotion) {

    fun applyTransform(kind: String, mapping: Pair<String, String>) {
        promotion.transforms.add(VariantPromotionTransform(kind, mapping.first, mapping.second))
    }

    fun copyResources(value: Boolean) {
        promotion.copyResources = value
    }

    fun copyTests(value: Boolean) {
        promotion.copyTests = value
    }
}
