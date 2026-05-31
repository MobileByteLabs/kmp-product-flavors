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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * v2.5 Phase 3 — Pre-codegen check that the consumer's `secrets-manifest.yaml`
 * is at schema v2.1+ when `kmpFlavors.buildKonfig { secret(...) }` is declared.
 *
 * **Behavior:**
 * - If the consumer has NOT declared any secrets via `buildKonfig { secret(id) }`,
 *   the task is registered as no-op (skipped via task graph). Registration happens
 *   in `KmpFlavorPlugin` only when secrets are declared.
 * - If secrets are declared but the manifest is missing → emit KMPF-V26 WARN.
 * - If secrets are declared and manifest schema_version < v2.1 → emit KMPF-V26 WARN
 *   (graceful degradation per RULE-SECRETS-VAULT-001 SV15 — no hardcoded values).
 * - If secrets are declared and manifest schema_version ≥ v2.1 → emit OK marker.
 *
 * The task writes a marker file at [outputMarker] so that downstream codegen tasks
 * can `dependsOn` it (forcing the check to run before BuildKonfig generation).
 * The marker contents are informational only — the actual secret resolution happens
 * in [com.mobilebytelabs.kmpflavors.internal.BuildKonfigSecretResolver] at codegen
 * time per the v2.5.x patch roadmap (see docs/SECRETS_INTEGRATION.md).
 *
 * **CI integration:** the WARN message includes `KMPF-V26` so downstream CI grep,
 * dashboard aggregation, and IDE inspection tooling can surface the schema-mismatch.
 *
 * @see com.mobilebytelabs.kmpflavors.internal.BuildKonfigSecretResolver
 * @see com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator.CODE_SECRET_RESOLUTION_FAIL
 */
@DisableCachingByDefault(
    because = "Task reads consumer's secrets-manifest.yaml at execution time + " +
        "emits informational marker; caching by Gradle's build-cache would skip " +
        "the schema-check WARN log on subsequent runs, defeating the SV15 visibility goal.",
)
abstract class FrameworkSchemaCheckTask : DefaultTask() {

    /**
     * Path to the consumer's `secrets-manifest.yaml` file. Optional — when absent,
     * the task emits KMPF-V26 WARN.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretsManifestFile: RegularFileProperty

    /**
     * Secret IDs declared via `kmpFlavors.buildKonfig { secret(id) }`. The task only
     * fires when this list is non-empty.
     */
    @get:Input
    abstract val declaredSecretIds: ListProperty<String>

    /**
     * Marker file written when the check completes. Contents are informational —
     * either `OK: schema-v2.1+` or `WARN: KMPF-V26 ...`. Used as a task graph
     * dependency so downstream codegen runs after this check.
     */
    @get:OutputFile
    abstract val outputMarker: RegularFileProperty

    init {
        group = "kmp flavors"
        description = "v2.5 — Pre-codegen check that secrets-manifest.yaml schema is v2.1+"
    }

    @TaskAction
    fun check() {
        val secretIds = declaredSecretIds.get()
        val marker = outputMarker.get().asFile
        marker.parentFile.mkdirs()

        if (secretIds.isEmpty()) {
            marker.writeText("OK: no secrets declared (task was a no-op)\n")
            return
        }

        val manifest = secretsManifestFile.orNull?.asFile
        if (manifest == null || !manifest.exists()) {
            val msg = "[KMP Flavors] KMPF-V26 — kmpFlavors.buildKonfig { secret(...) } declared " +
                "for ${secretIds.joinToString { "'$it'" }} but secrets-manifest.yaml not found. " +
                "Plugin will emit placeholder values (SV15 compliance). " +
                "See docs/SECRETS_INTEGRATION.md."
            logger.warn(msg)
            marker.writeText("WARN: KMPF-V26 manifest-missing\n$msg\n")
            return
        }

        // Naïve schema_version extraction — first non-comment line matching `schema_version: "X.Y"`.
        val schemaVersion = manifest
            .readLines()
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("schema_version:") }
            ?.substringAfter("schema_version:")
            ?.trim()
            ?.trim('"', '\'')
            ?: "unknown"

        if (versionLessThan(schemaVersion, "2.1")) {
            val msg = "[KMP Flavors] KMPF-V26 — kmpFlavors.buildKonfig { secret(...) } declared " +
                "for ${secretIds.joinToString { "'$it'" }} but secrets-manifest.yaml is " +
                "schema_version='$schemaVersion'. Schema v2.1+ required for flavor-aware " +
                "secret resolution. Plugin will emit placeholder values (SV15 compliance). " +
                "See docs/SECRETS_INTEGRATION.md."
            logger.warn(msg)
            marker.writeText("WARN: KMPF-V26 schema-v20-fallback\n$msg\n")
            return
        }

        marker.writeText(
            "OK: secrets-manifest.yaml schema_version='$schemaVersion' (v2.1+) — " +
                "secret IDs declared: ${secretIds.joinToString()}\n",
        )
    }

    /**
     * Naïve semver less-than comparison for schema_version strings ("2.0", "2.1", etc.).
     */
    private fun versionLessThan(a: String, b: String): Boolean {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            if (aParts[i] != bParts[i]) return aParts[i] < bParts[i]
        }
        return aParts.size < bParts.size
    }
}
