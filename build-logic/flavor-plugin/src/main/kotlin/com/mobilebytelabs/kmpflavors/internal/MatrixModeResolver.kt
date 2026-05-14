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

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

/**
 * Resolves whether v2.0 matrix mode is enabled for a given project.
 *
 * Hybrid resolution (RFC §3 Q16-C):
 * 1. If `extension.buildMatrix` is explicitly set (via convention plugin or
 *    consumer DSL), that value wins.
 * 2. Otherwise, falls back to the `kmpFlavors.buildMatrix` Gradle property.
 * 3. Otherwise, returns `false` (v1.x active-variant-only behaviour).
 *
 * This is the SINGLE consumer touch-point for matrix mode opt-in.
 * Per the Zero-Touch Adoption Design Tenet (RFC §1.1), the plugin
 * MUST NOT expose any per-module DSL that bypasses this resolver.
 */
internal object MatrixModeResolver {

    const val GRADLE_PROPERTY: String = "kmpFlavors.buildMatrix"

    /**
     * Returns `true` when v2.0 matrix mode is enabled for [project], using
     * the hybrid resolution rules in [MatrixModeResolver]'s class doc.
     */
    fun isEnabled(project: Project, extension: KmpFlavorExtension): Boolean {
        // 1. Explicit extension override wins.
        if (extension.buildMatrix.isPresent) {
            return extension.buildMatrix.get()
        }
        // 2. Fall back to Gradle property.
        val raw = project.findProperty(GRADLE_PROPERTY)?.toString() ?: return false
        // 3. Treat anything that isn't the literal "true" (case-insensitive)
        //    as false. Avoids confusing on/yes/1 surprises — explicit only.
        return raw.equals("true", ignoreCase = true)
    }
}
