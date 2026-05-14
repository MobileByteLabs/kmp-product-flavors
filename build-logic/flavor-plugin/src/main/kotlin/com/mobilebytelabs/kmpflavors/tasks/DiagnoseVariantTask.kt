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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * RFC §3 Q22 — `diagnoseVariant` task.
 *
 * Prints the resolved source-set tree, target list, BuildConfig fields, and
 * filter context for ONE variant. Lifesaver for "what did matrix mode
 * actually wire for `freeDev`?".
 *
 * Usage:
 * ```
 * ./gradlew :module:diagnoseVariant --variant freeDev          # human-readable
 * ./gradlew :module:diagnoseVariant --variant freeDev --json   # machine-readable
 * ./gradlew :module:diagnoseVariant                            # active variant
 * ```
 *
 * All inputs are captured at configuration time so the task is
 * configuration-cache-friendly.
 */
abstract class DiagnoseVariantTask : DefaultTask() {

    /**
     * Optional variant name supplied via `--variant <name>`. Falls back to the
     * active variant when omitted.
     */
    @get:Input
    @get:Optional
    @get:Option(option = "variant", description = "The variant name to diagnose (e.g. freeDev). Defaults to the active variant when omitted.")
    abstract val variantToDiagnose: Property<String>

    /**
     * Render output as JSON instead of the human-readable table. Enable with `--json`.
     */
    @get:Input
    @get:Optional
    @get:Option(option = "json", description = "Output as JSON for CI consumption.")
    abstract val jsonOutput: Property<Boolean>

    /**
     * Map of variant name to the list of constituent flavor names.
     */
    @get:Input
    abstract val flavorsByVariant: MapProperty<String, List<String>>

    /**
     * Map of variant name to the build-type name ("" when matrix mode has no build types).
     */
    @get:Input
    abstract val buildTypeByVariant: MapProperty<String, String>

    /**
     * Map of variant name to its compilation's source-set closure (defaultSourceSet
     * + dependsOn-transitive closure), captured at configuration time.
     */
    @get:Input
    abstract val sourceSetsByVariant: MapProperty<String, List<String>>

    /**
     * Map of variant name to the list of KMP target names that have a
     * registered compilation for that variant.
     */
    @get:Input
    abstract val targetsByVariant: MapProperty<String, List<String>>

    /**
     * Map of variant name to a flattened "<field> = <type>::<value>" mapping
     * derived from `flavor.mergedBuildConfigFields`.
     */
    @get:Input
    abstract val buildConfigFieldsByVariant: MapProperty<String, Map<String, String>>

    /**
     * The active variant name (used as the default when `--variant` is omitted).
     */
    @get:Input
    abstract val activeVariantName: Property<String>

    /**
     * Number of variantFilter actions declared on the extension. Surfaced as
     * context — N filters were considered against this variant.
     */
    @get:Input
    abstract val variantFilterCount: Property<Int>

    init {
        group = "kmp flavors"
        description = "Prints resolved source sets, targets, BuildConfig fields, and filter context for one variant"
    }

    @TaskAction
    fun diagnose() {
        val requested = variantToDiagnose.orNull?.takeIf { it.isNotBlank() }
            ?: activeVariantName.get()
        val flavorsMap = flavorsByVariant.get()

        if (requested !in flavorsMap) {
            val available = flavorsMap.keys.sorted().joinToString(", ")
            throw IllegalArgumentException(
                "Unknown variant '$requested'. Registered variants: [$available]. " +
                    "Use `--variant <name>` to pick one, or omit the flag to diagnose the active variant.",
            )
        }

        val flavors = flavorsMap[requested].orEmpty()
        val buildType = buildTypeByVariant.get()[requested].orEmpty()
        val sourceSets = sourceSetsByVariant.get()[requested].orEmpty()
        val targets = targetsByVariant.get()[requested].orEmpty()
        val buildConfigFields = buildConfigFieldsByVariant.get()[requested].orEmpty()
        val isActive = requested == activeVariantName.get()
        val filterCount = variantFilterCount.get()

        if (jsonOutput.getOrElse(false)) {
            renderJson(requested, isActive, flavors, buildType, sourceSets, targets, buildConfigFields, filterCount)
        } else {
            renderHuman(requested, isActive, flavors, buildType, sourceSets, targets, buildConfigFields, filterCount)
        }
    }

    private fun renderHuman(
        variant: String,
        isActive: Boolean,
        flavors: List<String>,
        buildType: String,
        sourceSets: List<String>,
        targets: List<String>,
        buildConfigFields: Map<String, String>,
        filterCount: Int,
    ) {
        val tag = if (isActive) " (ACTIVE)" else ""
        println()
        println("KMPF diagnoseVariant: $variant$tag")
        println("=".repeat(50))
        println("Flavors           : ${if (flavors.isEmpty()) "(none)" else flavors.joinToString(", ")}")
        println("Build type        : ${buildType.ifBlank { "(none)" }}")
        println("Targets           : ${if (targets.isEmpty()) "(no compilation registered)" else targets.joinToString(", ")}")
        println("Variant filters   : $filterCount considered")
        println()
        println("Source-set tree (defaultSourceSet + dependsOn closure):")
        if (sourceSets.isEmpty()) {
            println("  (no source sets — variant has no registered compilation on any target)")
        } else {
            sourceSets.forEach { println("  - $it") }
        }
        println()
        println("BuildConfig fields:")
        if (buildConfigFields.isEmpty()) {
            println("  (none — no buildConfigField(...) declarations contribute to this variant)")
        } else {
            buildConfigFields.entries.sortedBy { it.key }.forEach { (name, typeValue) ->
                println("  - $name : $typeValue")
            }
        }
        println()
    }

    private fun renderJson(
        variant: String,
        isActive: Boolean,
        flavors: List<String>,
        buildType: String,
        sourceSets: List<String>,
        targets: List<String>,
        buildConfigFields: Map<String, String>,
        filterCount: Int,
    ) {
        // Hand-rolled JSON — avoid pulling in a serialization dependency for a single task.
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"variant\":").append(quote(variant)).append(",")
        sb.append("\"active\":").append(isActive).append(",")
        sb.append("\"flavors\":").append(arrayJson(flavors)).append(",")
        sb.append("\"buildType\":").append(if (buildType.isBlank()) "null" else quote(buildType)).append(",")
        sb.append("\"targets\":").append(arrayJson(targets)).append(",")
        sb.append("\"sourceSets\":").append(arrayJson(sourceSets)).append(",")
        sb.append("\"variantFilterCount\":").append(filterCount).append(",")
        sb.append("\"buildConfigFields\":").append(objectJson(buildConfigFields))
        sb.append("}")
        println(sb.toString())
    }

    private fun quote(s: String): String = "\"" + s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t") + "\""

    private fun arrayJson(items: List<String>): String = items.joinToString(prefix = "[", postfix = "]", separator = ",") { quote(it) }

    private fun objectJson(map: Map<String, String>): String = map.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
        "${quote(k)}:${quote(v)}"
    }
}
