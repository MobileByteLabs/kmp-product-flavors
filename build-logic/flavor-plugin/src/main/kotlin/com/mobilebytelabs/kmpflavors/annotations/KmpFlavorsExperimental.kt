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

package com.mobilebytelabs.kmpflavors.annotations

/**
 * Marks a `KmpFlavorExtension` property or DSL surface as experimental.
 *
 * Experimental APIs may change behavior, get renamed, or be removed in
 * v2.x point releases **without** the SemVer-level breaking-change notice
 * that Stable surfaces enjoy. Consumers should plan for either tracking
 * the API on every minor release OR pinning a specific version.
 *
 * **Informational, not opt-in-required.** This annotation is intentionally
 * source-retention + does NOT trigger Kotlin's opt-in framework (no
 * `@RequiresOptIn`), because the cost of forcing every consumer to
 * `@OptIn(KmpFlavorsExperimental::class)` on every property access would
 * be high friction with low value. Instead, the annotation surfaces in:
 *
 * - KDoc generated for the plugin's public API.
 * - IDE hover hints (IntelliJ shows the `reason` parameter).
 * - The plugin's [`docs/REFERENCE.md`](../../../../../docs/REFERENCE.md)
 *   stability-bucket column.
 *
 * @param reason Short explanation of why the surface is experimental.
 *   Typical content: what data / API / consumer-signal would unlock
 *   promotion to Stable. Surfaces in IDE hover hints + the generated
 *   KDoc, so be concise + actionable.
 */
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
)
@Retention(AnnotationRetention.SOURCE)
annotation class KmpFlavorsExperimental(val reason: String = "")
