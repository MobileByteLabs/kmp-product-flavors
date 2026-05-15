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
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import javax.inject.Inject

/**
 * Public, configurable v2.0 variant API surface (RFC §3 Q19-B).
 *
 * Exposes one element per matrix-mode variant under
 * `kmpFlavors.variants`. Consumers customize variants via standard
 * Gradle `NamedDomainObjectCollection` mechanics:
 *
 * ```kotlin
 * kmpFlavors.variants.matching { it.flavors.contains("paid") }.configureEach {
 *     // attach a per-variant verification task, override BuildConfig
 *     // field, etc. Read-after-configuration; writes happen via
 *     // configureEach for ordering safety.
 * }
 * ```
 *
 * Implemented as an `abstract class` so Gradle's `objects.newInstance`
 * can provide a managed implementation that's config-cache friendly.
 *
 * **Note**: this is the consumer-facing API. The internal variant
 * resolution data class is [FlavorVariant], which carries
 * dimension-merged buildConfigField maps and other internal
 * bookkeeping. [KmpFlavorVariant] is a thin façade over that — it
 * exposes only fields the RFC §3 Q19 spec requires:
 *  - `name` (Gradle `Named`),
 *  - `flavors: List<String>`,
 *  - `buildType: String?`,
 *  - `targets: Set<KotlinTarget>` (populated after matrix-mode wiring),
 *  - `compilations: Map<KotlinTarget, KotlinCompilation<*>>` (same).
 */
abstract class KmpFlavorVariant @Inject constructor(private val variantName: String) : Named {

    override fun getName(): String = variantName

    /**
     * The flavor names that compose this variant, in dimension-priority order
     * (e.g. `["free", "dev"]` for the `freeDev` variant).
     */
    var flavors: List<String> = emptyList()
        internal set

    /**
     * The build type name (e.g. `"debug"`), or `null` when build types
     * are not enabled.
     */
    var buildType: String? = null
        internal set

    /**
     * The KMP targets this variant compiles on. Populated AFTER
     * `CompilationRegistrar.register()` runs, so reads before then
     * return an empty set. Use `configureEach { … }` (not eager `forEach`)
     * to consume this safely.
     */
    var targets: Set<KotlinTarget> = emptySet()
        internal set

    /**
     * The variant's `KotlinCompilation` instance per target. Same
     * lazy-population caveat as [targets].
     */
    var compilations: Map<KotlinTarget, KotlinCompilation<*>> = emptyMap()
        internal set

    /**
     * v2.2 Phase 1A — intermediate source sets this variant transitively `dependsOn`,
     * populated when `kmpFlavors.createIntermediateBuildTypeSourceSets = true` AND the
     * module has registered build types. Each entry is a `common{BuildType}` source set
     * (e.g. `commonStaging`) shared between sibling variants on the same build type.
     *
     * Empty when build-type-axis intermediate source sets are not opted in.
     */
    var intermediateSourceSets: List<KotlinSourceSet> = emptyList()
        internal set
}
