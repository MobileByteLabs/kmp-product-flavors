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

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Propagates KMP flavor DSL into the Android Gradle Plugin (AGP) extension.
 *
 * Reflective access is used so this module does not need a compile-time
 * dependency on `com.android.tools.build:gradle`. AGP's public extension
 * (`com.android.build.api.dsl.ApplicationExtension`) is found via the
 * project's extension container only when the consumer applies AGP.
 *
 * Scope (v1.1.0): `com.android.application` only.
 * Scope (v1.2.0, Phase J): `com.android.library` + `com.android.kotlin.multiplatform.library`.
 *
 * The bridge is conservative — when AGP `productFlavors` or `buildTypes` are
 * already populated by the consumer's own DSL, it logs a warning and skips
 * propagation rather than silently overriding.
 */
internal object AgpBridge {

    private const val APP_PLUGIN_ID = "com.android.application"

    /**
     * Apply the AGP bridge if requested. Returns silently when:
     * - the project does not apply `com.android.application` (v1.1.0 scope), or
     * - AGP's `ApplicationExtension` cannot be located.
     */
    fun apply(
        project: Project,
        bridgeProductFlavors: Boolean,
        bridgeBuildTypes: Boolean,
        kmpDimensions: List<FlavorDimension>,
        kmpFlavors: List<FlavorConfig>,
        kmpBuildTypes: List<BuildTypeConfig>,
        logger: Logger,
    ) {
        if (!bridgeProductFlavors && !bridgeBuildTypes) return

        if (!project.plugins.hasPlugin(APP_PLUGIN_ID)) {
            // v1.1.0 scope: only com.android.application is supported. Library
            // and KMP-library variants land in v1.2.0 (Phase J of
            // PLAN-gaps-fix-260510-191003).
            logger.info(
                "[KMP Flavors] bridgeAgp* flag set but com.android.application not " +
                    "applied — skipping AGP bridge (library plugin support arrives in v1.2.0).",
            )
            return
        }

        val androidExt = findApplicationExtension(project)
        if (androidExt == null) {
            logger.warn(
                "[KMP Flavors] com.android.application is applied but ApplicationExtension " +
                    "could not be resolved — AGP bridge skipped.",
            )
            return
        }

        if (bridgeProductFlavors) {
            propagateFlavors(androidExt, kmpDimensions, kmpFlavors, logger)
        }
        if (bridgeBuildTypes) {
            propagateBuildTypes(androidExt, kmpBuildTypes, logger)
        }
    }

    private fun findApplicationExtension(project: Project): Any? =
        runCatching {
            val cls = Class.forName("com.android.build.api.dsl.ApplicationExtension")
            project.extensions.findByType(cls)
        }.getOrNull()

    private fun propagateFlavors(
        androidExt: Any,
        kmpDimensions: List<FlavorDimension>,
        kmpFlavors: List<FlavorConfig>,
        logger: Logger,
    ) {
        if (kmpDimensions.isEmpty() || kmpFlavors.isEmpty()) {
            logger.info("[KMP Flavors] No KMP dimensions/flavors to propagate to AGP.")
            return
        }

        // Collision check — if AGP productFlavors already populated, warn and bail.
        val existing = readAgpProductFlavors(androidExt)
        if (existing.isNotEmpty()) {
            logger.warn(
                "[KMP Flavors] AGP productFlavors already declares: ${existing.joinToString()} — " +
                    "skipping bridge propagation. Remove the hand-written android { productFlavors {} } " +
                    "block to let kmpFlavors {} drive AGP, or set bridgeAgpProductFlavors.set(false).",
            )
            return
        }

        // Append dimensions in priority order (higher priority first, matching FlavorVariantResolver).
        val orderedDims = kmpDimensions.sortedBy { -(it.priority.orNull ?: 0) }
        val agpDimensions = orderedDims.map { it.name }
        appendDimensions(androidExt, agpDimensions)

        // Register flavors.
        val productFlavors = readMutableProductFlavorsContainer(androidExt) ?: run {
            logger.warn("[KMP Flavors] Could not access AGP productFlavors container — bridge skipped.")
            return
        }
        for (kmp in kmpFlavors) {
            registerAgpFlavor(productFlavors, kmp)
        }
        logger.lifecycle(
            "[KMP Flavors] Bridged ${kmpFlavors.size} flavor(s) across ${agpDimensions.size} dimension(s) into AGP.",
        )
    }

    private fun propagateBuildTypes(
        androidExt: Any,
        kmpBuildTypes: List<BuildTypeConfig>,
        logger: Logger,
    ) {
        if (kmpBuildTypes.isEmpty()) {
            logger.info("[KMP Flavors] No KMP buildTypes to propagate to AGP.")
            return
        }

        val container = readMutableBuildTypesContainer(androidExt) ?: run {
            logger.warn("[KMP Flavors] Could not access AGP buildTypes container — bridge skipped.")
            return
        }

        // Detect non-default custom build types and warn (debug/release are AGP defaults).
        val existingNames = readAgpBuildTypeNames(androidExt)
        val nonDefault = existingNames.filterNot { it == "debug" || it == "release" }
        if (nonDefault.isNotEmpty()) {
            logger.warn(
                "[KMP Flavors] AGP buildTypes already declares custom types: ${nonDefault.joinToString()} — " +
                    "skipping bridge propagation. Remove the hand-written android { buildTypes {} } " +
                    "additions to let kmpFlavors {} drive AGP, or set bridgeAgpBuildTypes.set(false).",
            )
            return
        }

        for (kmp in kmpBuildTypes) {
            registerAgpBuildType(container, kmp)
        }
        logger.lifecycle("[KMP Flavors] Bridged ${kmpBuildTypes.size} buildType(s) into AGP.")
    }

    // --- Reflective AGP accessors ---------------------------------------------------------

    private fun appendDimensions(androidExt: Any, names: List<String>) {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getFlavorDimensions" }
            ?: return
        @Suppress("UNCHECKED_CAST")
        val mutable = getter.invoke(androidExt) as? MutableList<String> ?: return
        for (name in names) {
            if (name !in mutable) mutable.add(name)
        }
    }

    private fun readAgpProductFlavors(androidExt: Any): List<String> {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getProductFlavors" }
            ?: return emptyList()
        val container = getter.invoke(androidExt) ?: return emptyList()
        val nameGetter = container.javaClass.methods.firstOrNull { it.name == "getNames" }
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (nameGetter.invoke(container) as? Set<String>)?.toList() ?: emptyList()
    }

    private fun readMutableProductFlavorsContainer(androidExt: Any): Any? {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getProductFlavors" }
            ?: return null
        return getter.invoke(androidExt)
    }

    private fun readMutableBuildTypesContainer(androidExt: Any): Any? {
        val getter = androidExt.javaClass.methods.firstOrNull { it.name == "getBuildTypes" }
            ?: return null
        return getter.invoke(androidExt)
    }

    private fun readAgpBuildTypeNames(androidExt: Any): List<String> {
        val container = readMutableBuildTypesContainer(androidExt) ?: return emptyList()
        val nameGetter = container.javaClass.methods.firstOrNull { it.name == "getNames" }
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (nameGetter.invoke(container) as? Set<String>)?.toList() ?: emptyList()
    }

    private fun registerAgpFlavor(container: Any, kmp: FlavorConfig) {
        val maybeCreate = container.javaClass.methods.firstOrNull {
            it.name == "maybeCreate" && it.parameterCount == 1
        } ?: return
        val agpFlavor = maybeCreate.invoke(container, kmp.name) ?: return

        kmp.dimension.orNull?.let { setProperty(agpFlavor, "setDimension", it) }
        kmp.applicationIdSuffix.orNull?.let { setProperty(agpFlavor, "setApplicationIdSuffix", it) }
        kmp.versionNameSuffix.orNull?.let { setProperty(agpFlavor, "setVersionNameSuffix", it) }
    }

    private fun registerAgpBuildType(container: Any, kmp: BuildTypeConfig) {
        val maybeCreate = container.javaClass.methods.firstOrNull {
            it.name == "maybeCreate" && it.parameterCount == 1
        } ?: return
        val agpBuildType = maybeCreate.invoke(container, kmp.name) ?: return

        kmp.isDebuggable.orNull?.let { setBooleanProperty(agpBuildType, "setDebuggable", it) }
        kmp.isMinifyEnabled.orNull?.let { setBooleanProperty(agpBuildType, "setMinifyEnabled", it) }
        kmp.applicationIdSuffix.orNull?.let { setProperty(agpBuildType, "setApplicationIdSuffix", it) }
    }

    private fun setProperty(target: Any, setter: String, value: String) {
        runCatching {
            val method = target.javaClass.methods.firstOrNull {
                it.name == setter && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }
            method?.invoke(target, value)
        }
    }

    private fun setBooleanProperty(target: Any, setter: String, value: Boolean) {
        runCatching {
            val method = target.javaClass.methods.firstOrNull {
                it.name == setter &&
                    it.parameterCount == 1 &&
                    (it.parameterTypes[0] == java.lang.Boolean.TYPE || it.parameterTypes[0] == Boolean::class.javaObjectType)
            }
            method?.invoke(target, value)
        }
    }
}
