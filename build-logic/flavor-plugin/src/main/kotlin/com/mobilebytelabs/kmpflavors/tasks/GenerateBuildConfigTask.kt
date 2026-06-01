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

import com.mobilebytelabs.kmpflavors.BuildConfigField
import com.mobilebytelabs.kmpflavors.CustomFieldDeclaration
import com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.Serializable

/**
 * Task that generates a Kotlin BuildConfig object with flavor information.
 *
 * This task is cacheable, meaning Gradle can reuse outputs from previous builds
 * when the inputs haven't changed.
 *
 * Generated file structure:
 * ```kotlin
 * package com.example.app
 *
 * object FlavorConfig {
 *     const val VARIANT_NAME: String = "freeDev"
 *     const val IS_FREE: Boolean = true
 *     const val IS_PAID: Boolean = false
 *     const val IS_DEV: Boolean = true
 *     const val IS_PROD: Boolean = false
 *     // Custom fields from buildConfigField()
 *     const val BASE_URL: String = "https://dev-api.example.com"
 * }
 * ```
 */
@CacheableTask
abstract class GenerateBuildConfigTask : DefaultTask() {

    /**
     * The package name for the generated file.
     * Example: "com.example.app"
     */
    @get:Input
    abstract val packageName: Property<String>

    /**
     * The class name for the generated object.
     * Default: "FlavorConfig"
     */
    @get:Input
    abstract val className: Property<String>

    /**
     * The active variant name.
     * Example: "freeDev"
     */
    @get:Input
    abstract val variantName: Property<String>

    /**
     * Set of all flavor names (for generating IS_<FLAVOR> constants).
     * Example: ["free", "paid", "dev", "prod"]
     */
    @get:Input
    abstract val allFlavorNames: SetProperty<String>

    /**
     * Set of active flavor names (IS_<FLAVOR> = true).
     * Example: ["free", "dev"]
     */
    @get:Input
    abstract val activeFlavorNames: SetProperty<String>

    /**
     * Set of all buildType names (for generating IS_<BUILDTYPE> constants).
     * Empty when no buildTypes are declared.
     * Example: ["debug", "staging", "release"]
     */
    @get:Input
    abstract val allBuildTypeNames: SetProperty<String>

    /**
     * The active buildType name. Empty string when no buildType is active.
     * Example: "debug"
     */
    @get:Input
    abstract val activeBuildTypeName: Property<String>

    /**
     * Custom build config fields merged from active flavors.
     * Key: field name, Value: BuildConfigField(type, name, value)
     */
    @get:Input
    abstract val buildConfigFields: MapProperty<String, BuildConfigField>

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 — BuildKonfig DSL expansion inputs.
    //
    // Threaded by GenerateBuildConfigTasksRegistrar from
    // KmpFlavorExtension.buildKonfigDsl. Default-empty values preserve v2.4
    // task-input contract — v2.4 consumers see no behavior change.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * v2.5 — Dimension enum specifications. One entry per `buildKonfig { enum(dim) }`
     * call. Codegen emits a `sealed class ${DimensionName.capitalize()}` per entry.
     */
    @get:Input
    @get:Optional
    abstract val dimensionEnumSpecs: ListProperty<DimensionEnumSpec>

    /**
     * v2.5 — Custom-type field declarations from `buildKonfig { customField(...) }`.
     * Codegen emits `public val ${name}: ${type} = ${value}`.
     */
    @get:Input
    @get:Optional
    abstract val customFieldSpecs: ListProperty<CustomFieldDeclaration>

    /**
     * v2.5 — Per-target scoped field declarations from `buildKonfig { perTarget(name) {...} }`.
     * Codegen emits a nested `object PerTarget { object {TargetName} { ... } }` block.
     */
    @get:Input
    @get:Optional
    abstract val perTargetFieldSpecs: ListProperty<PerTargetFieldDeclaration>

    /**
     * v2.5 — Vault-integrated secret IDs from `buildKonfig { secret(id) }`.
     * Codegen emits placeholder `const val ${id.toScreamingSnake()}: String = "<unresolved:see-docs>"`
     * for SV15 compliance — actual value resolution semantics deferred to v2.5.x
     * patch per docs/SECRETS_INTEGRATION.md.
     */
    @get:Input
    @get:Optional
    abstract val buildKonfigSecretIds: ListProperty<String>

    /**
     * v2.6 Phase 4 — variant-aware network constants. When set, codegen emits a
     * `BuildKonfig.Network { BASE_URL; TIMEOUT_SECONDS }` block. The URL is
     * selected from [com.mobilebytelabs.kmpflavors.NetworkConfigSpec.baseUrls]
     * by matching the key against the active variant's flavor names.
     * Validation surfaces KMPF-V29 (missing flavor) and KMPF-V30 (no baseUrl
     * for active variant) at extension-evaluation time.
     */
    @get:Input
    @get:Optional
    abstract val networkConfigSpec: Property<com.mobilebytelabs.kmpflavors.NetworkConfigSpec>

    /**
     * Output directory for generated files.
     * Default: build/generated/kmpFlavors/commonMain/kotlin
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "kmp flavors"
        description = "Generates BuildConfig Kotlin object with flavor information"
    }

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val clsName = className.get()
        val variant = variantName.get()
        val allFlavors = allFlavorNames.get()
        val activeFlavors = activeFlavorNames.get()
        val fields = buildConfigFields.get()

        // Create package directory
        val packageDir = pkg.replace('.', File.separatorChar)
        val outputDir = outputDirectory.get().asFile
        val targetDir = File(outputDir, packageDir)
        targetDir.mkdirs()

        // Generate the Kotlin file
        val outputFile = File(targetDir, "$clsName.kt")
        val content = buildString {
            appendLine("/*")
            appendLine(" * Generated by KMP Product Flavors Plugin")
            appendLine(" * DO NOT EDIT - This file is automatically generated")
            appendLine(" */")
            appendLine()
            appendLine("package $pkg")
            appendLine()
            appendLine("/**")
            appendLine(" * Build configuration for the active flavor variant.")
            appendLine(" *")
            appendLine(" * Active variant: $variant")
            appendLine(" * Active flavors: ${activeFlavors.joinToString(", ")}")
            appendLine(" */")
            appendLine("object $clsName {")
            appendLine()

            // Variant name
            appendLine("    /** The active variant name */")
            appendLine("    const val VARIANT_NAME: String = \"$variant\"")
            appendLine()

            // IS_<FLAVOR> constants
            if (allFlavors.isNotEmpty()) {
                appendLine("    // Flavor flags")
                for (flavor in allFlavors.sorted()) {
                    val isActive = flavor in activeFlavors
                    val constName = "IS_${flavor.uppercase()}"
                    appendLine("    const val $constName: Boolean = $isActive")
                }
                appendLine()
            }

            // BUILD_TYPE + IS_<BUILDTYPE> constants
            val activeBuildType = activeBuildTypeName.orNull.orEmpty()
            val allBuildTypes = allBuildTypeNames.get()
            if (allBuildTypes.isNotEmpty()) {
                appendLine("    /** The active buildType name (empty if buildTypes are not enabled) */")
                appendLine("    const val BUILD_TYPE: String = \"$activeBuildType\"")
                appendLine()
                appendLine("    // BuildType flags")
                for (bt in allBuildTypes.sorted()) {
                    val isActive = bt == activeBuildType
                    val constName = "IS_${bt.uppercase()}"
                    appendLine("    const val $constName: Boolean = $isActive")
                }
                appendLine()
            }

            // Custom build config fields
            if (fields.isNotEmpty()) {
                appendLine("    // Custom build config fields")
                for ((_, field) in fields.entries.sortedBy { it.key }) {
                    val typeStr = field.type
                    val nameStr = field.name
                    val valueStr = field.value
                    appendLine("    const val $nameStr: $typeStr = $valueStr")
                }
                appendLine()
            }

            // ─────────────────────────────────────────────────────────────
            // v2.5 — BuildKonfig DSL expansion blocks. All conditional on the
            // corresponding `buildKonfig {}` DSL block being populated.
            // ─────────────────────────────────────────────────────────────

            // v2.5 enum(dimension) — sealed class + active-flavor val per dimension.
            val enumSpecs = dimensionEnumSpecs.orNull.orEmpty()
            if (enumSpecs.isNotEmpty()) {
                appendLine("    // ─── v2.5 dimension enums ─────────────────────────────────────")
                for (spec in enumSpecs.sortedBy { it.dimensionName }) {
                    val dimCap = spec.dimensionName.replaceFirstChar { it.uppercase() }
                    appendLine("    /** Auto-generated sealed-class enum for dimension '${spec.dimensionName}'. */")
                    appendLine("    sealed class $dimCap {")
                    for (flavor in spec.flavorNames.sorted()) {
                        val flavorCap = flavor.replaceFirstChar { it.uppercase() }
                        appendLine("        object $flavorCap : $dimCap()")
                    }
                    appendLine("    }")
                    // Emit a typed val holding the active flavor instance, if known.
                    val activeFlavor = spec.activeFlavorName
                    if (activeFlavor != null && activeFlavor in spec.flavorNames) {
                        val activeCap = activeFlavor.replaceFirstChar { it.uppercase() }
                        appendLine("    val ${spec.dimensionName}: $dimCap = $dimCap.$activeCap")
                    }
                    appendLine()
                }
            }

            // v2.5 customField — sealed-class / List<T> / arbitrary-type field emission.
            val custom = customFieldSpecs.orNull.orEmpty()
            if (custom.isNotEmpty()) {
                appendLine("    // ─── v2.5 customField<T> declarations ──────────────────────────")
                for (cf in custom.sortedBy { it.name }) {
                    appendLine("    val ${cf.name}: ${cf.typeDescriptor} = ${cf.value}")
                }
                appendLine()
            }

            // v2.5 secret — placeholder emission (real resolution deferred to v2.5.x patch).
            // SV15 compliance: never emit real secret values into the generated `.kt` file.
            val secretIds = buildKonfigSecretIds.orNull.orEmpty()
            if (secretIds.isNotEmpty()) {
                appendLine("    // ─── v2.5 vault-integrated secrets (placeholders) ──────────────")
                appendLine("    // Real value resolution semantics deferred to v2.5.x patch per")
                appendLine("    // docs/SECRETS_INTEGRATION.md. SV15 compliance: codegen NEVER")
                appendLine("    // emits hardcoded secret values into the generated .kt file.")
                for (id in secretIds.sorted()) {
                    val constName = id.toScreamingSnake()
                    appendLine("    const val $constName: String = \"<unresolved:see-docs-SECRETS_INTEGRATION>\"")
                }
                appendLine()
            }

            // v2.5 perTarget — nested object emission. v2.5 simplification: emit all
            // target blocks into the main BuildKonfig object. KMP source-set dependsOn
            // discipline gates access at the language level. True per-file source-set
            // isolation deferred to v2.6 (see docs/SECRETS_INTEGRATION.md "perTarget semantics").
            val perTarget = perTargetFieldSpecs.orNull.orEmpty()
            if (perTarget.isNotEmpty()) {
                appendLine("    // ─── v2.5 perTarget conditional codegen ────────────────────────")
                appendLine("    /** Per-target scoped fields. v2.5 emits all blocks into the main object. */")
                appendLine("    object PerTarget {")
                val grouped = perTarget.groupBy { it.targetName }
                for ((targetName, fieldsForTarget) in grouped.toSortedMap()) {
                    val targetCap = targetName.replaceFirstChar { it.uppercase() }
                    appendLine("        object $targetCap {")
                    for (f in fieldsForTarget.sortedBy { it.name }) {
                        appendLine("            const val ${f.name}: ${f.typeDescriptor} = ${f.value}")
                    }
                    appendLine("        }")
                }
                appendLine("    }")
                appendLine()
            }

            // ─── v2.6 Phase 4 network constants ──────────────────────────────────
            // Picks the URL whose key matches one of the active variant's flavor
            // names. If no match is found, emits the sentinel placeholder; the
            // KMPF-V30 validator should have surfaced this at extension-eval time
            // so the placeholder is a belt-and-suspenders safety net.
            val networkSpec = networkConfigSpec.orNull
            if (networkSpec != null && networkSpec.baseUrls.isNotEmpty()) {
                val activeFlavors = activeFlavorNames.get()
                val matchingFlavor = activeFlavors.firstOrNull { it in networkSpec.baseUrls.keys }
                val activeUrl = networkSpec.baseUrls[matchingFlavor] ?: "<no baseUrl mapped for active variant>"
                appendLine("    // ─── v2.6 network constants ──────────────────────────────────")
                appendLine("    object Network {")
                appendLine("        const val BASE_URL: String = \"$activeUrl\"")
                appendLine("        const val TIMEOUT_SECONDS: Int = ${networkSpec.timeoutSeconds}")
                appendLine("    }")
                appendLine()
            }

            appendLine("}")
            appendLine()
        }

        outputFile.writeText(content)
        logger.lifecycle("[KMP Flavors] Generated $outputFile")
    }

    /**
     * Convert a kebab/dot/lower-snake identifier to SCREAMING_SNAKE_CASE for
     * use as a Kotlin `const val` name. Used by the v2.5 secret emission path
     * to map secret IDs like `api-key` → `API_KEY`.
     */
    private fun String.toScreamingSnake(): String {
        // Replace separators with underscore, then uppercase. Trim leading/trailing
        // underscores so e.g. "-x" doesn't produce "_X".
        return this
            .replace(Regex("[-.\\s]+"), "_")
            .uppercase()
            .trim('_')
    }
}

/**
 * v2.5 — Serializable input shape for [GenerateBuildConfigTask.dimensionEnumSpecs].
 *
 * @param dimensionName Name of the dimension (e.g. "tier"). Codegen capitalizes to
 *   produce the sealed-class name (`Tier`).
 * @param flavorNames Names of flavors that belong to this dimension. Codegen emits
 *   one `object` subclass per name, capitalized.
 * @param activeFlavorName Name of the variant's active flavor in this dimension,
 *   or null if not resolvable. When non-null, codegen emits a typed `val ${dim}`
 *   holding the active instance.
 */
data class DimensionEnumSpec(val dimensionName: String, val flavorNames: List<String>, val activeFlavorName: String?) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
