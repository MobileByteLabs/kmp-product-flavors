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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * RFC §3 Q26 — `./gradlew kmpFlavorsMigrateToV2` migration assistant.
 *
 * No-op safe: never modifies the project. Inspects the resolved plugin
 * configuration and prints a per-project report of what (if anything)
 * needs to change to land on v2.0 matrix mode. The report is informational
 * — v2.0 is fully additive, so the "migration" is at most a one-line
 * `gradle.properties` change.
 *
 * Output modes:
 *   - default — human-readable Markdown to stdout (`./gradlew kmpFlavorsMigrateToV2`)
 *   - `--dry-run` — same as default (the task is read-only either way; flag
 *     accepted for ergonomics + matches the RFC's documented invocation)
 *   - `--json` — single-line JSON object (CI-friendly)
 */
abstract class KmpFlavorsMigrateToV2Task : DefaultTask() {

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val flavorCount: Property<Int>

    @get:Input
    abstract val buildTypeCount: Property<Int>

    @get:Input
    abstract val matrixModeEnabled: Property<Boolean>

    @get:Input
    abstract val publishMatrixEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val activeVariantName: Property<String>

    @get:Input
    @get:Optional
    abstract val targetCount: Property<Int>

    @get:Input
    abstract val jsonOutput: Property<Boolean>

    /**
     * `--json` flag. Switches output to a single-line JSON object for
     * machine consumption (CI grep / structured tooling).
     *
     * Note on `--dry-run` flag from the RFC: the task is **always**
     * read-only and never modifies the project, so Gradle's built-in
     * `--dry-run` flag is unnecessary here — and passing it at the CLI
     * makes Gradle SKIP the task action entirely (the built-in flag
     * means "skip every task action this invocation"). The canonical
     * invocation is just `./gradlew kmpFlavorsMigrateToV2`.
     */
    @Option(option = "json", description = "Emit a single-line JSON report instead of Markdown.")
    fun setJson(json: Boolean) {
        jsonOutput.set(json)
    }

    @TaskAction
    fun run() {
        val version = pluginVersion.get()
        val matrix = matrixModeEnabled.get()
        val publish = publishMatrixEnabled.getOrElse(false)
        val flavors = flavorCount.get()
        val buildTypes = buildTypeCount.get()
        val variant = activeVariantName.getOrElse("(none)")
        val targets = targetCount.getOrElse(0)
        val recs = recommendations(matrix, publish, flavors)
        val ready = recs.none { it.startsWith("BLOCK") }

        if (jsonOutput.getOrElse(false)) {
            // Single-line JSON.
            val recsJson = recs.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
            println(
                """{"pluginVersion":"$version","matrixModeEnabled":$matrix,""" +
                    """"publishMatrixEnabled":$publish,"flavors":$flavors,"buildTypes":$buildTypes,""" +
                    """"activeVariant":"$variant","nonAndroidTargets":$targets,""" +
                    """"ready":$ready,"recommendations":[$recsJson]}""",
            )
            return
        }

        // Human-readable Markdown report
        val out = buildString {
            appendLine("# kmpFlavorsMigrateToV2 report — ${projectName.get()}")
            appendLine()
            appendLine("| Field | Value |")
            appendLine("|---|---|")
            appendLine("| Plugin version | $version |")
            appendLine("| Flavors registered | $flavors |")
            appendLine("| Build types registered | $buildTypes |")
            appendLine("| Active variant | $variant |")
            appendLine("| Non-Android KMP targets | $targets |")
            appendLine("| Matrix mode enabled | $matrix |")
            appendLine("| publishMatrix enabled | $publish |")
            appendLine()
            appendLine("## Recommendations")
            appendLine()
            if (recs.isEmpty()) {
                appendLine("Nothing to do. This project is already on v2.0 matrix mode.")
            } else {
                recs.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("See `docs/MIGRATION_v1_to_v2.md` for the full upgrade guide.")
        }
        println(out)
    }

    private fun recommendations(
        matrix: Boolean,
        publish: Boolean,
        flavors: Int,
    ): List<String> = buildList {
        if (flavors == 0) {
            add(
                "INFO: zero flavors registered. v2.0 is a drop-in upgrade — bump the " +
                    "plugin version pin; no other changes needed. Matrix mode has " +
                    "nothing to do here.",
            )
            return@buildList
        }
        if (!matrix) {
            add(
                "OPT-IN: matrix mode is OFF. v2.0 is fully back-compat — your build " +
                    "behaves identically to v1.x. To enable matrix mode (build every " +
                    "variant × target in one Gradle invocation), add ONE of:",
            )
            add("    a) `gradle.properties: kmpFlavors.buildMatrix=true`")
            add(
                "    b) `kmpFlavors { buildMatrix.set(true) }` in your convention plugin",
            )
        } else {
            add(
                "READY: matrix mode is ON. Run `./gradlew :${projectName.get()}:assembleAllVariants` " +
                    "to build every variant × non-Android target in one invocation.",
            )
        }
        if (matrix && !publish) {
            add(
                "OPTIONAL: enable per-variant Maven publications via " +
                    "`publishMatrix.set(true)` + applying the `maven-publish` plugin. " +
                    "See `docs/MATRIX_MODE.md` § Q21-D.",
            )
        }
    }
}
