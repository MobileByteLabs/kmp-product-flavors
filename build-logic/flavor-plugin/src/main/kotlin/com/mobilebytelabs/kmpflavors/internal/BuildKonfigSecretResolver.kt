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

import java.io.File
import java.util.Properties

/**
 * v2.5 Phase 3 — Standalone helper for resolving vault-integrated BuildKonfig
 * secrets from a consumer's `secrets-manifest.yaml` + materialized `local.properties`.
 *
 * **Status:** Phase 3 ships this as a callable API; the actual codegen path uses
 * placeholder emission per SV15 compliance (see GenerateBuildConfigTask v2.5 secret
 * emission block). Real-value wiring ships in a v2.5.x patch once the framework-side
 * `secrets-manifest.yaml` schema v2.1 + `secrets-pull.sh --emit-gradle-flavor-map`
 * mode land per docs/SECRETS_INTEGRATION.md.
 *
 * **API contract:** caller passes the project directory + variant name; resolver returns
 * a [SecretResolution] discriminated union. Caller decides how to consume:
 * - [SecretResolution.Resolved] — value is available; safe to inject into Gradle inputs
 *   marked `@get:Internal` (NEVER `@Input` — caching secrets is a leak).
 * - [SecretResolution.Unavailable] — reason describes why (schema-too-old, missing-key,
 *   no-flavor-selector-for-variant). Caller emits KMPF-V26 ERROR or WARN as appropriate.
 *
 * **Implementation notes:**
 * - Reads `secrets-manifest.yaml` via line-based parsing (no kotlinx.serialization
 *   dependency at v2.5 — keep build-logic deps lean). The parser handles the v2.1
 *   `needs[].flavor_selector.selector_values` block only; everything else surfaces
 *   as [SecretResolution.Unavailable].
 * - Reads `local.properties` via `java.util.Properties.load()`.
 * - Never writes secret values to logs. The `toString()` of [SecretResolution.Resolved]
 *   intentionally redacts the value.
 *
 * @see docs/SECRETS_INTEGRATION.md
 * @see com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask v2.5 secret emission
 */
internal class BuildKonfigSecretResolver(private val projectDir: File) {

    private val manifestFile: File by lazy { File(projectDir, "secrets-manifest.yaml") }
    private val localProperties: File by lazy { File(projectDir, "local.properties") }

    /**
     * Resolve a single secret for the given variant name.
     *
     * Returns:
     * - [SecretResolution.Resolved] when the manifest declares the secret with a
     *   `flavor_selector` entry matching [variantName] AND `local.properties` contains
     *   the corresponding key.
     * - [SecretResolution.Unavailable] with a descriptive `reason` otherwise.
     */
    fun resolveForVariant(secretId: String, variantName: String): SecretResolution {
        if (!manifestFile.exists()) {
            return SecretResolution.Unavailable("secrets-manifest-missing")
        }
        val manifest = parseManifest()
            ?: return SecretResolution.Unavailable("manifest-parse-failed")
        if (versionLessThan(manifest.schemaVersion, "2.1")) {
            return SecretResolution.Unavailable("schema-v20-fallback")
        }
        val need = manifest.needs.firstOrNull { it.id == secretId }
            ?: return SecretResolution.Unavailable("not-in-manifest")
        val selectorKey = need.flavorSelector[variantName]
            ?: return SecretResolution.Unavailable("no-selector-for-variant:$variantName")
        if (!localProperties.exists()) {
            return SecretResolution.Unavailable("local-properties-missing")
        }
        val props = Properties().apply {
            localProperties.inputStream().use { load(it) }
        }
        val value = props.getProperty(selectorKey)
            ?: return SecretResolution.Unavailable("local-properties-missing-key:$selectorKey")
        return SecretResolution.Resolved(value)
    }

    /**
     * Inspect the manifest's schema_version without performing per-secret resolution.
     * Returns null when the manifest is missing or unparseable.
     */
    fun manifestSchemaVersion(): String? {
        if (!manifestFile.exists()) return null
        return parseManifest()?.schemaVersion
    }

    /**
     * Minimalist YAML reader for secrets-manifest.yaml. Recognizes:
     * - Top-level `schema_version: "X.Y"` line
     * - Top-level `needs:` block with `- id: NAME` entries
     * - Per-need `flavor_selector: { selector_values: { variantName: key } }` block
     *
     * Everything else is ignored. This keeps build-logic free of kotlinx.serialization
     * + snakeyaml deps for v2.5; if richer manifest features are needed in v2.5.x, swap
     * in snakeyaml at that point.
     */
    private fun parseManifest(): ParsedManifest? = runCatching {
        val lines = manifestFile.readLines()
        var schemaVersion = "unknown"
        val needs = mutableListOf<ParsedNeed>()
        var current: ParsedNeed? = null
        var inFlavorSelectorBlock = false
        var inSelectorValues = false
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue

            // Top-level keys (no leading whitespace)
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                inFlavorSelectorBlock = false
                inSelectorValues = false
                when {
                    line.startsWith("schema_version:") -> {
                        schemaVersion = line.substringAfter("schema_version:").trim().trim('"', '\'')
                    }

                    line.startsWith("needs:") -> { /* enter needs section */ }
                }
                continue
            }

            // Need entry: `  - id: NAME`
            val trimmed = line.trimStart()
            if (trimmed.startsWith("- id:")) {
                current?.let { needs.add(it) }
                current = ParsedNeed(id = trimmed.substringAfter("- id:").trim().trim('"', '\''))
                inFlavorSelectorBlock = false
                inSelectorValues = false
                continue
            }
            val cur = current ?: continue

            if (trimmed.startsWith("flavor_selector:")) {
                inFlavorSelectorBlock = true
                inSelectorValues = false
                continue
            }
            if (inFlavorSelectorBlock && trimmed.startsWith("selector_values:")) {
                inSelectorValues = true
                continue
            }
            if (inSelectorValues && trimmed.contains(":")) {
                val k = trimmed.substringBefore(":").trim().trim('"', '\'')
                val v = trimmed.substringAfter(":").trim().trim('"', '\'')
                if (k.isNotBlank() && v.isNotBlank()) {
                    cur.flavorSelector[k] = v
                }
                continue
            }
        }
        current?.let { needs.add(it) }
        ParsedManifest(schemaVersion = schemaVersion, needs = needs)
    }.getOrNull()

    /**
     * Naïve semver comparison sufficient for major.minor strings used by
     * the secrets-manifest.yaml schema_version field.
     */
    private fun versionLessThan(a: String, b: String): Boolean {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            if (aParts[i] != bParts[i]) return aParts[i] < bParts[i]
        }
        return aParts.size < bParts.size
    }

    private data class ParsedManifest(val schemaVersion: String, val needs: List<ParsedNeed>)

    private data class ParsedNeed(val id: String, val flavorSelector: MutableMap<String, String> = mutableMapOf())
}

/**
 * v2.5 — Discriminated union for [BuildKonfigSecretResolver.resolveForVariant] result.
 *
 * Sealed class layered for caller pattern-matching. [Resolved.toString] is intentionally
 * redacted to keep secret values out of accidental logs.
 */
internal sealed class SecretResolution {
    data class Resolved(val value: String) : SecretResolution() {
        override fun toString(): String = "Resolved(value=<redacted>)"
    }
    data class Unavailable(val reason: String) : SecretResolution()
}
