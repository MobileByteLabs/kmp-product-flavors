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

package com.mobilebytelabs.kmpflavors

import org.gradle.api.Action
import java.io.Serializable

/**
 * v2.5 — top-level `kmpFlavors.buildKonfig {}` DSL block.
 *
 * Adds four capabilities on top of the v2.4 `flavors { register(...) { buildConfigField(...) } }`
 * pattern, all of which are purely additive — existing consumers using only flavor-level
 * buildConfigField don't see any v2.5 behavior change.
 *
 * **Four entry points:**
 *
 * 1. `secret(id)` — vault-integrated per-flavor secret. Resolved via the consumer's
 *    `secrets-manifest.yaml` flavor_selector block (schema v2.1+). Falls back to a
 *    placeholder value on schema v2.0 with KMPF-V26 WARN (SV15 compliance per
 *    RULE-SECRETS-VAULT-001). See docs/SECRETS_INTEGRATION.md.
 *
 * 2. `enum(dimension)` — auto-generated sealed-class dimension enum. For
 *    `dimension("tier") { flavor("free"); flavor("paid") }` plus
 *    `buildKonfig { enum("tier") }`, codegen emits:
 *    ```kotlin
 *    public sealed class Tier {
 *        public object Free : Tier()
 *        public object Paid : Tier()
 *    }
 *    public val tier: Tier = Tier.<ActiveFlavor>  // computed per variant
 *    ```
 *
 * 3. `customField<T>(name, value)` — sealed-class or `List<T>` fields. The codegen
 *    emits `public val ${name}: ${T} = ${value}` directly — the consumer's project
 *    must already define the referenced sealed class. For `List<T>`, T must be a
 *    primitive or sealed class. Unsupported types fire KMPF-V27.
 *
 * 4. `perTarget(targetName) { field(name, type, value) }` — per-target conditional
 *    fields. v2.5 emits these as a nested `object PerTarget { object {TargetName} { ... } }`
 *    block within the main BuildKonfig object. KMP source-set dependsOn discipline
 *    naturally gates access (androidMain code can't see iosMain types). True per-file
 *    source-set isolation deferred to v2.6. Invalid target names fire KMPF-V28.
 *
 * Example:
 * ```kotlin
 * kmpFlavors {
 *     dimensions {
 *         dimension("tier") {
 *             flavor("free")
 *             flavor("paid")
 *         }
 *     }
 *     buildKonfig {
 *         secret("API_KEY")
 *         enum("tier")
 *         customField<List<String>>("scopes", listOf("read", "write"))
 *         perTarget("iosMain") {
 *             field("BUNDLE_ID_SUFFIX", "String", "\".dev\"")
 *         }
 *     }
 * }
 * ```
 *
 * @see KmpFlavorExtension.buildKonfig
 */
open class BuildKonfigDsl internal constructor() {
    /**
     * Secret IDs declared via [secret]. Resolved at task-execution time by
     * [com.mobilebytelabs.kmpflavors.internal.BuildKonfigSecretResolver] against
     * the consumer's `secrets-manifest.yaml#needs[].flavor_selector` blocks.
     */
    internal val secrets: MutableList<String> = mutableListOf()

    /**
     * Dimension names declared via [enum]. Each entry triggers emission of a
     * sealed-class enum + a typed `val` for the active variant's flavor in that
     * dimension.
     */
    internal val enums: MutableList<String> = mutableListOf()

    /**
     * Custom-type fields declared via [customField]. List entries surface to the
     * task as a serializable `CustomFieldDeclaration` for codegen + validation.
     */
    internal val customFields: MutableList<CustomFieldDeclaration> = mutableListOf()

    /**
     * Per-target field blocks declared via [perTarget]. Map key = target name
     * (e.g. "iosMain"); value = ordered list of field declarations for that target.
     * Validation against `kotlin.targets` fires KMPF-V28.
     */
    internal val perTargetBlocks: MutableMap<String, MutableList<PerTargetFieldDeclaration>> =
        linkedMapOf()

    /**
     * Declare a vault-integrated per-flavor secret. Resolved per-variant via
     * `secrets-manifest.yaml#needs[].flavor_selector` (schema v2.1+).
     *
     * The plugin reads the materialized value from `local.properties` — it does
     * NOT decrypt secrets directly (that would violate RULE-SECRETS-VAULT-001 SV17).
     * Consumers must run `/secrets pull` before building.
     *
     * @param id Logical name of the secret (matches `needs[].id` in the manifest).
     */
    fun secret(id: String) {
        require(id.isNotBlank()) { "buildKonfig { secret(id) } requires a non-blank id" }
        secrets.add(id)
    }

    /**
     * Declare an auto-generated sealed-class enum for the named dimension.
     *
     * Codegen emits a `sealed class ${DimensionName.capitalize()}` with one
     * `object` subclass per flavor in that dimension, plus a `val ${dimensionName}`
     * holding the active flavor's instance for the resolved variant.
     *
     * @param dimension Name of a dimension declared via `dimensions { dimension(name) { ... } }`
     *   or `flavorDimensions { register(name) }`. Must exist in the project's flavor model.
     */
    fun enum(dimension: String) {
        require(dimension.isNotBlank()) { "buildKonfig { enum(dimension) } requires a non-blank dimension name" }
        enums.add(dimension)
    }

    /**
     * Declare a custom-type field for the BuildKonfig output. Supported types:
     * primitives (Boolean/Int/Long/Float/Double/String), sealed classes, and flat
     * `List<T>` where T is a primitive or sealed class.
     *
     * The consumer's project must define the referenced type — codegen emits a
     * direct value assignment, not the type declaration itself. Unsupported types
     * (nested generics, open classes, Map) fire KMPF-V27.
     *
     * For sealed classes, [value] should be the qualified subclass name as a string
     * (e.g. `"com.example.MyConfig.Default"`). For `List<T>`, [value] should be a
     * Kotlin literal expression (e.g. `"listOf(\"read\", \"write\")"`).
     *
     * Note: this DSL takes a String for `typeDescriptor` rather than a reified `T`
     * parameter — Kotlin doesn't support inline reified types on `open class` methods
     * (DimensionsDsl runtime is non-inline). For ergonomic reified syntax, consumers
     * can wrap their own helper extension function.
     *
     * @param name Field name as it appears in the generated BuildKonfig.
     * @param typeDescriptor Fully-qualified Kotlin type (e.g. `"List<String>"`, `"MyConfig"`).
     * @param value Kotlin literal expression producing a value of [typeDescriptor].
     */
    fun customField(name: String, typeDescriptor: String, value: String) {
        require(name.isNotBlank()) { "buildKonfig { customField(name, ...) } requires a non-blank name" }
        require(typeDescriptor.isNotBlank()) { "buildKonfig { customField(.., type, ...) } requires a non-blank type" }
        customFields.add(CustomFieldDeclaration(name, typeDescriptor, value))
    }

    /**
     * Open a `perTarget(targetName) { ... }` block declaring fields that should
     * appear only for the named KMP target.
     *
     * v2.5 emits these as a nested `object PerTarget { object {TargetName} { ... } }`
     * block inside the main BuildKonfig object — true per-file source-set isolation
     * is deferred to v2.6. KMP source-set dependsOn discipline naturally gates
     * access at the language level (androidMain code can't import iosMain types).
     *
     * Invalid target names (not present in `kotlin.targets`) fire KMPF-V28.
     *
     * @param targetName Name matching a KMP target's source-set prefix
     *   (e.g. `"iosMain"`, `"desktopMain"`, `"wasmJsMain"`).
     */
    fun perTarget(targetName: String, action: Action<PerTargetScope>) {
        require(targetName.isNotBlank()) { "buildKonfig { perTarget(targetName) {...} } requires a non-blank targetName" }
        val scope = PerTargetScope(targetName)
        action.execute(scope)
        perTargetBlocks.getOrPut(targetName) { mutableListOf() }.addAll(scope.fields)
    }

    /**
     * v2.6 Phase 4 — variant-aware network constants. Codegen emits a nested
     * `BuildKonfig.Network { BASE_URL; TIMEOUT_SECONDS }` block; the URL chosen
     * matches the active variant's flavor name.
     *
     * ```kotlin
     * kmpFlavors {
     *     buildKonfig {
     *         network {
     *             baseUrl(
     *                 "free" to "https://api.free.example.com",
     *                 "paid" to "https://api.paid.example.com",
     *             )
     *             timeout(seconds = 30)
     *         }
     *     }
     * }
     * ```
     *
     * Validation: missing flavor keys fire KMPF-V29; variants with no matching
     * baseUrl fire KMPF-V30. See docs/NETWORK_CONFIG.md.
     */
    internal val network: NetworkDsl = NetworkDsl()

    fun network(action: Action<NetworkDsl>) {
        action.execute(network)
    }
}

/**
 * v2.6 Phase 4 — `network { baseUrl(); timeout() }` block inside `buildKonfig {}`.
 *
 * See [BuildKonfigDsl.network] for the consumer-facing entry point.
 */
open class NetworkDsl internal constructor() {
    internal val baseUrls: MutableMap<String, String> = linkedMapOf()
    internal var timeoutSeconds: Int = 30

    /**
     * Register one or more `"flavor" to "https://..."` URL mappings.
     */
    fun baseUrl(vararg pairs: Pair<String, String>) {
        require(pairs.isNotEmpty()) { "network { baseUrl(...) } requires at least one flavor → URL pair" }
        pairs.forEach { (flavor, url) ->
            require(flavor.isNotBlank()) { "network { baseUrl(...) } flavor key must be non-blank" }
            require(url.isNotBlank()) { "network { baseUrl(\"$flavor\" to ...) } URL must be non-blank" }
            baseUrls[flavor] = url
        }
    }

    /**
     * Set the global `TIMEOUT_SECONDS` constant. Must be positive.
     */
    fun timeout(seconds: Int) {
        require(seconds > 0) { "network { timeout(seconds=$seconds) } must be positive" }
        timeoutSeconds = seconds
    }
}

/**
 * Scope passed to the `perTarget(targetName) { ... }` block — registers fields
 * scoped to a single KMP target.
 */
class PerTargetScope internal constructor(val targetName: String) {
    internal val fields: MutableList<PerTargetFieldDeclaration> = mutableListOf()

    /**
     * Declare a single field scoped to [targetName].
     *
     * @param name Field name as it appears in the generated nested object.
     * @param typeDescriptor Kotlin type (e.g. `"String"`, `"Boolean"`, `"Int"`).
     * @param value Kotlin literal expression.
     */
    fun field(name: String, typeDescriptor: String, value: String) {
        require(name.isNotBlank()) { "perTarget { field(name, ...) } requires a non-blank name" }
        fields.add(PerTargetFieldDeclaration(name, typeDescriptor, value, targetName))
    }
}

/**
 * Serializable declaration for a custom-type field. Threaded into
 * [com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask] as task input.
 */
data class CustomFieldDeclaration(val name: String, val typeDescriptor: String, val value: String) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Serializable declaration for a per-target scoped field.
 */
data class PerTargetFieldDeclaration(val name: String, val typeDescriptor: String, val value: String, val targetName: String) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
