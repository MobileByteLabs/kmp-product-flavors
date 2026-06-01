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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * v2.6 Phase 3 — `kmpFlavors { analytics { ... } }` block.
 *
 * Codegens per-variant `AnalyticsTags.kt` containing `VARIANT_NAME`,
 * `BUILD_TYPE`, and consumer-declared custom tags, plus a reflective
 * `attachTo(target)` helper that wires into Firebase-Crashlytics-shaped
 * targets (any object with a `setCustomKey(String, String)` method).
 *
 * ```kotlin
 * kmpFlavors {
 *     analytics {
 *         enabled.set(true)
 *         customTag("environment") { variant -> variant.flavors.firstOrNull()?.name ?: "default" }
 *         customTag("tier") { variant -> variant.flavors.firstOrNull { it.name in listOf("free", "paid") }?.name ?: "default" }
 *     }
 * }
 * ```
 *
 * No-op when `enabled = false` (the default). Consumer brings their own
 * Crashlytics / Sentry / Firebase Analytics SDK — the plugin only emits
 * the metadata + reflective attachment helper.
 */
abstract class AnalyticsTagsConfig @Inject constructor(objects: ObjectFactory) {

    /**
     * Master toggle. Default `false` — analytics codegen runs only when the
     * consumer explicitly opts in.
     */
    abstract val enabled: Property<Boolean>

    /**
     * Target platforms for emission. v2.6 ships commonMain-only — per-target
     * variant emission deferred to v2.7 if demand surfaces. Field is reserved
     * for forward compatibility.
     */
    abstract val platforms: ListProperty<String>

    /**
     * Consumer-registered custom tags. Each entry maps a tag name to a resolver
     * that derives the tag value from the [FlavorVariant] being codegened —
     * resolved at configuration time and frozen into the task's
     * `customTagValues` map property (no runtime closures cross the
     * config-cache boundary).
     */
    internal val customTags: MutableMap<String, (FlavorVariant) -> String> = linkedMapOf()

    /**
     * Register a custom tag.
     *
     * @param name UPPER_SNAKE_CASE key in the generated `AnalyticsTags` object
     *   (auto-uppercased; pass `"environment"` and get `ENVIRONMENT`).
     * @param valueResolver receives the variant and returns the string value
     *   for that variant's emitted constant.
     */
    fun customTag(name: String, valueResolver: (FlavorVariant) -> String) {
        require(name.isNotBlank()) { "customTag(name) requires a non-blank name" }
        customTags[name] = valueResolver
    }

    init {
        enabled.convention(false)
        platforms.convention(listOf("common"))
    }
}
