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

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * v2.2 Phase 4B — feature-flag integration top-level scope.
 *
 * Three opt-in nested platforms — GrowthBook, Statsig, LaunchDarkly. Each
 * generates a per-variant `FeatureFlags` Kotlin object alongside `BuildKonfig.kt`
 * that the consumer's flag-platform SDK can read at runtime for variant-aware
 * A/B-testing defaults.
 *
 * Consumer DSL:
 * ```kotlin
 * kmpFlavors {
 *     featureFlags {
 *         growthbook { defaultPayload.set(file("flags/growthbook.json")) }
 *         // statsig { defaultPayload.set(file("flags/statsig.json")) }
 *         // launchDarkly { defaultPayload.set(file("flags/launchDarkly.json")) }
 *     }
 * }
 * ```
 *
 * Each platform's `defaultPayload` is a `RegularFile` pointing at a JSON file
 * with the flag defaults. The configurator reads the JSON at config time + emits
 * a per-variant `FeatureFlags.kt` with the values embedded as `const val`s
 * (where the JSON values are scalar Kotlin literals) or `val`s (where they're
 * objects / lists, requiring runtime parsing).
 */
abstract class FeatureFlagsConfig @Inject constructor(objects: ObjectFactory) {

    /**
     * GrowthBook integration. Set `defaultPayload` to enable.
     */
    val growthbook: GrowthBookConfig = objects.newInstance(GrowthBookConfig::class.java)

    /**
     * Statsig integration. Set `defaultPayload` to enable.
     */
    val statsig: StatsigConfig = objects.newInstance(StatsigConfig::class.java)

    /**
     * LaunchDarkly integration. Set `defaultPayload` to enable.
     */
    val launchDarkly: LaunchDarklyConfig = objects.newInstance(LaunchDarklyConfig::class.java)

    fun growthbook(action: Action<GrowthBookConfig>) {
        action.execute(growthbook)
    }

    fun statsig(action: Action<StatsigConfig>) {
        action.execute(statsig)
    }

    fun launchDarkly(action: Action<LaunchDarklyConfig>) {
        action.execute(launchDarkly)
    }
}

/**
 * v2.2 Phase 4B — GrowthBook platform config.
 *
 * `defaultPayload` should point at a JSON file shaped like
 * `{ "feature_key": "default_value", … }`. The configurator generates one
 * `FeatureFlags.growthbook: Map<String, Any?>` constant per variant by walking
 * the JSON; per-variant overrides come from optional sibling files named
 * `flags/growthbook.<variantName>.json` (e.g., `flags/growthbook.paid.json`).
 */
abstract class GrowthBookConfig {
    abstract val defaultPayload: RegularFileProperty
    abstract val enabled: Property<Boolean>

    init {
        enabled.convention(false)
    }
}

/**
 * v2.2 Phase 4B — Statsig platform config. Same shape as GrowthBookConfig;
 * different JSON conventions documented in [docs/PUBLISHING.md].
 */
abstract class StatsigConfig {
    abstract val defaultPayload: RegularFileProperty
    abstract val enabled: Property<Boolean>

    init {
        enabled.convention(false)
    }
}

/**
 * v2.2 Phase 4B — LaunchDarkly platform config. Same shape as GrowthBookConfig;
 * different JSON conventions documented in [docs/PUBLISHING.md].
 */
abstract class LaunchDarklyConfig {
    abstract val defaultPayload: RegularFileProperty
    abstract val enabled: Property<Boolean>

    init {
        enabled.convention(false)
    }
}
