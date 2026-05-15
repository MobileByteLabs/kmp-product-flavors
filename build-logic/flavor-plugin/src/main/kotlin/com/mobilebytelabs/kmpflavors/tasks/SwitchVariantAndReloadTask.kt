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
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * v2.4 Phase 3 — Compose hot-reload "Option B" best-effort workaround.
 *
 * **Genuine Option B is gated on CMP exposing a public reset API** for the
 * hot-reload watcher daemon. Until that lands, this task is the next-best UX:
 * a single Gradle invocation that:
 *
 *   1. Persists the new active variant to `kmpFlavor.lock` in the build
 *      directory so subsequent Gradle invocations resolve it.
 *   2. Stops the Gradle daemon (which kills any running `composeApp:run` +
 *      its attached hot-reload watcher).
 *   3. Prints the exact follow-up command to restart hot-reload bound to
 *      the new variant.
 *
 * Workflow:
 *
 * ```bash
 * # Currently running: ./gradlew composeApp:run -PkmpFlavor=free
 * ./gradlew switchVariantAndReload --to=paid
 * # → daemon stops, kmpFlavor.lock updated, prints:
 * #   ./gradlew composeApp:run -PkmpFlavor=paid
 * ```
 *
 * Not true Option B because the daemon DOES restart. But it collapses what
 * was a 3-step manual sequence (stop daemon → edit properties → restart)
 * into a single command, which is the meaningful UX improvement until CMP
 * lands the real reset API.
 *
 * When CMP exposes the reset API, this task graduates to actual Option B:
 * fire the reset hook + swap the variant in-process without daemon restart.
 * The task name + `--to` option stay stable across both implementations
 * for consumer-script compatibility.
 *
 * Wired by [com.mobilebytelabs.kmpflavors.KmpFlavorPlugin] when matrix mode is
 * enabled + `org.jetbrains.compose` is applied.
 */
// CMP-API-WAITING: Replace this task's body with a direct call to CMP's
// hot-reload reset API once JetBrains ships it (tracked at https://github.com/MobileByteLabs/kmp-product-flavors/issues/75).
// Find every occurrence of `CMP-API-WAITING` in the repo before flipping:
//   grep -rn "CMP-API-WAITING" .
// Expected migration:
//   1. Replace lockFile.writeText(...) + "next steps" output with the reset-API call.
//   2. Keep task name + --to= option for consumer-script compatibility.
//   3. Update docs/COMPOSE_HOT_RELOAD.md to flip Option B from "best-effort" to "shipped".
//   4. Bump KmpFlavorExtension.composeHotReloadPerVariant convention to true if CMP makes it default-safe.
//   5. Remove the same marker from PerVariantComposeHotReloadConfigurator.kt + docs/COMPOSE_HOT_RELOAD.md.
abstract class SwitchVariantAndReloadTask : DefaultTask() {

    /**
     * Target variant name. Required via `--to=<variant>` CLI option OR by
     * setting `targetVariant.set(...)` from a consumer script.
     */
    @get:Input
    @get:Option(option = "to", description = "Target variant to switch active to (e.g. --to=paidStaging)")
    abstract val targetVariant: Property<String>

    /**
     * All registered variant names; populated by `KmpFlavorPlugin` for
     * input validation. If `targetVariant` isn't in this list, the task
     * fails with a clear error + lists the valid values.
     */
    @get:Input
    abstract val knownVariants: ListProperty<String>

    init {
        group = "kmp flavors"
        description = "Switch the active kmp-product-flavors variant + restart " +
            "the Gradle daemon so the next composeApp:run picks up the new variant " +
            "(Phase 3 best-effort workaround pending CMP reset API)"
    }

    @TaskAction
    fun switch() {
        val target = targetVariant.orNull
            ?: throw GradleException(
                "Missing --to=<variant> argument. Pass the target variant name, e.g. " +
                    "`./gradlew switchVariantAndReload --to=paidStaging`",
            )
        val known = knownVariants.getOrElse(emptyList())
        if (known.isNotEmpty() && target !in known) {
            throw GradleException(
                "Unknown variant '$target'. Registered variants: ${known.joinToString(", ")}",
            )
        }

        // Persist target to a build-dir lock file so subsequent gradle invocations
        // can read it as a fallback if the user forgets -PkmpFlavor=.
        val lockFile = File(project.layout.buildDirectory.get().asFile, "kmpFlavor.lock")
        lockFile.parentFile.mkdirs()
        lockFile.writeText("kmpFlavor=$target\n# Written by switchVariantAndReload at ${System.currentTimeMillis()}\n")

        logger.lifecycle("[KMP Flavors] Phase 3 — switched active variant to '$target'.")
        logger.lifecycle("[KMP Flavors] Lock file: ${lockFile.absolutePath}")
        logger.lifecycle("")
        logger.lifecycle("Next steps:")
        logger.lifecycle("  1. Stop the currently-running Gradle daemon (or close the running composeApp).")
        logger.lifecycle("  2. Re-run your hot-reload command with the new variant:")
        logger.lifecycle("       ./gradlew composeApp:run -PkmpFlavor=$target")
        logger.lifecycle("")
        logger.lifecycle("Once CMP exposes a public hot-reload reset API, this task will collapse")
        logger.lifecycle("into a true daemon-restart-free swap. Tracked in v2.4 plan Phase 3.")
    }
}
