/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.6 Phase 4 — documentation surface for the conditional-targets sample.
 * Demonstrates `variantFilter { excludeTargets(...) }` discipline; the active
 * variant resolves to `freeDev` by default. Switch via `-PkmpFlavor=paidProd`
 * (or any of the 4 variants).
 *
 * **Note:** like multi-dim-3d's Sample.kt, this file is a documentation
 * surface — it's compiled via the active variant's `main` source set only.
 * Move into `src/desktopMain/kotlin/` to compile end-to-end.
 */

@file:Suppress("unused")

package com.example.condtargets

/**
 * Documents the variantFilter { excludeTargets(...) } DSL surface.
 * BuildKonfig fields generated per variant:
 *   - VARIANT_NAME: String  (e.g. "freeDevMain")
 *   - IS_PREMIUM: Boolean   (true for paid tier, false for free)
 *   - API_BASE_URL: String  ("https://api.dev.example.com" / "https://api.example.com")
 *
 * Generated class is wired into each target's main source set (e.g. desktopMain).
 * Move this file into src/desktopMain/kotlin/ to resolve BuildKonfig at compile time.
 */
fun describeActive(): String = buildString {
    appendLine("Conditional Targets Sample")
    appendLine("==========================")
    appendLine("variantFilter { excludeTargets(...) } excludes watchOS/tvOS for 'free' tier:")
    appendLine("  free × dev  → desktop, iosArm64, iosSimulatorArm64  (3 compilations)")
    appendLine("  free × prod → desktop, iosArm64, iosSimulatorArm64  (3 compilations)")
    appendLine("  paid × dev  → desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*  (7)")
    appendLine("  paid × prod → desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*  (7)")
    appendLine("Total: 20 compilations vs. 28 without excludeTargets (~28% CI saving)")
}
