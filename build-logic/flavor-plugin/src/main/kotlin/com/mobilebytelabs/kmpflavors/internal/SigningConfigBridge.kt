/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.SigningConfig
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * v2.8 — Reflective bridge between `kmpFlavors.signingConfigs {}` and `android.signingConfigs {}`.
 *
 * Two passes per apply:
 *   1. For each `SigningConfig` declared in [KmpFlavorExtension.signingConfigs], reflectively
 *      `android.signingConfigs.create(name)` and assign `storeFile`/`storePassword`/`keyAlias`/`keyPassword`
 *      via [AgpReflectiveSetters.set] (two-pattern: classic setX(value) or AGP-9 Property<T>.set(value)).
 *   2. For each `FlavorConfig.signingConfig.orNull` reference, resolve the AGP signing config by name
 *      and assign it to the corresponding AGP `productFlavor` via reflection.
 *
 * Silent no-op when the consumer module doesn't apply an AGP plugin (no `android` extension).
 * Invoked from [com.mobilebytelabs.kmpflavors.KmpFlavorPlugin]'s
 * `pluginManager.withPlugin("com.android.{application,library}")` blocks alongside
 * [AgpProductFlavorRegistrar.apply], so signing configs land BEFORE AGP's `finalizeDsl` queue closes.
 */
internal object SigningConfigBridge {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val androidExt = project.extensions.findByName("android") ?: run {
            logger.info("[KMP Flavors] ${project.path}: signing bridge skipped (no android extension)")
            return 0
        }

        // Phase 1 — emit signing config declarations into AGP's signingConfigs container.
        // whenObjectAdded fires SYNCHRONOUSLY when the consumer's
        // `signingConfigs { create("release") { ... } }` runs, BEFORE the configure-action,
        // so storeFile/passwords may still be unset at this point. We use configureEach
        // for the subsequent property-realization pass.
        ext.signingConfigs.whenObjectAdded(object : Action<SigningConfig> {
            override fun execute(sc: SigningConfig) {
                createAgpSigningConfigEarly(androidExt, sc, logger)
            }
        })
        ext.signingConfigs.configureEach(object : Action<SigningConfig> {
            override fun execute(sc: SigningConfig) {
                updateAgpSigningConfigProperties(androidExt, sc, logger)
            }
        })

        // Phase 2 — for each flavor's signingConfig reference, assign the AGP signing config.
        // configureEach fires AFTER the consumer's `register { signingConfig.set(...) }` runs,
        // so flavor.signingConfig is realized at this point.
        ext.flavors.configureEach(object : Action<FlavorConfig> {
            override fun execute(flv: FlavorConfig) {
                val ref = flv.signingConfig.orNull ?: return
                runCatching {
                    assignSigningConfigToFlavor(androidExt, flv.name, ref, logger)
                }.onFailure { e ->
                    logger.warn(
                        "[KMP Flavors] failed to assign signingConfig '$ref' to flavor '${flv.name}': ${e.message}",
                    )
                }
            }
        })

        return 1
    }

    /** Phase 1: create the AGP signing config slot synchronously when consumer registers it. */
    private fun createAgpSigningConfigEarly(androidExt: Any, sc: SigningConfig, logger: Logger) {
        runCatching {
            val container = androidExt.javaClass.methods
                .firstOrNull { it.name == "getSigningConfigs" }
                ?.invoke(androidExt) ?: return@runCatching
            val findByName = container.javaClass.methods.firstOrNull {
                it.name == "findByName" && it.parameterCount == 1
            }
            if (findByName != null && findByName.invoke(container, sc.name) != null) return@runCatching
            val agpSlot = invokeMaybeCreate(container, sc.name)
                ?: invokeCreate(container, sc.name)
                ?: run {
                    logger.warn(
                        "[KMP Flavors] [KMPF-V51] signingConfig '${sc.name}' could not be " +
                            "created in android.signingConfigs (reflective lookup failed)",
                    )
                    return@runCatching
                }
            logger.info("[KMP Flavors] propagated signingConfig '${sc.name}' to AGP (slot=${agpSlot.javaClass.simpleName})")
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to create AGP signingConfig '${sc.name}': ${e.message}")
        }
    }

    /** Phase 2: update properties after the consumer's configure-action runs. */
    private fun updateAgpSigningConfigProperties(androidExt: Any, sc: SigningConfig, logger: Logger) {
        runCatching {
            val container = androidExt.javaClass.methods
                .firstOrNull { it.name == "getSigningConfigs" }
                ?.invoke(androidExt) ?: return@runCatching
            val agpSlot = invokeGetByName(container, sc.name)
                ?: invokeFindByName(container, sc.name)
                ?: return@runCatching
            AgpReflectiveSetters.set(agpSlot, "storeFile", sc.storeFile.orNull)
            AgpReflectiveSetters.set(agpSlot, "storePassword", sc.storePassword.orNull)
            AgpReflectiveSetters.set(agpSlot, "keyAlias", sc.keyAlias.orNull)
            AgpReflectiveSetters.set(agpSlot, "keyPassword", sc.keyPassword.orNull)
            logger.info("[KMP Flavors] finalized AGP signingConfig '${sc.name}'")
        }.onFailure { e ->
            logger.warn("[KMP Flavors] failed to update AGP signingConfig '${sc.name}': ${e.message}")
        }
    }

    /** Resolve `android.productFlavors.getByName(flavorName).signingConfig = android.signingConfigs.getByName(ref)`. */
    private fun assignSigningConfigToFlavor(
        androidExt: Any,
        flavorName: String,
        signingConfigRef: String,
        logger: Logger,
    ) {
        val productFlavorsContainer = androidExt.javaClass.methods
            .firstOrNull { it.name == "getProductFlavors" }
            ?.invoke(androidExt) ?: return
        val agpFlavor = invokeGetByName(productFlavorsContainer, flavorName)
            ?: invokeFindByName(productFlavorsContainer, flavorName)
            ?: return
        val signingConfigsContainer = androidExt.javaClass.methods
            .firstOrNull { it.name == "getSigningConfigs" }
            ?.invoke(androidExt) ?: return
        val agpSigningConfig = invokeGetByName(signingConfigsContainer, signingConfigRef)
            ?: invokeFindByName(signingConfigsContainer, signingConfigRef)
            ?: run {
                logger.warn(
                    "[KMP Flavors] [KMPF-V51] flavor '$flavorName' references signingConfig '$signingConfigRef' " +
                        "which is not declared in kmpFlavors.signingConfigs {}",
                )
                return
            }
        AgpReflectiveSetters.set(agpFlavor, "signingConfig", agpSigningConfig)
        logger.info("[KMP Flavors] flavor '$flavorName' signed with '$signingConfigRef'")
    }

    private fun invokeCreate(container: Any, name: String): Any? = runCatching {
        container.javaClass.getMethod("create", String::class.java).invoke(container, name)
    }.getOrNull()

    private fun invokeMaybeCreate(container: Any, name: String): Any? = runCatching {
        container.javaClass.getMethod("maybeCreate", String::class.java).invoke(container, name)
    }.getOrNull()

    private fun invokeGetByName(container: Any, name: String): Any? = runCatching {
        container.javaClass.getMethod("getByName", String::class.java).invoke(container, name)
    }.getOrNull()

    private fun invokeFindByName(container: Any, name: String): Any? = runCatching {
        container.javaClass.getMethod("findByName", String::class.java).invoke(container, name)
    }.getOrNull()
}
