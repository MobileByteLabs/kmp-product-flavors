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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * v2.6 Phase 3 — generates per-variant `AnalyticsTags.kt`.
 *
 * Output object:
 *
 * ```kotlin
 * object AnalyticsTags {
 *     const val VARIANT_NAME: String = "freeDev"
 *     const val BUILD_TYPE: String = "debug"
 *     const val ENVIRONMENT: String = "dev"
 *     const val TIER: String = "free"
 *
 *     fun attachTo(target: Any) {
 *         val method = target.javaClass.methods.firstOrNull {
 *             it.name == "setCustomKey" && it.parameterCount == 2
 *         } ?: return
 *         method.invoke(target, "variant_name", VARIANT_NAME)
 *         method.invoke(target, "build_type", BUILD_TYPE)
 *         method.invoke(target, "environment", ENVIRONMENT)
 *         method.invoke(target, "tier", TIER)
 *     }
 * }
 * ```
 *
 * Tags are pre-resolved at configuration time by [com.mobilebytelabs.kmpflavors.internal.PerVariantAnalyticsTagConfigurator]
 * before being frozen into [customTagValues] — no `(FlavorVariant) -> String`
 * closure crosses the configuration-cache boundary.
 */
@CacheableTask
abstract class GenerateAnalyticsTagsTask : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val buildTypeName: Property<String>

    @get:Input
    abstract val customTagValues: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val variant = variantName.get()
        val buildType = buildTypeName.get()
        val customs = customTagValues.get().toSortedMap()

        val outputDir = outputDirectory.get().asFile
        val packageDir = File(outputDir, pkg.replace('.', '/'))
        packageDir.mkdirs()

        File(packageDir, "AnalyticsTags.kt").writeText(
            buildSource(pkg, variant, buildType, customs),
        )
    }

    private fun buildSource(
        pkg: String,
        variant: String,
        buildType: String,
        customs: Map<String, String>,
    ): String = buildString {
        appendLine("package $pkg")
        appendLine()
        appendLine("/** v2.6 auto-generated cross-platform analytics metadata. */")
        appendLine("object AnalyticsTags {")
        appendLine("    const val VARIANT_NAME: String = \"$variant\"")
        appendLine("    const val BUILD_TYPE: String = \"$buildType\"")
        for ((k, v) in customs) {
            appendLine("    const val ${k.uppercase()}: String = \"$v\"")
        }
        appendLine()
        appendLine("    /** Reflectively attaches every tag to a Firebase-Crashlytics-shaped target. */")
        appendLine("    fun attachTo(target: Any) {")
        appendLine("        val method = target.javaClass.methods.firstOrNull {")
        appendLine("            it.name == \"setCustomKey\" && it.parameterCount == 2")
        appendLine("        } ?: return")
        appendLine("        method.invoke(target, \"variant_name\", VARIANT_NAME)")
        appendLine("        method.invoke(target, \"build_type\", BUILD_TYPE)")
        for (k in customs.keys) {
            appendLine("        method.invoke(target, \"$k\", ${k.uppercase()})")
        }
        appendLine("    }")
        appendLine("}")
    }
}
