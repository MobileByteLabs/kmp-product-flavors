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

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * v2.2 Phase 2A (Option B) — print the currently active variant.
 *
 * The v2.2 plan documented two options for "Compose hot-reload per variant":
 *   - **Option A** — plugin-side change: register hot-reload tasks per variant.
 *     Requires deep investigation of `org.jetbrains.compose.hot-reload` task
 *     graph internals; deferred to v2.3+.
 *   - **Option B** — documented honest "still active-only": ship a CLI helper
 *     so developers can quickly identify and switch the active variant via
 *     `-PkmpFlavor=<name>` without restarting Gradle.
 *
 * This task is Option B. It prints:
 *   1. The currently-active variant name.
 *   2. The full list of registered variants (so consumers know what they can
 *      switch to).
 *   3. The exact CLI flag to switch variants
 *      (`-PkmpFlavor={variantName}`).
 *
 * Combined with the v2.1 Phase 4 `generateVariantRunConfigurations` task,
 * developers get a workable hot-reload UX: switch the active variant via
 * the `-PkmpFlavor` flag in a generated `.run.xml`, then re-run
 * `composeApp:run` to restart Compose against the new active variant.
 *
 * Per-variant Compose hot-reload (without restarting Gradle) remains a
 * documented limitation. The Q24 adjacent-plugin compat row in
 * `docs/MATRIX_MODE.md` is honest about it.
 */
abstract class ListActiveVariantTask : DefaultTask() {

    /**
     * The currently-active variant name. Wired from `KmpFlavorPlugin.apply()`'s
     * `activeVariantResolved`.
     */
    @get:Input
    abstract val activeVariantName: Property<String>

    /**
     * All registered variant names, sorted in registration order.
     */
    @get:Input
    abstract val allVariantNames: ListProperty<String>

    init {
        group = "kmp flavors"
        description = "Prints the currently-active variant + switch instructions (v2.2 Phase 2A Option B)"
    }

    @TaskAction
    fun list() {
        val active = activeVariantName.get()
        val all = allVariantNames.get()

        println()
        println("KMP Flavors — active variant")
        println("=".repeat(50))
        println("Active : $active")
        println("All    : ${if (all.isEmpty()) "(no variants registered)" else all.joinToString(", ")}")
        println()
        println("Switch the active variant by running:")
        println("  ./gradlew <task> -PkmpFlavor=<variantName>")
        println()
        println("Example:")
        all.firstOrNull { it != active }?.let { exampleVariant ->
            println("  ./gradlew :module:compileKotlinDesktop -PkmpFlavor=$exampleVariant")
        }
        println()
        println("Per-variant Compose hot-reload (without restarting Gradle) is a documented")
        println("limitation in v2.2 — see docs/MATRIX_MODE.md Q24 row for the rationale.")
        println()
    }
}
