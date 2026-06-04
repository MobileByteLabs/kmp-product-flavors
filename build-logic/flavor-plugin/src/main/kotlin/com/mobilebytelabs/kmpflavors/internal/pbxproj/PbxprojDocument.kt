/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal.pbxproj

internal data class PbxprojDocument(
    val rootObject: String,
    val objects: MutableMap<String, PbxObject>,
    val rawHeader: String,
    val archiveVersion: String,
    val objectVersion: String,
)
