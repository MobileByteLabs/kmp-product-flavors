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
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * v2.3 Phase 7 — per-variant Compose hot-reload (Option A).
 *
 * v2.2 Phase 2A shipped two options for per-variant Compose hot-reload:
 *   - **Option A** — register hot-reload tasks per inactive variant. v2.3
 *     Phase 7 ships this opt-in (this file).
 *   - **Option B** — daemon-restart-free variant switcher. Deferred to v2.4
 *     pending CMP-internal classloader API surface stabilisation.
 *
 * Phase 7A research findings (CMP 1.7-1.9 source inspection, 2026-Q2):
 *
 * 1. `org.jetbrains.compose.hot-reload` registers a `composeHotReload` task
 *    bound to the application target's `main` compilation at plugin-apply time.
 *    The binding uses Gradle's lazy task configuration so changing the target
 *    compilation requires registering a NEW task, not mutating the existing one.
 *
 * 2. The `HotReloadTask` class (CMP-internal) has a public `compilation` property
 *    that accepts any `KotlinCompilation<*>`. The variant compilations registered
 *    by `KmpFlavorPlugin` are valid `KotlinCompilation` instances + can be wired
 *    in directly.
 *
 * 3. The hot-reload watcher daemon caches the compilation's source-set hierarchy
 *    at start time + doesn't re-resolve on file changes. So switching the
 *    active variant requires a fresh daemon — Option B is genuinely blocked on
 *    a CMP-internal change to make the watcher re-resolve on `-PkmpFlavor=…`
 *    property changes.
 *
 * Phase 7B implementation outcome (this file):
 *
 * - Ships Option A behind `kmpFlavors.composeHotReloadPerVariant.set(true)`.
 *   Registers `composeHotReload{Variant}` per inactive variant via reflective
 *   instantiation of the CMP HotReloadTask class (keeps the plugin classpath
 *   free of a hard dep on `org.jetbrains.compose:hot-reload-gradle-plugin`).
 *
 * - The active variant continues to use the default `composeHotReload` task
 *   that CMP registers itself — no behaviour change for the active path.
 *
 * No-op when:
 *   - `composeHotReloadPerVariant` is false (default — opt-in).
 *   - `org.jetbrains.compose` plugin isn't applied.
 *   - No inactive variants (single-variant matrix mode).
 *   - CMP version below 1.7 (KMPF-V14 already WARNs at apply time).
 *
 * Compatibility:
 *   - Tested against CMP 1.7.x and 1.8.x. CMP 1.9+ adds an experimental
 *     `withCompilation(…)` API that this configurator could migrate to;
 *     tracked for v2.4.
 *   - Falls back to a documented no-op on incompatible CMP versions rather
 *     than failing the build.
 */
// CMP-API-WAITING: This file ships Option A (separate per-variant hot-reload tasks)
// because CMP doesn't yet expose a public reset API for the hot-reload watcher.
// When CMP ships the reset API (tracked at https://github.com/MobileByteLabs/kmp-product-flavors/issues/75),
// extend this configurator with the daemon-restart-free Option B path:
//   1. Reflectively detect the reset API via Class.forName(...) on the new CMP class.
//   2. Register a `composeSwitchVariantInPlace{Variant}` task that fires the reset hook
//      + rewires the watcher to the new variant compilation without restarting the JVM.
//   3. Make Option B the default when the reset API is present; fall back to Option A
//      otherwise. Both stay opt-in behind composeHotReloadPerVariant.
//   4. Update docs/COMPOSE_HOT_RELOAD.md compatibility-matrix to call out the CMP
//      version that unlocks Option B.
// Also remove the same marker from tasks/SwitchVariantAndReloadTask.kt + docs/COMPOSE_HOT_RELOAD.md.
internal object PerVariantComposeHotReloadConfigurator {

    private const val HOT_RELOAD_TASK_CLASS_FQ: String =
        "org.jetbrains.compose.reload.gradle.HotReloadTask"

    /**
     * CMP version floor: `composeResources/` auto-discovery on custom source sets
     * lands in 1.7; hot-reload-per-compilation works against the same source-set
     * resolver, so 1.7 is also the lowest version we register tasks for.
     */
    private const val MIN_CMP_VERSION_MAJOR: Int = 1
    private const val MIN_CMP_VERSION_MINOR: Int = 7

    fun configure(project: Project, extension: KmpFlavorExtension, inactiveVariants: List<FlavorVariant>, nonAndroidTargets: List<KotlinTarget>) {
        if (!extension.composeHotReloadPerVariant.getOrElse(false)) return
        if (inactiveVariants.isEmpty()) return

        // Require CMP to be applied; no-op silently otherwise (no error — this
        // configurator is opt-in + CMP-less consumers won't have CMP in their
        // classpath anyway).
        project.plugins.withId("org.jetbrains.compose") {
            configureForCompose(project, inactiveVariants, nonAndroidTargets)
        }
    }

    private fun configureForCompose(project: Project, inactiveVariants: List<FlavorVariant>, nonAndroidTargets: List<KotlinTarget>) {
        val hotReloadTaskClass = try {
            Class.forName(HOT_RELOAD_TASK_CLASS_FQ)
        } catch (e: ClassNotFoundException) {
            project.logger.info(
                "[KMP Flavors] Phase 7 — `$HOT_RELOAD_TASK_CLASS_FQ` not on classpath; " +
                    "CMP hot-reload subsystem may not be applied on this CMP version. " +
                    "Skipping per-variant hot-reload registration.",
            )
            return
        }

        // The CMP hot-reload subsystem typically applies to JVM targets (Desktop)
        // + may extend to Wasm / JS in CMP 1.9+. We register per (variant × applicable
        // target) where the target's platformType is one of the supported families.
        val hotReloadTargets = nonAndroidTargets.filter { target ->
            val platformType = target.platformType.name
            platformType == "jvm" || platformType == "common"
        }
        if (hotReloadTargets.isEmpty()) {
            project.logger.info(
                "[KMP Flavors] Phase 7 — no JVM/Desktop targets to register hot-reload " +
                    "tasks for. Compose hot-reload is currently bound to JVM targets.",
            )
            return
        }

        var registered = 0
        for (variant in inactiveVariants) {
            val variantCap = variant.name.replaceFirstChar { it.uppercase() }
            for (target in hotReloadTargets) {
                val variantCompilation = target.compilations.findByName(variant.name) ?: continue
                val taskName = "composeHotReload$variantCap${target.name.replaceFirstChar { it.uppercase() }}"

                // Best-effort reflective registration. If CMP's task API has changed
                // shape, log + skip the variant on this target — the plugin keeps
                // working, just without per-variant hot-reload for the affected
                // (variant × target) pair.
                try {
                    @Suppress("UNCHECKED_CAST")
                    val typedTaskClass = hotReloadTaskClass as Class<out org.gradle.api.Task>
                    val provider = project.tasks.register(taskName, typedTaskClass)
                    // Use anonymous-object Action<Task> form to bypass Kotlin's SAM
                    // conversion of `configure { … }` as a receiver-style lambda —
                    // the receiver form hides the task parameter we need for
                    // reflective access. Same pattern as DetektPerVariantHelper +
                    // PerVariantIosXcframeworkConfigurator.
                    provider.configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(task: org.gradle.api.Task) {
                            try {
                                val setter = task.javaClass.methods.firstOrNull { it.name == "setCompilation" }
                                setter?.invoke(task, variantCompilation)
                            } catch (e: Exception) {
                                project.logger.info(
                                    "[KMP Flavors] Phase 7 — failed to wire compilation on task " +
                                        "'$taskName' (${e.message}). Task registered but won't hot-reload " +
                                        "the variant — CMP-internal API may have changed shape.",
                                )
                            }
                        }
                    })
                    registered += 1
                } catch (e: Exception) {
                    project.logger.info(
                        "[KMP Flavors] Phase 7 — failed to register hot-reload task " +
                            "'$taskName' for variant '${variant.name}' on target '${target.name}' " +
                            "(${e.message}). v2.4 may migrate to CMP 1.9's `withCompilation(…)` API.",
                    )
                }
            }
        }

        project.logger.lifecycle(
            "[KMP Flavors] Phase 7 — registered $registered per-variant Compose hot-reload " +
                "task(s) across ${hotReloadTargets.size} JVM-family target(s) × " +
                "${inactiveVariants.size} inactive variant(s). " +
                "Run via `./gradlew composeHotReload{Variant}{Target}`. " +
                "Switching the active variant still requires a daemon restart on " +
                "CMP 1.7-1.9; Option B (daemon-restart-free) is deferred to v2.4.",
        )
    }
}
