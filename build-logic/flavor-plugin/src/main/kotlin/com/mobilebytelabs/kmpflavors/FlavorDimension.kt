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

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration for a flavor dimension.
 *
 * Dimensions define independent flavor axes. For example, you might have:
 * - "tier" dimension with flavors: free, paid
 * - "environment" dimension with flavors: dev, staging, prod
 *
 * This creates a variant matrix of 2x3 = 6 variants:
 * freeDev, freeStaging, freeProd, paidDev, paidStaging, paidProd
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     flavorDimensions {
 *         register("tier") {
 *             priority.set(0)  // Applied first
 *         }
 *         register("environment") {
 *             priority.set(1)  // Applied second, overrides tier
 *         }
 *     }
 * }
 * ```
 */
open class FlavorDimension @Inject constructor(
    private val dimensionName: String,
    objects: ObjectFactory,
) : Named {
    /**
     * Returns the name of this dimension.
     */
    override fun getName(): String = dimensionName

    /**
     * The merge priority for this dimension.
     *
     * When multiple dimensions have conflicting build config fields,
     * the dimension with higher priority takes precedence.
     * Lower values are applied first, higher values override.
     *
     * Convention: 0
     */
    val priority: Property<Int> = objects.property(Int::class.javaObjectType).convention(0)
}
