/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Exports the currently declared `kmpFlavors {}` DSL as a machine-readable
 * `variants.json` manifest — a single derivation point downstream deployment
 * stages (fastlane variant resolvers, CI resolve-variants jobs) can read instead
 * of hardcoding a flavor list. Reads only the plugin's public DSL surface.
 *
 * ## Config-cache safety
 *
 * All inputs are `MapProperty` / `RegularFileProperty` — no `Project` reference is
 * captured in the task action. The provider blocks that resolve the DSL live at the
 * registration site.
 *
 * ## Output schema (variants.json)
 *
 *     {
 *       "schema_version": 1,
 *       "flavors":    [ { "name", "applicationIdSuffix", "bundleIdSuffix" }, ... ],
 *       "buildTypes": [ { "name", "applicationIdSuffix" }, ... ],
 *       "variants":   [ { "name", "flavor", "buildType" }, ... ]
 *     }
 *
 * Variant name convention: `flavor + BuildType` (build-type first letter upper-cased).
 * Both axes are sorted alphabetically for deterministic, git-diff-friendly output.
 */
@DisableCachingByDefault(because = "Reads live DSL state at execution time; regenerated every run so downstream CI is never stale")
abstract class ExportKmpFlavorsManifestTask : DefaultTask() {

    /** flavor name → Android `applicationIdSuffix` (empty string when none). */
    @get:Input
    abstract val flavorAppIdSuffixes: MapProperty<String, String>

    /** flavor name → iOS `bundleIdSuffix` (empty string when none). */
    @get:Input
    abstract val flavorBundleIdSuffixes: MapProperty<String, String>

    /** build-type name → Android `applicationIdSuffix` (empty string when none). */
    @get:Input
    abstract val buildTypeAppIdSuffixes: MapProperty<String, String>

    /** Destination for the emitted `variants.json` (convention: `build/kmp-flavors/variants.json`). */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "kmp-flavors"
        description = "Emit variants.json from the kmpFlavors {} DSL (single derivation point for CI/CD)."
    }

    @TaskAction
    fun export() {
        val flavors = flavorAppIdSuffixes.get()
        val bundleIds = flavorBundleIdSuffixes.get()
        val buildTypes = buildTypeAppIdSuffixes.get()

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(buildManifestJson(flavors, bundleIds, buildTypes))

        logger.lifecycle(
            "exportKmpFlavorsManifest: wrote ${out.absolutePath} " +
                "(${flavors.size} flavors x ${buildTypes.size} buildTypes = " +
                "${flavors.size * buildTypes.size} variants)",
        )
    }

    private fun buildManifestJson(flavors: Map<String, String>, bundleIds: Map<String, String>, buildTypes: Map<String, String>): String {
        val sortedFlavors = flavors.entries.sortedBy { it.key }
        val sortedBuildTypes = buildTypes.entries.sortedBy { it.key }

        val flavorsJson = sortedFlavors.joinToString(separator = ",\n    ", prefix = "[\n    ", postfix = "\n  ]") { (name, appIdSuffix) ->
            val bundleSuffix = bundleIds[name] ?: ""
            "{ \"name\": \"${jsonEscape(name)}\", " +
                "\"applicationIdSuffix\": \"${jsonEscape(appIdSuffix)}\", " +
                "\"bundleIdSuffix\": \"${jsonEscape(bundleSuffix)}\" }"
        }

        val buildTypesJson = sortedBuildTypes.joinToString(separator = ",\n    ", prefix = "[\n    ", postfix = "\n  ]") { (name, appIdSuffix) ->
            "{ \"name\": \"${jsonEscape(name)}\", \"applicationIdSuffix\": \"${jsonEscape(appIdSuffix)}\" }"
        }

        val variantRows = sortedFlavors.flatMap { (flavor, _) ->
            sortedBuildTypes.map { (buildType, _) ->
                val name = flavor + buildType.replaceFirstChar { it.uppercase() }
                "    { \"name\": \"${jsonEscape(name)}\", " +
                    "\"flavor\": \"${jsonEscape(flavor)}\", " +
                    "\"buildType\": \"${jsonEscape(buildType)}\" }"
            }
        }
        val variantsJson = variantRows.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n  ]")

        return """
            |{
            |  "schema_version": 1,
            |  "flavors": $flavorsJson,
            |  "buildTypes": $buildTypesJson,
            |  "variants": $variantsJson
            |}
            |
        """.trimMargin()
    }

    private fun jsonEscape(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u").append("%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.toString()
    }
}
