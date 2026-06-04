/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig

/**
 * Phase 6 — typed model fed into the 6 runtime API template renderers.
 * Note: the emitted source files are VARIANT-AGNOSTIC at codegen time — they read
 * platform-native sources at compile/runtime to get the per-variant values.
 */
internal data class RuntimeApiSpec(
    val packageName: String,
)

internal data class RuntimeVariantHint(
    val flavorName: String,
    val buildTypeName: String,
    val isDemo: Boolean,
    val isDebug: Boolean,
) {
    companion object {
        fun from(flavor: FlavorConfig, buildType: BuildTypeConfig): RuntimeVariantHint {
            val isDemoField =
                (flavor.buildConfigFields.orNull?.get("IS_DEMO")?.value == "true") || (flavor.name == "demo")
            return RuntimeVariantHint(
                flavorName = flavor.name,
                buildTypeName = buildType.name,
                isDemo = isDemoField,
                isDebug = (buildType.isDebuggable.orNull == true),
            )
        }
    }
}
