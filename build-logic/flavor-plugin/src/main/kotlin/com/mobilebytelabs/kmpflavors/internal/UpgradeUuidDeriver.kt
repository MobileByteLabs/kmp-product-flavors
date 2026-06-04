/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import java.security.MessageDigest

/**
 * Phase 14 — derives Windows MSI upgradeUuid deterministically from (packageName, projectName).
 * Used when consumer sets `desktop { windows.upgradeUuid.set("auto") }`.
 * v1 suffix in seed allows future re-keying without colliding with deployed installations.
 */
internal object UpgradeUuidDeriver {
    fun derive(packageName: String, projectName: String): String {
        val seed = "$packageName:$projectName:kmp-flavors-upgradeUuid-v1"
        val sha = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val hex = sha.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
