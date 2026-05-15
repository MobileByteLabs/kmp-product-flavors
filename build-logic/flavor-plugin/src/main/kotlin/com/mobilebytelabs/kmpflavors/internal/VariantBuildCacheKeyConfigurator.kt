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

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

/**
 * v2.3 Phase 2 — variant-scoped Gradle build cache namespacing.
 *
 * **Status: ships as a no-op pending telemetry (v2.3 plan Phase 2 path-(a)).**
 *
 * ## Problem statement
 *
 * Gradle's build cache key for `compileKotlin{Target}` includes the source-set
 * fingerprint. With matrix mode, every variant's compilation pulls in
 * `commonMain + that variant's per-flavor common source sets`. When a paid-only
 * file changes, `commonMain` stays identical but the paid variant's cache key
 * invalidates correctly. The hypothesised risk is at **scale**: a 50-variant
 * module on a multi-target KMP project may push the cache key space past
 * Gradle's default 10K-entry limit, causing eviction → cache-miss explosion.
 *
 * ## Why this is a stub
 *
 * The v2.3 plan's acceptance gate splits two paths:
 *   - **(a)** v2.2 / v2.3 Build Scan data shows acceptable cache-hit rates
 *     on 8+ variant modules → ship a documented no-op + the
 *     [KmpFlavorExtension.variantCacheNamespacing] property so consumers
 *     can opt-in forward-compat. This is the path the v2.3 cycle is on.
 *   - **(b)** Real cache-miss data → ship a [Task.Inputs] hook that injects
 *     variant-scoped namespacing into the cache key. Flip the convention
 *     to `true` in v2.3.x or v2.4. Tracked for future iteration.
 *
 * Until path (b) data arrives, this configurator emits an INFO-level log
 * line if the property is set to `true` so consumers can verify their
 * opt-in is recognised, but does NOT modify any task inputs.
 *
 * ## Why ship the stub now vs wait for path (b)
 *
 * Forward-compatibility for adopters. Adding the property in a future
 * minor release would be a DSL break for consumers who already
 * `kmpFlavors.variantCacheNamespacing.set(true)` based on this stub +
 * its KDoc. Exposing it now lets adopters wire the opt-in flag in their
 * convention plugins ahead of the path-(b) implementation.
 *
 * ## Implementation notes for path-(b)
 *
 * The future implementation would:
 *   1. Find all `compileKotlin{Variant}{Target}` tasks via
 *      `project.tasks.matching { it.name.startsWith("compileKotlin") }`.
 *   2. On each task, add a `@Input` property containing the variant name.
 *      The variant name participates in the task's input fingerprint,
 *      which Gradle hashes into the cache key. Result: same compilation
 *      output but variant-scoped cache buckets.
 *   3. Optional fan-out: split the cache key space into N buckets per
 *      variant so each variant's cache evictions don't cascade across
 *      sibling variants on the same JVM.
 *
 * Custom cache-key injection is a brittle Gradle internal — KGP version
 * compat may break the hook. Path-(b) ships with an opt-in flag preserved
 * (consumers can flip back to `variantCacheNamespacing.set(false)`).
 */
internal object VariantBuildCacheKeyConfigurator {

    /**
     * Apply path-(a) stub behaviour: log only, no task modifications.
     *
     * @param project Gradle project to inspect for opt-in state.
     * @param extension Plugin extension carrying [KmpFlavorExtension.variantCacheNamespacing].
     */
    fun configure(project: Project, extension: KmpFlavorExtension) {
        if (!extension.variantCacheNamespacing.getOrElse(false)) return

        project.logger.info(
            "[KMP Flavors] Phase 2 stub — `variantCacheNamespacing=true` recognised. " +
                "Implementation is a no-op pending real cache-miss telemetry on 8+ variant " +
                "modules (v2.3 plan Phase 2 path-(b) trigger). Track via Build Scan cache-hit " +
                "rates; flip in a future v2.3.x / v2.4 once data justifies it.",
        )
    }
}
