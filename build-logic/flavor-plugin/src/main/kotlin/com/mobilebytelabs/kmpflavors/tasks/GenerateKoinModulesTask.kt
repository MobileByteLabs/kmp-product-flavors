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

import com.mobilebytelabs.kmpflavors.KoinModuleSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * v2.6 Phase 3 — generates per-variant Koin module files.
 *
 * Per spec, this task emits two file kinds:
 *
 * 1. **Per-flavor actual val** — one file per [KoinModuleSpec] declared with a
 *    body for this variant's primary flavor. File name is
 *    `${ModuleName}KoinActual.kt`. The flavor is derived from the lowercase
 *    prefix of [variantName] (e.g. `freeDev` → `free`).
 *
 * 2. **commonMain expect-val + flavorDependentModules() aggregator** — a single
 *    `FlavorDependentModules.kt` file with `expect val ${name}Module` for every
 *    declared module plus a `fun flavorDependentModules(): List<Module>` that
 *    concatenates them. Consumers append this list to their `startKoin {}`
 *    modules call (see docs/DI_INTEGRATION.md).
 *
 * String-template codegen (no kotlinpoet dep) keeps the plugin lightweight —
 * same approach as `GenerateBuildConfigTask`.
 */
@CacheableTask
abstract class GenerateKoinModulesTask : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val moduleSpecs: ListProperty<KoinModuleSpec>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val variant = variantName.get()
        val specs = moduleSpecs.get()
        if (specs.isEmpty()) return

        val outputDir = outputDirectory.get().asFile
        val packageDir = File(outputDir, pkg.replace('.', '/'))
        packageDir.mkdirs()

        // Primary flavor — lowercase prefix of variantName ("freeDev" → "free").
        val primaryFlavor = variant.takeWhile { !it.isUpperCase() }

        // 1) Per-flavor actual val files.
        for (spec in specs) {
            val bindingBody = spec.variantBindings[primaryFlavor] ?: continue
            val file = File(packageDir, "${spec.moduleName.cap()}KoinActual.kt")
            file.writeText(buildActualValSource(pkg, spec.moduleName, bindingBody))
        }

        // 2) commonMain expect-val + flavorDependentModules() aggregator.
        File(packageDir, "FlavorDependentModules.kt").writeText(
            buildCommonAggregatorSource(pkg, specs),
        )
    }

    private fun buildActualValSource(pkg: String, moduleName: String, bindingBody: String): String =
        buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import org.koin.core.module.Module")
            appendLine("import org.koin.dsl.module")
            appendLine()
            appendLine("actual val ${moduleName}Module: Module = module {")
            // Ensure exactly one trailing newline on the binding body so the closing
            // `}` lands on its own line regardless of whether the caller supplied a
            // trailing `\n` (DSL helpers do; raw string inputs in tests may not).
            append(bindingBody.trimEnd('\n'))
            appendLine()
            appendLine("}")
        }

    private fun buildCommonAggregatorSource(pkg: String, specs: List<KoinModuleSpec>): String =
        buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import org.koin.core.module.Module")
            appendLine()
            for (spec in specs) {
                appendLine("expect val ${spec.moduleName}Module: Module")
            }
            appendLine()
            appendLine("fun flavorDependentModules(): List<Module> = listOf(")
            specs.forEachIndexed { idx, spec ->
                val suffix = if (idx == specs.lastIndex) "" else ","
                appendLine("    ${spec.moduleName}Module$suffix")
            }
            appendLine(")")
        }

    private fun String.cap(): String = replaceFirstChar { it.uppercase() }
}
