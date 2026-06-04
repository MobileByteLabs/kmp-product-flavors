/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal.pbxproj

import java.security.MessageDigest

/**
 * Phase 12 — deterministic 24-char hex IDs from (projectName, relativePath).
 * Same seed = same ID, so re-running bootstrap doesn't churn IDs.
 */
internal object PbxObjectIdGenerator {
    fun forFileRef(projectName: String, relativePath: String): String {
        val seed = "$projectName:$relativePath"
        val sha = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return sha.take(12).joinToString("") { "%02X".format(it) }
    }
}
