/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal.pbxproj

/**
 * Phase 12 — typed model of a single pbxproj object.
 * Vendored minimal model. Handles only XCBuildConfiguration, PBXFileReference, PBXGroup.
 * Unknown types are preserved as Raw and round-tripped without parsing.
 */
internal sealed class PbxObject {
    abstract val id: String
    abstract val raw: MutableMap<String, Any>
    abstract val annotation: String? // /* Comment */ next to ID

    data class XCBuildConfiguration(
        override val id: String,
        override val raw: MutableMap<String, Any>,
        override val annotation: String?,
        val name: String,
        var baseConfigurationReference: String?,
    ) : PbxObject()

    data class PBXFileReference(
        override val id: String,
        override val raw: MutableMap<String, Any>,
        override val annotation: String?,
        val path: String?,
        val sourceTree: String?,
    ) : PbxObject()

    data class PBXGroup(
        override val id: String,
        override val raw: MutableMap<String, Any>,
        override val annotation: String?,
        var children: MutableList<String>,
        val name: String?,
        val path: String?,
    ) : PbxObject()

    data class Raw(
        override val id: String,
        override val raw: MutableMap<String, Any>,
        override val annotation: String?,
        val isa: String,
    ) : PbxObject()
}
