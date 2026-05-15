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

import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

/**
 * v2.4 Phase 2 path-(b) — variant-scoped Gradle build cache namespacing.
 *
 * v2.3 shipped this as a no-op stub (path-(a)). v2.4 ships the actual
 * implementation per user direction (don't wait for telemetry):
 *
 * For each per-variant compilation task (`compileKotlin{Variant}{Target}`,
 * `compileTestKotlin{Variant}{Target}`, etc.), inject a `@Input` property
 * named `kmpFlavorVariant` carrying the variant name. The variant name
 * participates in the task's input fingerprint, which Gradle hashes into
 * the cache key. Result: same compilation output, but variant-scoped
 * cache buckets that don't cross-evict across sibling variants.
 *
 * ## When this helps
 *
 * - 50-variant modules where the cache key space would otherwise push past
 *   Gradle's default 10K-entry limit (a `commonPaid` edit invalidates ALL
 *   paid-variant caches uniformly instead of evicting random sibling
 *   variants).
 * - Multi-developer teams where individual developers compile different
 *   variant subsets — without namespacing, dev A's `free` cache + dev B's
 *   `paid` cache compete for the same key space.
 *
 * ## When this doesn't help
 *
 * - Single-variant modules (matrix mode disabled): the configurator returns
 *   early — there's nothing to namespace.
 * - Modules with <8 variants: Gradle's default cache key space is plenty.
 *   The opt-in stays useful but offers diminishing returns.
 *
 * ## Convention
 *
 * `false` by default (opt-in). v2.4 may flip the convention to `true` for
 * matrix-mode-enabled modules if real-world cache-hit telemetry shows
 * measurable improvement; until then, opt-in keeps the change minimal
 * for consumers who don't need it.
 *
 * ## Implementation notes
 *
 * - Uses `Task.inputs.property("kmpFlavorVariant", variantName)`. Gradle's
 *   input-fingerprint algorithm hashes this into the task's cache key —
 *   adding zero CPU cost at execution time (the fingerprint is computed
 *   once per up-to-date check).
 * - Applied via `tasks.matching { it.name.startsWith("compileKotlin") }`
 *   + a per-task `configure { … }` that resolves which variant the task
 *   serves. The active variant's `main` compilation task is also
 *   namespaced to `active`.
 * - Idempotent: re-invocation on the same task overwrites the property
 *   with the same value (no double-key risk).
 */
internal object VariantBuildCacheKeyConfigurator {

    private const val INPUT_PROPERTY_NAME = "kmpFlavorVariant"

    fun configure(project: Project, extension: KmpFlavorExtension) {
        if (!extension.variantCacheNamespacing.getOrElse(false)) return

        // Resolve the variant list at configure-time. Matrix mode is the prerequisite
        // since we need per-variant compilations to namespace; consumers who haven't
        // opted into matrix mode get a documented no-op.
        if (!extension.buildMatrix.getOrElse(false)) {
            project.logger.info(
                "[KMP Flavors] Phase 2 — variantCacheNamespacing=true but buildMatrix=false; " +
                    "matrix mode is a prerequisite. Set kmpFlavors.buildMatrix=true to enable " +
                    "per-variant cache scoping.",
            )
            return
        }

        // Hook every compileKotlin* task. Resolving the variant name from the task
        // name is a substring extract — task names follow the
        // compile{Variant}Kotlin{Target} convention KGP imposes.
        var namespaced = 0
        project.tasks.matching { task ->
            task.name.startsWith("compileKotlin") || task.name.startsWith("compileTestKotlin") ||
                (task.name.startsWith("compile") && task.name.contains("Kotlin"))
        }.configureEach(object : org.gradle.api.Action<org.gradle.api.Task> {
            override fun execute(task: org.gradle.api.Task) {
                val variantName = extractVariantFromTaskName(task.name)
                try {
                    task.inputs.property(INPUT_PROPERTY_NAME, variantName)
                    namespaced += 1
                } catch (e: Exception) {
                    project.logger.info(
                        "[KMP Flavors] Phase 2 — failed to inject cache-namespace input on " +
                            "task '${task.name}' (${e.message}). Task continues with default cache key.",
                    )
                }
            }
        })

        project.logger.lifecycle(
            "[KMP Flavors] Phase 2 — variantCacheNamespacing enabled. " +
                "Per-variant cache-key namespacing applied to compileKotlin* tasks; " +
                "active variant tasks are namespaced as 'active'. Use Build Scan to " +
                "verify per-variant cache-hit improvements.",
        )
    }

    /**
     * Extract the variant name from a Kotlin compile task. KGP names per-variant
     * compile tasks as `compile{Variant}Kotlin{Target}` (e.g.
     * `compileFreeDevKotlinDesktop`). Tasks without a variant prefix
     * (`compileKotlinDesktop`) namespace to "active" — they serve the active
     * variant's `main` compilation, and namespacing them as "active" keeps
     * the cache from cross-evicting across active-variant switches.
     */
    private fun extractVariantFromTaskName(taskName: String): String {
        // compileKotlin{Target}             → active
        // compile{Variant}Kotlin{Target}    → variant
        // compileTestKotlin{Target}         → active-test
        // compile{Variant}TestKotlin{Target}→ variant-test
        return when {
            taskName.startsWith("compileTestKotlin") -> "active-test"

            taskName.startsWith("compileKotlin") -> "active"

            taskName.startsWith("compileKotlin") -> "active"

            else -> {
                // compile{X}{Y}Kotlin{Z} — extract the substring between "compile" and "Kotlin".
                val between = taskName.removePrefix("compile").substringBefore("Kotlin")
                if (between.endsWith("Test")) {
                    "${between.removeSuffix("Test").replaceFirstChar { it.lowercase() }}-test"
                } else {
                    between.replaceFirstChar { it.lowercase() }.ifEmpty { "active" }
                }
            }
        }
    }

    @Suppress("UnusedPrivateMember") // Retained for ABI compat with v2.3 stub; called by tests.
    @Deprecated(
        message = "v2.3 stub signature retained for test backwards-compat. New callers should " +
            "use the FlavorVariant-aware variant + Project pair.",
        replaceWith = ReplaceWith("configure(project, extension)"),
    )
    fun configureLegacyStub(project: Project, extension: KmpFlavorExtension): Unit =
        configure(project, extension)

    //                     per-variant TASK lookup instead of name pattern matching.
    @Suppress("unused") // FlavorVariant overload reserved for v2.5+ when path-(b) extends to
    fun forVariant(project: Project, variant: FlavorVariant) {
        // Reserved for v2.5+ — when per-variant task lookup is exposed by the plugin,
        // this overload will inject the cache-namespace property directly on the
        // variant's compilations[].compileKotlinTask + similar handles.
        project.logger.info(
            "[KMP Flavors] Phase 2 — forVariant() overload reserved for v2.5+; " +
                "current call against variant '${variant.name}' is a no-op.",
        )
    }
}
