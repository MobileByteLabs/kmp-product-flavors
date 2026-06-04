/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * AGP productFlavors + buildTypes registrar.
 *
 * Uses NamedDomainObjectCollection.whenObjectAdded on the consumer's `kmpFlavors {}`
 * extension to register each flavor/buildType into AGP's `android.productFlavors`
 * and `android.buildTypes` containers AT THE MOMENT the consumer's DSL adds them.
 *
 * Why this approach: AGP's `finalizeDsl` callback queue is drained during AGP's
 * own afterEvaluate. Callbacks added to that queue AFTER AGP drains it are silently
 * ignored. The consumer's `extensions.configure<KmpFlavorExtension> { ... }` block
 * runs synchronously during convention-plugin apply(), which is BEFORE AGP's
 * afterEvaluate but AFTER AGP has registered its lifecycle. By using
 * whenObjectAdded, we hook each flavor registration synchronously — productFlavors
 * land in AGP's container immediately, well before variant materialization.
 *
 * All reflective so the flavor-plugin module never compile-time-depends on AGP.
 */
internal object AgpProductFlavorRegistrar {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val androidExt = project.extensions.findByName("android") ?: run {
            logger.info("[KMP Flavors] ${project.path}: android extension not yet available")
            return 0
        }

        // Two-phase wiring:
        //  - whenObjectAdded fires SYNCHRONOUSLY when the consumer's `register("name") { ... }`
        //    is called — but BEFORE the configure-action runs, so properties like
        //    dimension/applicationIdSuffix are still unset. We use this to place an
        //    AGP-side flavor slot in the container EARLY (using flavorDimensions[0]
        //    as the dimension fallback so AGP doesn't reject the un-dimensioned flavor).
        //  - configureEach fires AFTER the FlavorConfig is realized — i.e. after the
        //    consumer's configure-action completes. We use this to UPDATE the AGP
        //    flavor with the consumer-specified properties.
        ext.flavors.whenObjectAdded(object : Action<FlavorConfig> {
            override fun execute(flv: FlavorConfig) {
                createAgpFlavorEarly(androidExt, flv, ext, logger)
            }
        })
        ext.flavors.configureEach(object : Action<FlavorConfig> {
            override fun execute(flv: FlavorConfig) {
                updateAgpFlavorProperties(androidExt, flv, logger)
            }
        })
        ext.buildTypes.whenObjectAdded(object : Action<BuildTypeConfig> {
            override fun execute(bt: BuildTypeConfig) {
                createAgpBuildTypeEarly(androidExt, bt, logger)
            }
        })
        ext.buildTypes.configureEach(object : Action<BuildTypeConfig> {
            override fun execute(bt: BuildTypeConfig) {
                updateAgpBuildTypeProperties(androidExt, bt, logger)
            }
        })

        logger.lifecycle(
            "[KMP Flavors] ${project.path}: AGP propagation wired via whenObjectAdded",
        )
        return 1
    }

    /** Phase 1: create the AGP slot synchronously when consumer registers the flavor. */
    private fun createAgpFlavorEarly(androidExt: Any, flv: FlavorConfig, ext: KmpFlavorExtension, logger: Logger) {
        runCatching {
            val productFlavors = androidExt.javaClass.methods
                .firstOrNull { it.name == "getProductFlavors" }
                ?.invoke(androidExt) ?: return@runCatching
            val findByName = productFlavors.javaClass.methods.firstOrNull {
                it.name == "findByName" && it.parameterCount == 1
            } ?: return@runCatching
            if (findByName.invoke(productFlavors, flv.name) != null) return@runCatching

            // Ensure at least the first flavorDimension is registered with AGP, so
            // when we set the dimension on the AGP flavor below, AGP accepts it.
            val firstDim = ext.flavorDimensions.firstOrNull()?.name
            if (firstDim != null) {
                val getFlavorDims = androidExt.javaClass.methods.firstOrNull {
                    it.name == "getFlavorDimensions"
                }

                @Suppress("UNCHECKED_CAST")
                val existingDims = getFlavorDims?.invoke(androidExt) as? MutableList<String>
                if (existingDims != null && firstDim !in existingDims) {
                    existingDims.add(firstDim)
                }
            }

            val create = productFlavors.javaClass.methods.firstOrNull {
                it.name == "create" && it.parameterCount == 1
            } ?: return@runCatching
            val agpFlavor = create.invoke(productFlavors, flv.name)

            // Default the AGP flavor's dimension to the first flavorDimension. The real
            // value (if different) will be applied in updateAgpFlavorProperties after
            // the consumer's configure-action runs.
            if (firstDim != null) {
                AgpReflectiveSetters.set(agpFlavor, "dimension", firstDim)
            }
            logger.info(
                "[KMP Flavors] propagated flavor '${flv.name}' to AGP (dim=${firstDim ?: "<none>"})",
            )
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to create AGP flavor '${flv.name}': ${e.message}")
        }
    }

    /** Phase 2: update the AGP slot's properties after the consumer's configure-action runs. */
    private fun updateAgpFlavorProperties(androidExt: Any, flv: FlavorConfig, logger: Logger) {
        runCatching {
            val productFlavors = androidExt.javaClass.methods
                .firstOrNull { it.name == "getProductFlavors" }
                ?.invoke(androidExt) ?: return@runCatching
            val findByName = productFlavors.javaClass.methods.firstOrNull {
                it.name == "findByName" && it.parameterCount == 1
            } ?: return@runCatching
            val agpFlavor = findByName.invoke(productFlavors, flv.name) ?: return@runCatching

            // Now set the consumer-specified dimension (if different from default)
            flv.dimension.orNull?.let { dim ->
                val getFlavorDims = androidExt.javaClass.methods.firstOrNull {
                    it.name == "getFlavorDimensions"
                }

                @Suppress("UNCHECKED_CAST")
                val existingDims = getFlavorDims?.invoke(androidExt) as? MutableList<String>
                if (existingDims != null && dim !in existingDims) existingDims.add(dim)
                AgpReflectiveSetters.set(agpFlavor, "dimension", dim)
            }
            flv.applicationIdSuffix.orNull?.let { suffix ->
                AgpReflectiveSetters.set(agpFlavor, "applicationIdSuffix", suffix)
            }
            flv.versionNameSuffix.orNull?.let { suffix ->
                AgpReflectiveSetters.set(agpFlavor, "versionNameSuffix", suffix)
            }
            // v2.8 — per-flavor versionCode + versionName reflective propagation.
            flv.versionCode.orNull?.let { vc ->
                AgpReflectiveSetters.set(agpFlavor, "versionCode", vc)
            }
            flv.versionName.orNull?.let { vn ->
                AgpReflectiveSetters.set(agpFlavor, "versionName", vn)
            }
            logger.info("[KMP Flavors] finalized AGP flavor '${flv.name}'")
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to update AGP flavor '${flv.name}': ${e.message}")
        }
    }

    private fun createAgpBuildTypeEarly(androidExt: Any, bt: BuildTypeConfig, logger: Logger) {
        runCatching {
            val buildTypesCol = androidExt.javaClass.methods
                .firstOrNull { it.name == "getBuildTypes" }
                ?.invoke(androidExt) ?: return@runCatching
            val findByName = buildTypesCol.javaClass.methods.firstOrNull {
                it.name == "findByName" && it.parameterCount == 1
            } ?: return@runCatching
            if (findByName.invoke(buildTypesCol, bt.name) != null) return@runCatching
            val maybeCreate = buildTypesCol.javaClass.methods.firstOrNull {
                it.name == "maybeCreate" && it.parameterCount == 1
            } ?: return@runCatching
            maybeCreate.invoke(buildTypesCol, bt.name)
            logger.info("[KMP Flavors] propagated buildType '${bt.name}' to AGP")
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to create AGP buildType '${bt.name}': ${e.message}")
        }
    }

    private fun updateAgpBuildTypeProperties(androidExt: Any, bt: BuildTypeConfig, logger: Logger) {
        runCatching {
            val buildTypesCol = androidExt.javaClass.methods
                .firstOrNull { it.name == "getBuildTypes" }
                ?.invoke(androidExt) ?: return@runCatching
            val findByName = buildTypesCol.javaClass.methods.firstOrNull {
                it.name == "findByName" && it.parameterCount == 1
            } ?: return@runCatching
            val agpBt = findByName.invoke(buildTypesCol, bt.name) ?: return@runCatching
            bt.isDebuggable.orNull?.let { v ->
                AgpReflectiveSetters.set(agpBt, "debuggable", v)
            }
            bt.applicationIdSuffix.orNull?.let { suffix ->
                AgpReflectiveSetters.set(agpBt, "applicationIdSuffix", suffix)
            }
            bt.isMinifyEnabled.orNull?.let { v ->
                AgpReflectiveSetters.set(agpBt, "minifyEnabled", v)
            }
            logger.info("[KMP Flavors] finalized AGP buildType '${bt.name}'")
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to update AGP buildType '${bt.name}': ${e.message}")
        }
    }
}
